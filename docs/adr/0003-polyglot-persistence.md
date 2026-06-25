# ADR-0003 — Polyglot persistence: Postgres metadata + Cassandra messages

**Status:** Accepted — **Postgres-only for Phase 0**, split in Phase 2 · **Date:** 2026-06-19

## Context
Messages are append-only, write-heavy, time-ordered, and almost always queried as "the last N in this
conversation." User/membership data is relational and needs transactions and joins. One store can't be
optimal for both.

## Decision
**Metadata in PostgreSQL** (users, devices, conversations, participants, read cursors). **Messages in
Cassandra/ScyllaDB** at scale — a textbook wide-column partition: `partition = conversationId`,
`clustering = seq DESC`. **Phase 0 keeps messages in Postgres too**; the split is the deliberate
"scale persistence" chapter (Phase 2 / Day 12), not day-1 complexity.

## Consequences
- Each store is used where it's strong; "last N by convo" is a single partition read.
- Two stores to operate; **no cross-store transaction** → handle consistency via outbox/idempotency.
- The Phase-0→2 migration is a real, demoable milestone (and a war-story), not a rewrite — the domain
  was modelled for it from the start.

## Alternatives
- **All-Postgres forever:** simplest ops; fine until message volume is large. Acceptable MVP, but the
  polyglot split *is* the scale story we want to tell.
- **All-Cassandra:** loses relational integrity/joins for membership and read cursors.

## Revisit if
Message volume stays modest in practice → staying all-Postgres longer is legitimate; the split is
driven by measured write pressure, not dogma.
