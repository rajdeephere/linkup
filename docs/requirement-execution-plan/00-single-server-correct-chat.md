# Phase 00 — Correct chat on one server

**Status:** 🟡 in progress (Day 1 ✅) · **Roadmap:** Days 1–6

## Goal
A **correct, ordered, idempotent, offline-tolerant** 1:1 + group chat running on a **single** server.
Ship nothing until a message **cannot be lost, duplicated, or misordered** on one node. This is the
bedrock the entire platform stands on.

## Scope
**In:** JWT auth + `User`/`Device`; conversations + participants; WebSocket/STOMP transport;
send/receive with server-assigned `seq`; idempotent send (`clientMsgId`) + client dedup; durable
persist + ack; history with cursor pagination; reconnect + incremental sync.
**Out (later phases):** cross-pod fan-out (Redis), Kafka, receipts/presence polish, media, push, E2E,
calls. Single server only — no scaling yet.

## Architecture delta
```
   Angular client ──WSS/STOMP + REST──► single Spring Boot server ──► Postgres
   server: assign seq (atomic per convo) · persist · ack · broadcast to participants' queues
```
All-Postgres ([ADR-0003](../adr/0003-polyglot-persistence.md) Phase-0 stance); schema
via Flyway ([ADR-0011](../adr/0011-flyway-schema.md)).

## Done when
- [x] **Day 1:** log in / register, get a JWT, `GET /me` works; `User`+`Device` persisted.
- [x] **Day 2:** create a 1:1 and a group; list my conversations.
- [x] **Day 3:** two tabs connect over STOMP; server logs both principals.
- [x] **Day 4:** type in tab A → appears in tab B in real time, in `seq` order.
- [x] **Day 5:** optimistic send on a throttled network → bubble appears instantly, reconciles on echo; double-tap → **once**.
- [ ] **Day 6:** scroll months of history; go "offline," send, reconnect → missed messages appear in order.

## Maps to
- ADRs: [0002 seq](../adr/0002-server-assigned-seq.md),
  [0004 at-least-once+dedup](../adr/0004-at-least-once-plus-dedup.md),
  [0009 Angular/RxJS](../adr/0009-angular-rxjs-streams.md),
  [0010 JWT](../adr/0010-stateless-jwt-auth.md),
  [0011 Flyway](../adr/0011-flyway-schema.md)
- Runbooks: [`../step-by-step-implementation/implementation/`](../step-by-step-implementation/implementation/)
- Hard scenarios proven: double-tap (#1), out-of-order (#2), offline (#3)
