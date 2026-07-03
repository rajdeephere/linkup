# Architecture Decision Records

Each ADR captures **one load-bearing decision** with a trade-off you can argue both sides of. They keep
settled decisions from being silently re-litigated — and each one is a ready-made answer to *"walk me
through a hard technical decision."*

**Format:** Status · Context · Decision · Consequences · Alternatives · Revisit-if.
**Status:** `Accepted` = decided & (being) implemented · `Proposed` = locked direction for a later phase.

| ADR | Decision | Status | Phase |
|-----|----------|--------|-------|
| [0001](./0001-redis-pubsub-fanout.md) | Connection fan-out via Redis Pub/Sub | Accepted | 2 (Day 8) |
| [0002](./0002-server-assigned-seq.md) | Server-assigned monotonic `seq` per conversation | Accepted | 0 (Day 4) |
| [0003](./0003-polyglot-persistence.md) | Polyglot persistence: Postgres metadata + Cassandra messages | Accepted¹ | 0→2 |
| [0004](./0004-at-least-once-plus-dedup.md) | At-least-once delivery + client dedup = exactly-once display | Accepted | 0–1 |
| [0005](./0005-direct-to-blob-media.md) | Direct-to-blob media upload (presigned URLs) | Proposed | 3 |
| [0006](./0006-e2e-libsignal.md) | E2E encryption via libsignal (client-side, opt-in) | Proposed | 4 |
| [0007](./0007-sfu-for-calls.md) | Integrate an SFU for calls (don't build media routing) | Proposed | 4 |
| [0008](./0008-push-outbox.md) | Push via outbox + Kafka, deduped vs in-app | Proposed | 3 |
| [0009](./0009-angular-rxjs-streams.md) | Angular + RxJS; model the socket as streams | Accepted | 0 |
| [0010](./0010-stateless-jwt-auth.md) | Stateless JWT auth (HS256) | Accepted | 0 (Day 1) |
| [0011](./0011-flyway-schema.md) | Flyway-owned schema; `ddl-auto=validate` | Accepted | 0 (Day 1) |
| [0012](./0012-stateful-ws-tier-rolling-drain.md) | Rolling deploy + graceful drain for the stateful WS tier | Proposed | 2 / 5 |

¹ Postgres-only now; the Cassandra split is the deliberate Phase-2 scale chapter.

**Where they come from:** 0001–0009 are the architecture-level decisions; 0010–0011 are the concrete
Day-1 implementation decisions; 0012 is the key **deployment** decision (see
[DEPLOYMENT-ARCHITECTURE.md](../DEPLOYMENT-ARCHITECTURE.md)).
