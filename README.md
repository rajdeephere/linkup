<div align="center">

# LinkUp

### *Always connected.*

A **production-grade real-time chat & messaging platform** — the delivery engine behind a
Slack/WhatsApp-style app, built as a senior-backend portfolio piece.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular&logoColor=white)
![Postgres](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

</div>

---

## The one idea

> **The product is the delivery engine, not the chat-bubble UI.**

Every messaging product sits on a system that gets a message from one device to everyone else's —
**instantly, in order, exactly once, and never lost** — even when devices are offline, networks flap,
and a user has five devices. The chat UI is a commodity; the **delivery guarantees underneath** are the
hard part, and the part this project is about:

- **Strict per-conversation ordering** — a server-assigned monotonic `seq` is the source of truth (never wall-clock).
- **Exactly-once display** — at-least-once delivery + idempotent client dedup on `clientMsgId`.
- **No lost messages** — durably persisted before the sender is acked; offline devices catch up on reconnect.
- **Horizontal scale** — stateful WebSocket pods with cross-pod fan-out via Redis; survives pod loss.

This is a **distributed-systems** project wearing a chat app's clothes.

## Status

| | |
|---|---|
| **Phase** | 0 — *correct chat on one server* (Day 4 of 15 ✅) |
| **Shipped** | JWT auth · conversations (direct/group, dedup, authz) · JWT-auth WebSocket/STOMP · **real-time messaging — server-assigned monotonic `seq`, idempotent send, exactly-once display, live chat thread** · light/dark redesigned client |
| **Next** | Day 5 — optimistic send (render pending, reconcile on echo) + richer dedup |

Full milestone table → [`docs/requirement-execution-plan/`](./docs/requirement-execution-plan/).

## Architecture at a glance

```
  Angular client ──WSS/STOMP + REST──►  ┌── API tier (stateless: auth, convos, history) ──► Postgres
   (RxJS streams)                       └── WS tier (stateful: send/deliver/receipts)
                                              │ assign seq · persist · ack · fan-out
                                              ▼
                                         Redis (Pub/Sub fan-out · presence) · Kafka (durable log)
```
Deep dive → [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md).

## Repository layout

| Path | What | Stack |
|------|------|-------|
| [`backend/`](./backend)   | The delivery engine | Java 21 · Spring Boot 3.4 · Maven |
| [`frontend/`](./frontend) | The client that proves the guarantees | Angular 20 · RxJS · signals |
| [`infra/`](./infra)       | Local dev infrastructure | docker-compose (Postgres + Redis) |
| [`docs/`](./docs)         | Design, ADRs, the phased plan, and build/deploy runbooks | Markdown |

## Quick start

> Ports avoid a native Postgres (5432) and Apache (8080) on the dev machine.
> **Postgres → 5433 · Redis → 6379 · Backend → 8081 · Frontend → 4200.**

**Option A — everything in Docker (one command):**
```bash
docker compose -f infra/docker-compose.yml up -d --build
# Postgres + Redis + backend (Spring Boot) + frontend (nginx) all come up.
```

**Option B — infra in Docker, apps on the host (hot-reload for dev):**
```bash
docker compose -f infra/docker-compose.yml up -d postgres redis   # just the datastores
cd backend  && mvn spring-boot:run                                 # API  → http://localhost:8081
cd frontend && npm install && npm start                            # web  → http://localhost:4200
```

Open **http://localhost:4200** → register → land on the home page showing your `/me` profile.
(Inside Docker the backend reaches the DB as `postgres:5432`; the browser hits the published `localhost:8081`.)

<details>
<summary>API smoke test (curl)</summary>

```bash
curl -X POST http://localhost:8081/v1/auth/register -H "Content-Type: application/json" \
  -d '{"username":"alice","displayName":"Alice","password":"password123","platform":"WEB"}'
# response carries accessToken → call /me with it:
curl http://localhost:8081/v1/users/me -H "Authorization: Bearer <accessToken>"
```
</details>

## Documentation

All engineering docs live in [`docs/`](./docs):

| Doc | What |
|-----|------|
| [ARCHITECTURE.md](./docs/ARCHITECTURE.md) | How the system works — components, send/sync paths, the invariants |
| [DEPLOYMENT-ARCHITECTURE.md](./docs/DEPLOYMENT-ARCHITECTURE.md) | Deploy strategy — two-tier rollout, the stateful WS-tier drain, CI/CD, scaling, DR |
| [data-model.md](./docs/data-model.md) · [wire-protocol.md](./docs/wire-protocol.md) | Domain/schema · REST + STOMP contract |
| [adr/](./docs/adr/) | 12 Architecture Decision Records (every load-bearing trade-off) |
| [requirement-execution-plan/](./docs/requirement-execution-plan/) | The phased build plan (what/why) |
| [step-by-step-implementation/](./docs/step-by-step-implementation/) | Build + deploy runbooks (the how) |

## License

MIT.
