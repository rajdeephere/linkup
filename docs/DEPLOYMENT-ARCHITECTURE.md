# Deployment Architecture & Strategy

How LinkUp is built into images, deployed, scaled, rolled out, observed, and recovered — and **why**.
This is the design-level strategy (knowable now); the hands-on runbooks live in
[`./step-by-step-implementation/deployment/`](./step-by-step-implementation/deployment/) and get
written as each piece ships.

> **The defining deployment challenge:** LinkUp has **two tiers with opposite deploy semantics**.
> The **API tier is stateless** (trivial rolling deploys). The **WebSocket tier is stateful** — each
> pod holds tens of thousands of live sockets, so terminating a pod *drops real connections*. Getting
> that rollout right **without losing a message** is the whole game. (See [ADR-0012](./adr/0012-stateful-ws-tier-rolling-drain.md).)

---

## 1. Two tiers, two deploy semantics

```
   STATELESS API tier            STATEFUL WebSocket tier
   ──────────────────            ───────────────────────
   REST: auth, convos,           holds N live STOMP sockets per pod
   history, presign              delivery routes via Redis (not stickiness)
   any pod serves any request    a pod dying = those clients must reconnect
   → rolling update, no drama    → graceful drain + client reconnect+resync
   → HPA on CPU / RPS            → HPA on CONNECTION COUNT; PDB; long grace period
```
Datastores (Postgres, Redis, Kafka, Cassandra) are **StatefulSets** with PVCs — deployed once, not
part of app rollouts.

---

## 2. Containerization

One **multi-stage image per service** (small runtime, no build tools shipped):

| Service | Build stage | Runtime stage |
|---|---|---|
| backend | `maven` → jar | `eclipse-temurin:21-jre` (distroless-ish, non-root) |
| frontend | `node` → `ng build` static bundle | `nginx:alpine` serving `dist/` |

Images are tagged `:<service>-<git-sha>` (immutable, traceable to a commit), pushed to a registry
(ECR/GHCR). `latest` is never deployed to prod.

---

## 3. Orchestration topology (Kubernetes)

```
   per app service:   Deployment (image · envFrom ConfigMap+Secret · liveness · readiness ·
                                  requests/limits · terminationGracePeriod · preStop drain)
                      + Service (ClusterIP) + HPA + PodDisruptionBudget
   per datastore:     StatefulSet + PVC (Postgres/Redis/Kafka/Cassandra)
   edge:              Ingress / Istio Gateway (WS-aware, L7) → Services
   config:            ConfigMap (ports, *_URL, tunables)   Secret (DB password, JWT secret)
```

Why the API tier is a **Deployment** (not StatefulSet): pods are interchangeable — no stable identity
needed. Why the WS tier is **also a Deployment** (not StatefulSet): a socket pod has *runtime* state
(the sockets) but no *persistent identity* — clients can reconnect to **any** pod, so what it needs is
graceful drain, not a stable name/volume.

---

## 4. The stateful WS-tier rollout (the centerpiece)

Naively rolling the WS tier would hard-kill sockets and flap the whole user base. Instead, on each pod
replacement:

```
   1. Pod gets SIGTERM (rollout / scale-down / node drain)
   2. preStop: mark NOT-ready → LB stops sending NEW connections to this pod
   3. Pod keeps serving existing sockets during terminationGracePeriodSeconds (e.g. 60–120s)
   4. Optionally send clients a "reconnect soon" hint; clients reconnect to another pod
      with backoff + jitter (no thundering herd)
   5. On reconnect, client sends last-known seq per convo → server streams the gap → ZERO loss
   6. Grace expires → pod exits; durability was always in Postgres/Cassandra/Kafka, never the socket
```

Guardrails: **PodDisruptionBudget** (cap how many WS pods drain at once), **maxUnavailable: small /
maxSurge** on the rollout, and **connection-count-based HPA** so scaling reshuffles sockets gradually.
This is why **reconnect + sync is a first-class product path**, not an afterthought — the deploy story
*depends* on it. ([ADR-0012](./adr/0012-stateful-ws-tier-rolling-drain.md), [ADR-0002 seq sync](./adr/0002-server-assigned-seq.md))

---

## 5. Rollout strategies (which, where, why)

| Strategy | Mechanism | Best for | Notes for LinkUp |
|---|---|---|---|
| **Rolling** (default) | k8s replaces pods gradually | both tiers | API: trivial. WS: with the §4 drain. |
| **Canary** | Istio routes X% to the new version by weight | risky changes | great for the **delivery path** — shift 5% of sends, watch error budget, ramp |
| **Blue-green** | full new stack, flip the LB | DB/format-breaking changes | costly (2× WS capacity); the flip still needs client reconnect |
| **Recreate** | kill all, start all | never in prod | drops every socket at once — only for local/dev |

