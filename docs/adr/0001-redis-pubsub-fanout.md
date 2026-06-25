# ADR-0001 — Connection fan-out via Redis Pub/Sub

**Status:** Proposed (Phase 2) · **Date:** 2026-06-19

## Context
The WebSocket tier is stateful and horizontally scaled: user A's socket lives on pod-1, user B's on
pod-7. A message from A must reach B's socket on a *different* pod. We need cross-pod routing without
a single bottleneck, and we must still scale connections horizontally.

## Decision
Split into a **stateless API tier** + a **stateful WebSocket tier**. Pods route messages to each
other through **Redis Pub/Sub** — a channel per conversation/user. On connect a pod subscribes to the
channels for the conversations it holds; on send it publishes to the conversation's channel; whichever
pod holds each recipient picks it up and writes the frame to that socket.

## Consequences
- Any pod can accept any connection and still deliver to a recipient elsewhere → true horizontal scale.
- Redis becomes critical-path infra.
- Pub/Sub is **fire-and-forget (no replay)** → durability must come from Postgres/Cassandra + Kafka,
  never from Redis. A pod that's momentarily down misses the live publish (recovered on reconnect/sync).

## Alternatives
- **Sticky-session-only:** simpler but ties a user to one pod; breaks on pod loss, limits balancing.
- **Discord-style gateway sharding:** more control, much more complexity.
- **Single STOMP broker (RabbitMQ relay):** simplest, but caps scale at one broker.

## Revisit if
Fan-out volume outgrows Redis Pub/Sub → move to Kafka-based routing or a dedicated gateway-shard model.
