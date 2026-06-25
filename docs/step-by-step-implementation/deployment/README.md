# Deployment Track — run & ship runbooks

How to **run** LinkUp locally and (later) ship it. Each runbook: **Goal · Steps · Verify · Gotchas.**

> **The strategy these runbooks execute** lives in the app repo:
> [`linkup/docs/DEPLOYMENT-ARCHITECTURE.md`](../../DEPLOYMENT-ARCHITECTURE.md) — the
> two-tier rollout, the **stateful WS-tier graceful drain** ([ADR-0012](../../adr/0012-stateful-ws-tier-rolling-drain.md)),
> CI/CD, scaling, multi-region, and DR. Read it first; these runbooks are the hands-on execution.

| # | Topic | Status |
|---|-------|--------|
| [01](./01-local-docker-dev.md) | Local dev: infra (Postgres+Redis) + backend + frontend | ✅ full |
| 02 | Dockerize backend + frontend (multi-stage images) | ⬜ outline |
| 03 | Kubernetes (Helm charts) + CI/CD (GitHub Actions → build/test/push/deploy) | ⬜ outline |
| 04 | Observability (OpenTelemetry → Prometheus / Grafana / Tempo / Loki) | ⬜ outline |
| 05 | Multi-region (stretch): 2nd region WS tier + global routing | ⬜ outline |

The deployment depth grows with the build — Phase 02 (scale) brings Redis/Kafka/Cassandra into the
compose/k8s story; Phase 05 brings the full k8s + CI/CD + observability chapter.
