# Impl 08 — Day 8: Redis Pub/Sub fan-out, go multi-pod ⭐

**Outcome:** the stateful WebSocket tier scales horizontally — a message from a socket on one pod
reaches a socket on another via Redis Pub/Sub (ADR-0001). **Status:** ✅ shipped & verified
(alice pod-1 → bob pod-2). The signature war-story.

## Prerequisites
- Phase 1 done (Redis already in the stack; real-time paths use SimpMessagingTemplate).

## Build order (backend) — `com.linkup.realtime`
1. **`FanoutMessage`** — envelope `{recipients (usernames), destination, payload (JsonNode)}`.
2. **`RealtimeFanout.send(recipients, destination, payload)`** — publish the envelope to Redis channel
   `linkup:fanout` (via `StringRedisTemplate.convertAndSend`, Jackson-serialized).
3. **`FanoutSubscriber`** (`MessageListener`) — on each pod: parse the envelope and
   `convertAndSendToUser(recipient, destination, payload)` for each — a **no-op** for users not on this
   pod, so each recipient is delivered to exactly once by the pod that holds them (sender echo included).
4. **`RedisFanoutConfig`** — `RedisMessageListenerContainer` subscribing the subscriber to the channel.
5. **Refactor every fan-out call site** to `fanout.send(...)` instead of `convertAndSendToUser`:
   `MessageService` (all participants), `PresenceService` (peers), `TypingController` (others),
   `ConversationService.markRead` (others). Added `ParticipantRepository.findParticipantUsernames`.

## Multi-pod infra (`infra/`)
- `docker-compose.yml`: **backend1** (builds `linkup-backend:local`, direct port 8091) + **backend2**
  (reuses the image, port 8092), both → same Postgres + Redis; a **gateway** (nginx) round-robins
  REST + WS over both pods and publishes 8081; frontend → gateway. YAML anchor shares the backend env.
- `gateway/nginx.conf`: WebSocket-aware (`Upgrade`/`Connection` via `map`), `upstream` over both pods,
  long read/send timeouts. **No sticky sessions** — delivery is via Redis.

## Verify
- **Cross-pod (deterministic):** `e2e/npm run demo:crosspod` — alice→pod-1(:8091), bob→pod-2(:8092),
  alice sends → bob receives (`seq` set); server logs show the two sessions on different pods.
- **Through the gateway (regression):** `npm run demo:receipts` still passes (presence + blue ticks +
  typing) load-balanced over both pods.

## Why (one line each)
Stateful WS tier + plain LB → breaks (pod only holds its own sockets). Publish-to-Redis → every pod
delivers to *its* recipients → cross-pod, exactly-once (no-op elsewhere). One channel now →
correctness; channel-per-conversation later → scale. No sticky sessions → any pod serves any
connection (JWT stateless + Redis routing). Redis fire-and-forget → durability stays in Postgres;
missed live publish recovered by the Day-6 sync cursor.

## Known limitation (documented)
Orphan sessions in the presence Redis SET after a hard pod restart (users look "online"). Fix =
heartbeat-refreshed TTL per session — Phase-2 hardening. Message fan-out is unaffected.

## Decisions referenced
- [ADR-0001 Redis Pub/Sub fan-out](../../adr/0001-redis-pubsub-fanout.md) (now **Accepted**).
  Hard scenario #4. Interview deep-dive: doc 08. War-story #6.