LinkUp default: **rolling everywhere**, **canary (via Istio weight) for delivery-path changes**, where
you can watch p99 fan-out latency + error budget burn before ramping 5% → 50% → 100%.

---

## 6. CI/CD pipeline

```
   git push ─► GitHub Actions (per changed service):
      build → unit/integration test → build image → scan → push :svc-<sha> → update manifest
   ─► Argo CD (GitOps) watches the manifest repo → syncs to the cluster (declarative, auditable)
```
- **Per-service builds** (path filters) so one change doesn't rebuild the world.
- **Tests gate the image** — no green pipeline, no deploy.
- **GitOps** (Argo CD): the cluster state = what's in git; rollbacks are a git revert; drift is detected.
- Environments promoted by PR: `dev → staging → prod` (same image, different config).

---

## 7. Config & secrets

- **Non-secret config** → ConfigMap (`*_URL`, ports, tunables), injected via `envFrom`.
- **Secrets** (DB password, `LINKUP_JWT_SECRET`) → k8s Secret sourced from a real manager (AWS Secrets
  Manager / Vault via **External Secrets Operator**) — never in git, never in the image.
- Same image across environments; **only config differs** (12-factor).

---

## 8. Scaling

| Workload | Signal | Mechanism |
|---|---|---|
| API tier | CPU / requests-per-second | HPA |
| **WS tier** | **active connection count** (custom metric) | HPA on `ws_connections` |
| Kafka consumers (push, search, AI, media) | **consumer lag** | **KEDA** (scale-to-zero ↔ N) |
| Nodes | pending pods / bin-packing | Cluster Autoscaler / **Karpenter** (spot-friendly) |

WS-tier scaling is the subtle one: adding/removing pods **reshuffles sockets**, so scale **gradually**
and lean on reconnect+resync (§4) — the same machinery that makes deploys safe makes scaling safe.

---

## 9. Health, readiness & deploy gating

- **liveness** (`/actuator/health/liveness`, no deps) → restart a wedged pod.
- **readiness** (`/actuator/health/readiness`, checks DB/Redis/broker) → keep traffic off a pod that
  can't serve **and gate the rollout** (a new version that can't reach its deps never receives traffic).
- WS tier flips readiness **false** in `preStop` to drain (§4).

---

## 10. Observability of deployments

- **Trace one message across pods** (OTel: send → seq → fan-out → deliver → ack) — proves a deploy
  didn't break the delivery path.
- **Deploy markers** on Grafana dashboards (annotate p99 fan-out latency, error rate, connection count
  at each rollout) → instantly see if a release regressed.
- **SLO / error-budget gating**: a canary that burns error budget too fast is auto-halted/rolled back.

---

## 11. Multi-region (stretch — Phase 5)

```
   Global LB (Route 53 latency routing / Global Accelerator)
        ├── Region A: full stack (API + WS tier + datastores)
        └── Region B: full stack (API + WS tier + datastores)
   cross-region: message replication · presence federation · per-region Redis/Kafka (not stretched)
```
**Active-active**, users pinned to nearest region; **per-region** Istio/Redis/Kafka (a stretched mesh
across regions is an anti-pattern — latency + blast radius). Cross-region message replication keeps
history convergent; presence federates. Regional failover = global LB health-checks reroute; clients
reconnect+resync into the surviving region (§4 again).

---

## 12. Rollback & DR

- **Rollback** = redeploy the previous immutable image tag (GitOps: revert the manifest commit). The
  stateful nuance: a rollback still drains+reconnects the WS tier, so it's a §4 event, not instant.
- **Schema rollback** = a new Flyway migration (never edit-in-place); design migrations to be
  backward-compatible across one version (expand/contract) so a rollback doesn't break the old code.
- **DR**: datastore backups (Postgres PITR, Cassandra snapshots), tested restore runbooks, defined
  **RPO/RTO**; region loss handled by §11 failover.

---

## 13. Phased adoption (this lands gradually)

| Phase | Deployment capability added |
|---|---|
| 0 (now) | local docker-compose dev ([deployment/01](./step-by-step-implementation/deployment/01-local-docker-dev.md)) |
| 2 | multi-pod WS tier behind an LB; the §4 drain/reconnect drill |
| 5 | Dockerize → k8s (Helm) → GitHub Actions + Argo CD → OTel/Grafana → canary; (stretch) multi-region |

> **Golden rule (deployment edition):** the stateful WS tier's safety — on deploy, scale, node-drain,
> and region failover — all reduces to the **same** mechanism: graceful drain + client reconnect+resync
> on the `seq` cursor. Build that once, and every operational event becomes survivable.
