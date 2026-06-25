# Phase 01 — Real-time UX: receipts, presence, typing

**Status:** ⬜ · **Roadmap:** Days 6–7

## Goal
Make it **feel like a real messenger**: the double-tick (sent → delivered → read), presence
(online/last-seen), "X is typing…", unread counts, and silent reconnect/resync — all as RxJS streams.

## Scope
**In:** per-device `Receipt` (sent/delivered/read) aggregated for the double-tick; presence + typing
via **Redis with TTL**, debounced; unread counts via `lastReadSeq`; optimistic send reconciliation;
reconnect banner. **Out:** cross-pod fan-out (still one server — Redis is used only for ephemeral
state here, not routing yet); media; push.

## Architecture delta
```
   + Redis: online set, typing, last-seen  (TTL'd, high-churn, NEVER persisted to durable store)
   + Receipt table (per recipient device)  + Participant.lastReadSeq drives unread counts
```
Presence/typing are deliberately Redis-only and ephemeral (hard scenario #9).

## Done when
- [ ] Read on one tab → ticks turn blue on the other (per-device receipt aggregation).
- [ ] "online / last seen" reflects connect/disconnect; "X is typing…" appears and debounces.
- [ ] Unread badge per conversation is correct via `lastReadSeq`.
- [ ] Edit / delete / unsend propagates to all devices (tombstone + reconcile by `seq`).

## Maps to
- ADRs: [0008 push outbox](../adr/0008-push-outbox.md) (receipts groundwork),
  [0002 seq](../adr/0002-server-assigned-seq.md)
- Hard scenarios: multi-device read convergence (#6), presence churn (#9), edit/delete (#14)
