# ADR-0001 — Connection fan-out via Redis Pub/Sub

**Status:** Accepted (implemented Day 8) · **Date:** 2026-06-19

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

## As built (Day 8)
A single Redis channel (`linkup:fanout`) carries envelopes `{recipients, destination, payload}`.
`RealtimeFanout.send(...)` publishes; every pod's `FanoutSubscriber` receives and calls
`convertAndSendToUser` — a **no-op for users it doesn't hold**, so each recipient is delivered to
exactly once by the one pod that has their socket (sender's echo included). Per-conversation channels
(subscribe-on-join) are the optimization for when one global channel gets hot — the documented next
step below. All real-time paths (messages, presence, typing, receipts) route through this one service.
An nginx gateway round-robins REST + WS over the pods; **no sticky sessions** — delivery is via Redis.

## Revisit if
The single channel's per-pod filtering cost grows → move to **channel-per-conversation** (each pod
subscribes only to the conversations it holds); beyond that, Kafka-based routing or gateway sharding.
