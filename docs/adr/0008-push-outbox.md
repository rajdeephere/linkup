# ADR-0008 — Push notifications via outbox + Kafka, deduped vs in-app

**Status:** ✅ Accepted (as-built, Day 11) · **Date:** 2026-06-19

> **As-built (Day 11):** A `linkup-push` Kafka consumer group (separate from Day 9's
> `linkup-events`) reads `message.created`, checks **Redis presence** per recipient, and for
> **offline** recipients writes a `push_outbox` row per device and dispatches via a `PushSender`.
> Dedup vs in-app = skip online recipients. Idempotency = unique `(message_id, device_id)`. Unread
> count rides the payload for badging. `LoggingPushSender` is the dev default; `FcmPushSender`
> (HTTP v1) is a `@ConditionalOnProperty` drop-in. `GET /v1/notifications` exposes the outbox.
> The presence-vs-push race is accepted (a device going offline right after the check may miss the
> live message but recovers via the Day-6 sync cursor). Proven by `e2e demo:push`. Next hardening:
> a standalone retry-dispatcher for the PENDING backlog.

## Context
"Device is offline/backgrounded → wake it" is a different reliability domain than in-app socket
delivery. A user with an online device and a backgrounded device must be notified **once**, not twice.

## Decision
Decouple push via an **outbox + Kafka consumer**. A consumer reads the message stream, checks device
**presence**, and emits **FCM/APNs** only for **offline** devices — **deduping** against in-app
delivery so an online device isn't double-notified. Badge counts derive from unread state.

## Consequences
- Push reliability is independent of socket delivery; offline devices reliably woken.
- **Presence-vs-push race conditions** (device goes offline between check and send) must be handled.
- Badge-count accuracy is fiddly (multi-device unread reconciliation).

## Alternatives
- **Push on every message regardless of presence:** double-notifies active users; noisy.
- **No push:** offline/backgrounded devices never learn of messages until they reopen.

## Revisit if
Push volume/cost grows → batch/coalesce notifications per device window.
