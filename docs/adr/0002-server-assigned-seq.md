# ADR-0002 — Server-assigned monotonic `seq` per conversation

**Status:** Accepted (Phase 0, implemented Day 4) · **Date:** 2026-06-19

## Context
Message order must be unambiguous and identical on every device. Senders' clocks lie and skew (and
will skew worse across regions), so wall-clock `createdAt` cannot be trusted for ordering.

## Decision
The server assigns a **strictly monotonic, gap-free `seq` per conversation**, atomically, at send
time. Clients **sort, render, and sync on `seq`** — never on `createdAt`. `seq` is the single source
of truth for ordering.

## Consequences
- Deterministic order everywhere; "message N of M" and cursor sync become trivial.
- Assigning `seq` is a **serialization point per conversation** — a counter that must increment
  atomically (Postgres row + `SELECT … FOR UPDATE` / dedicated `seq` table in Phase 0; Redis `INCR`
  for hot conversations later).
- Hot groups concentrate writes on one counter → needs an efficient atomic increment.

## Alternatives
- **Lamport / hybrid-logical clocks:** more distributed, no single counter, but harder to render a
  clean linear "N of M" and more complex client reconciliation.
- **Wall-clock `createdAt`:** rejected — clock skew reorders messages.

## Revisit if
A single conversation's write rate makes the per-conversation counter a bottleneck → shard the counter
or move to HLC for that path.
