# ADR-0012 — Rolling deploy with graceful drain for the stateful WebSocket tier

**Status:** Proposed (Phase 2 / Phase 5) · **Date:** 2026-06-19

## Context
The WebSocket tier is **stateful at runtime**: each pod holds tens of thousands of live STOMP sockets.
Any pod replacement — a deploy, a scale-down, a node drain, a region failover — *terminates real
connections*. A naive rolling restart would hard-kill sockets across the whole user base and risk
losing in-flight messages. We need a rollout that loses **zero messages** and avoids a reconnect
stampede.

## Decision
Deploy the WS tier as a **Deployment** (interchangeable pods, no stable identity) and roll it with
**graceful drain**, leaning on the **reconnect + `seq`-cursor resync** path as a first-class mechanism:
1. On SIGTERM, a **preStop** hook flips the pod to **NOT-ready** so the LB stops sending new connections.
2. The pod keeps serving existing sockets for a long **`terminationGracePeriodSeconds`** (60–120s).
3. Clients reconnect to other pods with **backoff + jitter**; on reconnect they send last-known `seq`
   per conversation and the server **streams the gap** — durability lives in Postgres/Cassandra/Kafka,
   never in the socket.
4. A **PodDisruptionBudget** + small `maxUnavailable` cap how many WS pods drain at once.

## Consequences
- Deploys, scaling, node drains, and region failover all reduce to the **same** drain+resync mechanism
  — build it once, every operational event becomes survivable.
- Reconnect+resync is **not optional polish** — the deploy story depends on it (couples §6 of the spec
  to operations).
- Longer grace periods slow rollouts slightly; PDBs must be tuned so a node drain can still proceed.

## Alternatives
- **Blue-green:** spin up a full new WS fleet and flip the LB. Costs 2× socket capacity, and the flip
  *still* forces every client to reconnect — you pay the reconnect cost anyway, plus double the nodes.
- **Recreate / hard restart:** drops every socket simultaneously → reconnect storm + message-loss risk.
  Dev-only.
- **StatefulSet:** gives stable identity/volumes the WS tier doesn't need; clients can reconnect to any
  pod, so identity is the wrong tool.

## Revisit if
Connection counts get so large that mass reconnects overwhelm the cluster → add connection-migration /
session-handoff, or shard the gateway (Discord-style) so drains affect a bounded slice.
