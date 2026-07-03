# LinkUp Infra — local stack

The full LinkUp stack via docker-compose: datastores **+ backend + frontend**.

> Part of the **LinkUp monorepo** — siblings: [`../backend`](../backend), [`../frontend`](../frontend).

---

## Services (multi-pod since Day 8)

| Service | Image / build | Host port | Notes |
|---|---|---|---|
| **postgres** | `postgres:17-alpine` | **5433** → 5432 | metadata + messages |
| **redis** | `redis:7-alpine` | 6379 | presence/typing (Phase 1) **+ Pub/Sub fan-out routing (Phase 2)** |
| **backend1** | built from `../backend` | **8091** → 8081 | pod 1 (builds the shared `linkup-backend:local` image) |
| **backend2** | reuses `linkup-backend:local` | **8092** → 8081 | pod 2 |
| **gateway** | `nginx:alpine` | **8081** | WebSocket-aware LB over both pods (round-robin, no affinity) |
| **frontend** | built from `../frontend` | **4200** → 80 | nginx serving the Angular bundle |

> **Cross-pod delivery:** a message from a socket on backend1 reaches a socket on backend2 via
> **Redis Pub/Sub** (ADR-0001), so the gateway needs **no sticky sessions**. The browser hits the
> **gateway** at `localhost:8081`; the pods are also exposed directly on `:8091`/`:8092` so a test can
> target a specific pod and prove cross-pod (`cd ../frontend/e2e && npm run demo:crosspod`).
>
> Kafka / Cassandra / MinIO arrive in Phase 2 (Day 9) / 3 — not here yet.

## Use

```bash
# full stack (build images + run)
docker compose -f docker-compose.yml up -d --build

# just the datastores (when running backend/frontend on the host for hot-reload)
docker compose -f docker-compose.yml up -d postgres redis

docker compose -f docker-compose.yml ps        # status
docker compose -f docker-compose.yml logs -f backend
docker compose -f docker-compose.yml down       # stop (keeps data volumes)
docker compose -f docker-compose.yml down -v    # stop + wipe data
```

After a code change, rebuild the affected image: `docker compose ... up -d --build backend` (or `frontend`).

Full local-dev runbook → [`../docs/step-by-step-implementation/deployment/`](../docs/step-by-step-implementation/deployment/).
