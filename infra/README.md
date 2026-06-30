# LinkUp Infra — local stack

The full LinkUp stack via docker-compose: datastores **+ backend + frontend**.

> Part of the **LinkUp monorepo** — siblings: [`../backend`](../backend), [`../frontend`](../frontend).

---

## Services

| Service | Image / build | Host port | Notes |
|---|---|---|---|
| **postgres** | `postgres:17-alpine` | **5433** → 5432 | metadata + messages; 5433 avoids a native Postgres on 5432 |
| **redis** | `redis:7-alpine` | 6379 | used from Phase 1 (presence) & Phase 2 (Pub/Sub fan-out) |
| **backend** | built from `../backend` | **8081** | Spring Boot; reaches DB in-network as `postgres:5432` |
| **frontend** | built from `../frontend` | **4200** → 80 | nginx serving the Angular bundle |

> **Networking:** inside the compose network services use service names (`backend → postgres:5432`).
> The **browser** uses published host ports — it calls the backend at `localhost:8081` and loads the
> app from `localhost:4200`, which is why backend CORS allows `http://localhost:4200`.
>
> Kafka / Cassandra / MinIO arrive in Phase 2/3 — not here yet (golden rule: correct-on-one-server first).

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
