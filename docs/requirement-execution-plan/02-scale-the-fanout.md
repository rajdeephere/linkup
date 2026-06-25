# Phase 02 — Scale the fan-out ⭐ (the headline)

**Status:** ⬜ · **Roadmap:** Days 8–9

## Goal
Go **multi-pod**. Prove a message from a socket on **pod-1** reaches a socket on **pod-7** in real
time, and that **killing a pod loses zero messages**. This is the "it's actually distributed" moment —
the signature war-story.

## Scope
**In:** Redis Pub/Sub routing (channel per conversation/user); run 2+ WS pods behind an LB; Kafka
durable log (every message also published to a topic); graceful drain on shutdown; client backoff+jitter
reconnect; move messages to Cassandra/Scylla (the scale-persistence split). **Out:** media, push, E2E,
calls.

## Architecture delta
```
   + Redis Pub/Sub: on connect subscribe convo channels; on send publish; each pod delivers to its sockets
   + Kafka: durable message.created topic → async backbone (history/push/search/AI)
   + Cassandra: messages partition=convoId, cluster=seq DESC   (Postgres keeps metadata)
   N WS pods behind a WS-aware LB; delivery does NOT depend on stickiness
```
([ADR-0001 Redis fan-out](../adr/0001-redis-pubsub-fanout.md),
[ADR-0003 polyglot split](../adr/0003-polyglot-persistence.md))

## Done when
- [ ] User A on pod-1, user B on pod-2 → A's message reaches B in real time (cross-pod).
- [ ] Kill a pod holding live sockets → clients reconnect to another pod, resync via cursor, **zero loss**.
- [ ] Load-test fan-out latency p99 and a reconnect storm without melting the cluster.
- [ ] Messages served from Cassandra (partition by convo, cluster by `seq DESC`).

## Maps to
- ADRs: [0001](../adr/0001-redis-pubsub-fanout.md), [0003](../adr/0003-polyglot-persistence.md)
- Hard scenarios: cross-pod (#4), pod-kill (#5), reconnect storm (#8). **Two signature war-stories.**
