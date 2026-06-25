# ADR-0008 — Push notifications via outbox + Kafka, deduped vs in-app

**Status:** Proposed (Phase 3) · **Date:** 2026-06-19

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
