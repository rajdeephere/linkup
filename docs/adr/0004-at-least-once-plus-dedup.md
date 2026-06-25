# ADR-0004 — At-least-once delivery + client dedup = exactly-once display

**Status:** Accepted (Phase 0–1) · **Date:** 2026-06-19

## Context
True exactly-once *delivery* over a flaky network is impractical (the two-generals problem). But users
must never see a message twice or lose one. This is the central delivery-semantics decision.

## Decision
Deliver **at-least-once** and make the **client idempotent** on `(conversationId, clientMsgId)`. Every
send carries a client-generated `clientMsgId`; the server is idempotent on it (a retry returns the same
canonical message) and **acks only after durable persist**. The client dedups on `clientMsgId`, so the
*display* is exactly-once even though the wire delivery is at-least-once.

## Consequences
- A message may be transmitted/pushed more than once; the client silently collapses duplicates.
- Requires an ack/retry protocol on the wire and a unique index on `(conversationId, clientMsgId)`.
- Senders get a safe ack (durably persisted) before the UI "confirms"; optimistic UI reconciles on ack.

## Alternatives
- **Exactly-once delivery:** impractical/fragile over real networks.
- **At-most-once:** simpler but loses messages — unacceptable.

## Revisit if
Never (this is foundational). Tuning happens in retry/backoff parameters, not the semantic.
