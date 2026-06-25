# Application Architecture — how LinkUp works

How the system behaves as a **real-time delivery engine** — what each component does, how a
message moves from one device to all recipient devices, and where the **sync** and **async**
paths are. Read this so your manifests, probes, routing, and dashboards are built against how
the app *actually* works.

> **The product is the delivery engine, not the chat-bubble UI.**
> `connect → send → fan-out → deliver → acknowledge → sync`, over a transport that survives
> reconnection, scales across many servers, and never shows a message twice or out of order.

- **Domain + schema** → [`data-model.md`](./data-model.md)
- **Wire contract** (REST + STOMP frames) → [`wire-protocol.md`](./wire-protocol.md)
- **Why each decision** → [`adr/`](./adr/)
- **Build plan / runbooks** → [`requirement-execution-plan/`](./requirement-execution-plan/) · [`step-by-step-implementation/`](./step-by-step-implementation/)

---

## 1. The system in one paragraph

A user signs in (JWT), and each of their **devices** opens a persistent **WebSocket** to the
backend. Sending a message is the central flow: the server assigns a **monotonic `seq`** per
conversation (the ordering source of truth), **durably persists** the message, **acks** the
sender, then **fans the message out** to every online recipient *device* — across however many
WebSocket pods hold those sockets — via **Redis Pub/Sub**. A durable **Kafka** log backs the
async side (push to offline devices, search indexing, AI). Each device **dedups on
`clientMsgId`** and **orders on `seq`**, so delivery is *at-least-once* but **display is
exactly-once**. The hard part isn't the UI — it's making delivery **correct under concurrency,
ordered under load, durable under failure, and consistent across devices**.

---

## 2. Component catalog (target state)

| Component | Lang | Role | Datastore | Phase |
|---|---|---|---|---|
| **frontend** | Angular | Web client; proves the guarantees (ordering, dedup, receipts, reconnect) | — | 0 ✅ |
| **backend (API tier)** | Spring Boot | REST: auth, conversations, history, presign | Postgres | 0 ✅ |
| **backend (WS tier)** | Spring Boot | Stateful WebSocket/STOMP: send/deliver/receipts/typing | — (holds sockets) | 3 |
| **Postgres** | — | Metadata: users, devices, conversations, participants, read cursors (+ messages in Phase 0) | — | 0 ✅ |
| **Redis** | — | Pub/Sub fan-out routing · presence/typing (TTL) · `seq` counters · rate limits | — | 1–2 |
| **Kafka** | — | Durable event log → async consumers (push, search, AI) | — | 2 |
| **Cassandra/Scylla** | — | Message store at scale (partition by convo, cluster by `seq DESC`) | — | 2 (split) |
| **S3/MinIO + CDN** | — | Media blobs (direct-to-blob presigned upload) | — | 3 |
| **SFU (LiveKit/mediasoup) + TURN** | — | WebRTC media routing for calls (signaling over our socket) | — | 4 |

