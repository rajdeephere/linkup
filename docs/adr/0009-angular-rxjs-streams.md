# ADR-0009 — Angular + RxJS; model the socket as observable streams

**Status:** Accepted (Phase 0) · **Date:** 2026-06-19

## Context
The client's inputs — incoming messages, presence, typing, receipts, connection state — are all
*streams over time*. We want a frontend whose model matches that, and a stack that reinforces the
enterprise Java + Angular story.

## Decision
**Angular + RxJS.** A central `SocketService` exposes observable streams (`messages$`, `presence$`,
`typing$`, `receipts$`, `connection$`); features subscribe. Incoming messages `scan` into a
per-conversation `seq`-ordered map with `clientMsgId` dedup; connection state is a stream with
**retry + exponential backoff + jitter** built from RxJS operators; sends are optimistic and reconcile
on the server's canonical message.

## Consequences
- Stream operations (merge, scan, debounce typing, retry-with-backoff) are expressed natively.
- RxJS learning curve; risk of over-engineering if every click becomes an observable (mitigated by
  using **signals for synchronous UI state**, RxJS for async streams).

## Alternatives
- **React:** larger ecosystem, but a weaker reactive-stream fit for *this* problem and a weaker tie to
  the Java/Angular enterprise narrative.

## Revisit if
The team's expertise or hiring strongly favors another framework — the stream architecture transfers.
