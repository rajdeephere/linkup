# LinkUp Infra — Local Dev

Local development infrastructure for LinkUp, via docker-compose.

> Part of the **LinkUp monorepo** — siblings: [`../backend`](../backend), [`../frontend`](../frontend).

---

## What's here

| Service | Image | Host port | Why now |
|---|---|---|---|
| **Postgres** | `postgres:17-alpine` | **5433** → 5432 | metadata + (Phase 0) messages; 5433 avoids a native Postgres on 5432 |
| **Redis** | `redis:7-alpine` | 6379 | used from Phase 1 (presence/typing) & Phase 2 (Pub/Sub fan-out); running now costs nothing |

Both have healthchecks and named volumes (data persists across restarts).

> **Deliberately *not* here yet:** Kafka, Cassandra, MinIO. They arrive in Phase 2/3 — the golden rule
> is *correct-on-one-server before scaling*, so the heavy infra waits until it's earned.

## Use

```bash
docker compose -f docker-compose.yml up -d     # start
docker ps                                       # both should be (healthy)
docker compose -f docker-compose.yml down       # stop (keeps volumes/data)
docker compose -f docker-compose.yml down -v    # stop + wipe data
```

Connection (matches `backend/src/main/resources/application.yml`):
`postgres://linkup:linkup@localhost:5433/linkup` · `redis://localhost:6379`.

Full local-dev runbook → [`../docs/step-by-step-implementation/deployment/01-local-docker-dev.md`](../docs/step-by-step-implementation/deployment/01-local-docker-dev.md).
