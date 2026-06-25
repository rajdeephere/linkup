<div align="center">

# LinkUp Backend — The Delivery Engine

**The real-time messaging engine behind LinkUp.** The product is the *delivery engine, not the
chat-bubble UI* — this is that engine.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven&logoColor=white)
![Postgres](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)

</div>

> Part of the **LinkUp monorepo** — siblings: [`../frontend`](../frontend), [`../infra`](../infra),
> design & decisions in [`../docs`](../docs).

---

## Stack

| Concern | Choice | Notes |
|---|---|---|
| Runtime | **Java 21 (LTS)** | virtual threads matter for the WebSocket tier later |
| Framework | **Spring Boot 3.4** | web · security · validation · data-jpa |
| Build | **Maven** | `spring-boot-starter-parent` BOM |
| Store | **PostgreSQL** | all-Postgres for Phase 0; messages split to Cassandra in Phase 2 |
| Schema | **Flyway** | versioned SQL; Hibernate `ddl-auto=validate` (never `update`) |
| Auth | **JWT (HS256)** + BCrypt | stateless; `jjwt` |

## Status — Day 1 (auth + domain skeleton) ✅

| Endpoint | Purpose |
|---|---|
| `POST /v1/auth/register` | create account + bind a device → JWT |
| `POST /v1/auth/login` | authenticate → JWT |
| `GET  /v1/users/me` | JWT-protected profile (proves the full auth loop) |

Plus: `User` + `Device` JPA entities (device is first-class — fan-out targets devices), stateless
Spring Security filter chain, BCrypt hashing, Flyway-managed schema, a uniform `ApiError` envelope, and
a **generic 401** on bad credentials (no username enumeration).

## Run

Needs infra up: `docker compose -f ../infra/docker-compose.yml up -d`.

```bash
mvn spring-boot:run        # → http://localhost:8081
mvn -DskipTests package    # build a jar
mvn test                   # tests
```

> **Port 8081** (avoids native Apache on 8080) · **Postgres host 5433** (avoids native Postgres on 5432).

## Configuration

`src/main/resources/application.yml`. Secrets come from env vars with dev fallbacks:

| Env var | Meaning | Dev default |
|---|---|---|
| `LINKUP_JWT_SECRET` | base64 256-bit HS256 signing key | dev-only placeholder |
| `LINKUP_JWT_TTL` | access-token TTL (ISO-8601, e.g. `PT1H`) | 1 hour |

## Layout

```
src/main/java/com/linkup/
├── LinkupApplication.java        entry point
├── config/                       SecurityConfig, JwtProperties
├── auth/                         JWT issue/verify, login/register, principal, filter  (+ dto/)
├── user/                         User + Device entities, repos, /me controller        (+ dto/)
└── common/                       ApiError, GlobalExceptionHandler, exceptions
src/main/resources/
├── application.yml
└── db/migration/                 Flyway — V1__init_users_and_devices.sql
```

## API quick reference

```bash
curl -X POST http://localhost:8081/v1/auth/register -H "Content-Type: application/json" \
  -d '{"username":"alice","displayName":"Alice","password":"password123","platform":"WEB"}'
curl http://localhost:8081/v1/users/me -H "Authorization: Bearer <accessToken>"
```

## Roadmap (this service)

| Day | Adds |
|---|---|
| 2 | `Conversation` + `Participant` + REST (create / list / add-members) |
| 3 | WebSocket/STOMP transport + auth handshake |
| 4 | Send/receive with server-assigned monotonic `seq` (ordering truth) |
| 5–6 | Idempotent send (`clientMsgId`) + dedup · history + reconnect/sync |
| 7+ | Receipts · presence · Redis Pub/Sub fan-out · Kafka · media · push … |

Why behind each decision → [`../docs/adr/`](../docs/adr/). Build runbooks →
[`../docs/step-by-step-implementation/`](../docs/step-by-step-implementation/).
