# LinkUp — Step-by-Step Implementation

> The granular **how**. Where [`../requirement-execution-plan/`](../requirement-execution-plan/) says
> *what to build and why*, these are the hands-on runbooks: exact steps, commands, key files, and
> verification. Two perspectives, two tracks:

| Track | Perspective | What it answers |
|-------|-------------|-----------------|
| [`implementation/`](./implementation/) | **Build** | How do I *write* this feature? (code, entities, endpoints, tests) |
| [`deployment/`](./deployment/) | **Run / Deploy** | How do I *run* and ship it? (infra, docker, ports, later k8s/CI/CD) |

## Numbering
Runbooks are numbered to track the build (`NN-dayN-topic.md`). A runbook is written **fully when its
feature ships**, and outlined before — so completed days are detailed, future days are stubs that get
filled in as we go (no speculative guesswork).

## Status

**Implementation track**
- [x] [01 — Day 1: auth + domain skeleton](./implementation/01-day1-auth-and-domain.md) ✅ full
- [ ] 02 — Day 2: conversations + participants (REST) — outlined
- [ ] 03 — Day 3: WebSocket/STOMP transport — outlined
- [ ] 04 — Day 4: send/receive with `seq` ordering — outlined
- [ ] 05+ — receipts, fan-out, media, push, flagship … — outlined per phase

**Deployment track**
- [x] [01 — Local docker dev (infra + backend + frontend)](./deployment/01-local-docker-dev.md) ✅ full
- [ ] 02 — Dockerize backend + frontend (images) — outlined
- [ ] 03 — Kubernetes (Helm) + CI/CD (GitHub Actions) — outlined
- [ ] 04 — Observability (OTel → Prometheus/Grafana/Tempo) — outlined

## See also
- App design: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../adr/`](../adr/), [`../DEPLOYMENT-ARCHITECTURE.md`](../DEPLOYMENT-ARCHITECTURE.md)
- The phased plan: [`../requirement-execution-plan/`](../requirement-execution-plan/)