> Day-1 reality: the **API tier** (auth + `User`/`Device`) and the **Angular client** exist.
> The WS tier, Redis fan-out, Kafka, and the rest arrive on the [phased plan](../README.md#build-progress).
> The architecture is designed for the target state so later phases don't require a rewrite.

---

## 3. Topology — three views

### 3a. Layers (request top to bottom)
```
   EDGE   browser ──► Angular client ──► Load Balancer (WS-aware, L7)
            sticky by connection, NOT required for delivery (Redis routes cross-pod)
              │  WSS/STOMP  +  HTTPS/REST
   ───────────┼──────────────────────────────────────────────────────────────
   API tier (stateless)        WS tier (stateful, N pods)
     auth · conversations        holds sockets {deviceA, deviceC, …} per pod
     history · presign           on connect → subscribe Redis chans for my convos
                                 on send → assign seq + persist → publish to convo chan
   ───────────┬──────────────────────────────────────────────────────────────
              ▼  owns
   DATA   Postgres (metadata + Phase-0 messages) · Redis (pubsub/presence/seq)
          Kafka (durable log) · Cassandra (messages @ scale) · S3/MinIO (media)
```

### 3b. Synchronous send path (who does what, in order)
```
   device ──► WS pod ──► [1] assign seq (atomic, per conversation)
                          [2] persist message (durable) ───────────► Postgres/Cassandra
                          [3] ack sender (now sender is safe)
                          [4] publish to Redis convo channel ───────► Redis Pub/Sub
                          [5] emit to Kafka (async backbone) ────────► Kafka
   every pod holding a recipient socket ◄── picks up from Redis ──► writes frame to socket
   recipient device ──► dedup on clientMsgId ──► render in seq order ──► send delivered/read receipt
```

### 3c. Asynchronous fan-out (Kafka consumers, non-blocking)
```
   Kafka  message.created ──┬──► push consumer        offline devices → FCM/APNs (outbox, dedup)
                            ├──► search indexer        → OpenSearch (non-E2E convos)
                            └──► AI assist             smart replies / summarize / moderate

   producer ──► Kafka  thumbnail.requests / media ──► media worker  (thumbnails/transcode)
```

**Two traffic shapes on purpose:** **sync** (the blocking send → seq → persist → fan-out chain)
and **async** (event-driven push/search/AI off the durable log).

---

## 4. Communication patterns

**Real-time — WebSocket + STOMP.** Bidirectional, framed, ordered. STOMP gives sub/ack
semantics over one socket. Every device subscribes to `/user/queue/messages` (its inbound
stream). See [`wire-protocol.md`](./wire-protocol.md).

**Cross-pod fan-out — Redis Pub/Sub.** A sender's socket on pod-1 and a recipient's socket on
pod-7 are connected because every pod subscribes to Redis channels for the conversations/users
it holds; on send, the pod publishes to the conversation's channel, and whichever pod holds each
recipient picks it up and writes the frame. Pub/Sub is fire-and-forget (no replay) — durability
comes from Postgres/Cassandra + Kafka, never from Redis. (See [ADR-0001](./adr/0001-redis-pubsub-fanout.md).)

**Durable async — Kafka.** Every message is also written to a durable topic so consumers (push,
search, AI) process independently with their own consumer groups, at-least-once, idempotent.

---

## 5. The invariants you defend with your life

1. **Ordering** — within a conversation `seq` is strictly monotonic and gap-free; clients render
   in `seq` order, **never** wall-clock. `createdAt` is display-only (clocks skew across senders).
2. **Exactly-once display** — delivery is *at-least-once* (a message may be pushed twice); the
   client dedups on `(conversationId, clientMsgId)` so the user sees it once. ([ADR-0004](./adr/0004-at-least-once-plus-dedup.md))
3. **No lost messages** — a sent+acked message is durably persisted *before* the sender's ack;
   offline recipients get it on next sync.
4. **Multi-device convergence** — all of a user's devices reach the same state via a
   sequence-numbered sync cursor (`lastReadSeq` / last-known `seq` per convo).

> **`seq` is assigned server-side, atomically, per conversation — the single source of truth for
> ordering.** This is the most important design decision in the system. ([ADR-0002](./adr/0002-server-assigned-seq.md))

---

## 6. Edge request lifecycle (auth)

```
   PUBLIC    /v1/auth/**            → pass through (register, login)
   PROTECTED everything else:
     - require  Authorization: Bearer <jwt>
     - verify signature + expiry + issuer (stateless; any pod, no DB lookup)
     - valid   → SecurityContext carries AppUserPrincipal (userId)
     - invalid → 401/403 (never reaches the handler)
```
Day-1 enforcement is app-level JWT (HS256). The WS handshake will authenticate the same token →
principal (Phase 3). ([ADR-0010](./adr/0010-stateless-jwt-auth.md))

---

## 7. Cross-cutting contract (every service guarantees these — and why you care)

| Feature | Behavior | Ops use |
|---|---|---|
| **Liveness** | `GET /actuator/health/liveness` (no deps) | restart a wedged pod |
| **Readiness** | `GET /actuator/health/readiness` (DB/Redis/broker) | keep traffic off a pod that can't serve; gate rollouts |
| **Metrics** | `/actuator/prometheus` → RED + WS gauges (`ws_connections`, fan-out latency) | scrape; HPA; SLO dashboards |
| **Trace propagation** | forward `traceparent` end-to-end (send → seq → fan-out → deliver → ack) | one message = one trace |
| **Structured JSON logs** | stdout, one JSON/line, with `trace_id` | log ↔ trace correlation |
| **Graceful drain** | on SIGTERM, stop accepting, let clients reconnect elsewhere | clean rolling updates of a *stateful* tier |

> The metric `route`/`destination` label is always the **template** (`/conversations/{id}`), never
> the raw path — bounded cardinality so Prometheus doesn't explode.

*(Day-1 backend has the security/JSON-log foundations; the actuator/OTel wiring lands with the
observability phase — see the build plan.)*

---

## 8. Failure modes (designed against — the chaos catalog)

| Inject | What the system does | Mechanism |
|---|---|---|
| Double-tap send on slow net | message appears **once** | `clientMsgId` idempotency + client dedup ([ADR-0004](./adr/0004-at-least-once-plus-dedup.md)) |
| Out-of-order arrival | rendered in correct order | server `seq`; client sorts on `seq` ([ADR-0002](./adr/0002-server-assigned-seq.md)) |
| Recipient offline | delivered on reconnect; pushed if backgrounded | durable store + sync cursor + push outbox ([ADR-0008](./adr/0008-push-outbox.md)) |
| Sender pod-1, recipient pod-7 | delivered cross-pod in real time | Redis Pub/Sub ([ADR-0001](./adr/0001-redis-pubsub-fanout.md)) |
| WS pod crashes holding 50k sockets | clients reconnect elsewhere, resync, lose nothing | stateless reconnect + cursor; durability in DB/Kafka |
| Typing/presence churn (thousands/s) | doesn't touch durable DB | Redis-only, TTL'd, debounced |

> **Deliberate gaps (chaos/learning material, not bugs):** no transactional outbox yet (a crash
> between persist and Redis publish can drop a real-time delivery — recovered on next sync, but
> the live push is lost); no E2E in the core. These are where sagas/outbox/E2E phases add depth.

---

## 9. Startup order (what blocks what)

```
   1  Datastores   Postgres · Redis · (Kafka, Cassandra in later phases)
   2  API tier     auth + conversations + history (stateless, scales freely)
   3  WS tier      stateful socket pods (need Redis for fan-out + presence)
   4  Consumers    push · search · AI   (need Kafka)
   5  Edge         load balancer ──► Angular client
```
A pod's readiness returning 503 means one of *its* deps isn't up yet — not a crash; services
retry initial connect and stay un-ready (no crash-loop) until the dep is available.
