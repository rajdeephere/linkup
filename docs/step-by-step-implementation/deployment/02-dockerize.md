# Deploy 02 — Dockerize backend + frontend (full stack in compose)

**Goal:** the whole app runs with one `docker compose up --build` — Postgres, Redis, backend, frontend.
**Status:** ✅ working.

## What was added
- **`backend/Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` builds the jar →
  `eclipse-temurin:21-jre` runs it. Exposes 8081. (`.dockerignore` excludes `target/`.)
- **`frontend/Dockerfile`** — multi-stage: `node:22-alpine` runs `npm ci` + `npm run build` →
  `nginx:alpine` serves `dist/linkup-frontend/browser`. (`.dockerignore` excludes `node_modules/`,
  `dist/`, `.angular/`.)
- **`frontend/nginx.conf`** — SPA fallback (`try_files … /index.html`) so client routes resolve.
- **`infra/docker-compose.yml`** — added `backend` and `frontend` services alongside postgres/redis.

## The networking model (the bit that matters)
```
   browser ──http://localhost:4200──► frontend (nginx)         [published 4200->80]
   browser ──http://localhost:8081──► backend  (Spring Boot)   [published 8081->8081]
   backend ──jdbc:postgresql://postgres:5432/linkup──► postgres  [in-network service name]
```
- **In-network**, services use **service names** (`backend` → `postgres:5432`). The backend image
  overrides the DB host via `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/linkup` (Spring
  relaxed binding) — the `application.yml` default (`localhost:5433`) is only for host-run dev.
- **The browser** can't see the compose network, so it uses **published host ports**: the SPA
  (baked at build time with `apiBaseUrl=http://localhost:8081`) calls the backend at `localhost:8081`,
  which is why backend CORS allows `http://localhost:4200`.
- `depends_on: postgres { condition: service_healthy }` makes the backend wait for a ready DB.

## Run
```bash
docker compose -f infra/docker-compose.yml up -d --build   # full stack
docker compose -f infra/docker-compose.yml up -d postgres redis   # datastores only (host dev)
docker compose -f infra/docker-compose.yml up -d --build backend  # rebuild one service
```

## Verify
```
docker ps → 4 linkup-* containers
backend logs → "Database: jdbc:postgresql://postgres:5432/linkup" + "Started LinkupApplication"
curl localhost:8081/v1/users/me → 403 (up, protected) ; login alice → 200 ; /me → 200
curl localhost:4200 → 200, <title>LinkUp …</title>
```
All verified ✓.

## Gotchas
- Host `mvn`/`ng` and the containers both want 8081/4200 — run **one or the other**, not both.
- The frontend bundle is built **inside** the image, so `environment.ts` is baked at build time; a
  different deploy target (real domain) needs a build-time env swap or a runtime config file.
- JRE image has no curl, so there's no in-container backend healthcheck yet (we have no actuator dep);
  add Spring Boot Actuator + a `/actuator/health` healthcheck when we wire observability (Phase 5).

## Next (outlined)
- **Deploy 03:** Kubernetes (Helm) + GitHub Actions CI/CD; push images to a registry.
  See [`../../DEPLOYMENT-ARCHITECTURE.md`](../../DEPLOYMENT-ARCHITECTURE.md) for the strategy.
