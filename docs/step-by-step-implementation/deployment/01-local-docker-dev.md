# Deploy 01 — Local dev (infra + backend + frontend)

**Goal:** run the whole stack on your machine. **Status:** ✅ working.

## Ports (and why these, not the defaults)
| Service | Port | Note |
|---|---|---|
| Postgres (Docker) | host **5433** → container 5432 | avoids a **native Postgres** already on 5432 |
| Redis (Docker) | 6379 | not used by the app until Phase 1 |
| Backend | **8081** | avoids a **native Apache (httpd)** already on 8080 |
| Frontend | 4200 | Angular dev server |

> These offsets came from a real first-boot failure: `localhost:5432` routed to the host's native
> Postgres (no `linkup` user) → Flyway auth error. Lesson: "works in the container" ≠ "the host routes
> to the container." Diagnose with `netstat -ano | grep ":5432"` (two listeners = collision).

## Steps
```bash
# 1. Infra (from the app repo root: linkup/)
docker compose -f infra/docker-compose.yml up -d
docker ps        # expect linkup-postgres (5433) + linkup-redis (6379), both healthy

# 2. Backend  → http://localhost:8081
cd backend && mvn spring-boot:run

# 3. Frontend → http://localhost:4200
cd ../frontend && npm install && npm start
```
Open http://localhost:4200 → register → land on home showing your `/me` profile.

## Verify
```bash
# infra reachable inside the container
docker exec linkup-postgres psql -U linkup -d linkup -c "select 1;"
# backend up (protected route returns 403 without a token)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/v1/users/me
# full auth smoke test → see implementation/01-day1-auth-and-domain.md "Verify"
```

## Config / secrets
- DB: `linkup` / `linkup` @ `localhost:5433/linkup` (in `application.yml`).
- JWT: `LINKUP_JWT_SECRET` (base64 256-bit) + `LINKUP_JWT_TTL` (e.g. `PT1H`) — env vars with dev
  fallbacks. The committed default is a **dev placeholder**; override in any real environment.

## Tear down
```bash
docker compose -f infra/docker-compose.yml down       # keeps named volumes (data persists)
docker compose -f infra/docker-compose.yml down -v     # also wipes pgdata/redisdata
```

## Gotchas
- **Node version:** newest Angular wants Node ≥ 24.15; this project pins Angular **20** for Node 24.12.
- **Stop background servers** before restructuring folders or rebuilding (`mvn`/`ng serve` hold ports).
- Compose uses named volumes (no host bind paths), so the app works regardless of where the repo lives.

## Next (outlined)
- **Deploy 02:** multi-stage Dockerfiles for backend (JRE) + frontend (nginx static), pushed to a registry.
- **Deploy 03:** Helm charts (Deployment + Service + ConfigMap/Secret, liveness/readiness), GitHub Actions.
