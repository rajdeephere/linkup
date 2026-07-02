# Impl 07 — Day 7: Presence, typing & read receipts (Phase 1)

**Outcome:** online/last-seen presence, "X is typing…", the blue double-tick (read receipts),
and unread badges. **First use of Redis** (ephemeral state). **Status:** ✅ shipped & verified
(backend 4/4 headless; UI verified in two browsers).

## Prerequisites
- Phase 0 done (messages + `seq` + `lastReadSeq` on participants). Redis in compose.

## Redis wiring
- `pom`: `spring-boot-starter-data-redis`; `application.yml`: `spring.data.redis.host/port`
  (`SPRING_DATA_REDIS_HOST=redis` in compose); backend `depends_on: redis`.

## Build order (backend) — `com.linkup.presence` + conversation read path
1. **Presence** `PresenceService` (Redis) — `ws:sessions:{userId}` SET (online ⇔ non-empty),
   `presence:lastseen:{userId}` stamped on last disconnect; fan online/offline to peers.
   `WebSocketEventListener` calls onConnect/onDisconnect. `PresenceController`
   `GET /v1/users/{id}/presence`.
2. **Typing** `TypingController` `@MessageMapping /conversations/{id}/typing` — membership check;
   6s-TTL Redis key `typing:{convo}:{user}`; fan `TypingEvent` to other participants.
3. **Receipts/unread** — `ParticipantSummary` +`lastReadSeq`; `ConversationResponse` +`unreadCount`
   (`= lastSeq − my lastReadSeq`); `ConversationService.markRead` (monotonic advance + broadcast
   `ReadReceiptEvent`); `POST /v1/conversations/{id}/read`.
4. **Repo** `ParticipantRepository` +`findByConversation_IdAndUser_Id`,
   `findOtherParticipantUsernames`, `findPeerUsernames`.

## Build order (frontend)
1. `SocketService` +`presence$`/`typing$`/`receipts$` streams + `sendTyping()`.
2. `ConversationService` +`read()`, `presence()`; models +`unreadCount`/`lastReadSeq`.
3. `home` — refactor `selected` to a **computed** off `conversations` (so receipts flow into the
   thread); subscribe the 3 streams; unread badges + online dot in the sidebar; presence/typing in
   the header; **blue ✓✓** when `every other participant's lastReadSeq ≥ msg.seq`; optimistic
   read-marking on open + on new message; debounced/throttled typing send.

## Verify
- **Backend (headless):** presence online/offline events + GET; typing start/stop; read receipt +
  unread `24 → 0`. All 4 passed.
- **UI (two browsers, `e2e/npm run demo:receipts`):** alice sees bob "online" + dot; alice's ticks
  turn **blue ✓✓** when bob opens the chat; bob sees "Alice is typing…". ✓

## Why (one line each)
Redis for presence/typing → high-churn ephemeral state, TTL'd, never Postgres (#9). Session SET →
correct multi-device online. Peers-only presence fan-out → relevant + scrape-safe. Typing TTL →
no stuck "…is typing". `lastReadSeq` cursor → double-tick + O(1) unread without a receipt table.
Monotonic read advance + broadcast → real-time blue ticks. `selected` as computed → receipts
reactively update the tick.

## Decisions referenced
- Hard scenario #9 (presence churn), #6 (multi-device read). Ephemeral-vs-durable storage split.
  Interview deep-dive: doc 07.
