# Impl 04 — Day 4: Send/receive a message (the core loop)

**Outcome:** send a message → server assigns a monotonic `seq` → persist → fan out to
participants → it renders in the other client in `seq` order. The composer is live.
**Status:** ✅ shipped & verified (ordering + idempotency).

## Prerequisites
- Day 1–3 done; infra + backend running; a live WebSocket session.

## Build order (backend) — `com.linkup.message`
1. **Migration** `V3__messages.sql` — `messages` table (`seq`, `client_msg_id`, type, body,
   edited/deleted tombstones) with unique `(conversation_id, seq)` **and** `(conversation_id,
   client_msg_id)`; index `(conversation_id, seq)`. Add `last_seq bigint` to `conversations`.
2. **Entity/enum** `Message`, `MessageType` (TEXT…); add `lastSeq` to `Conversation`.
3. **Repo** `MessageRepository.findByConversationIdAndClientMsgId` (idempotency lookup);
   `ConversationRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` — the seq serialization point).
4. **DTOs** `SendMessageRequest` (clientMsgId, type, body), `MessageResponse` (carries `seq`).
5. **Service** `MessageService.send`: membership check → idempotency check (return existing, no
   re-broadcast) → **lock conversation, `seq = last_seq + 1`** → persist → fan out to each
   participant via `convertAndSendToUser(username, "/queue/messages", msg)`.
6. **STOMP controller** `MessageStompController` — `@MessageMapping("/conversations/{id}/send")`;
   sender id from the **session principal** (not the payload); `@Valid @Payload`.

## Build order (frontend)
1. `core/messages/message.models.ts` — `Message`, `SendMessageRequest`.
2. `SocketService` — `messages$` now typed `Message`; add `send(conversationId, body)` →
   publishes to `/app/conversations/{id}/send` with a generated `clientMsgId`.
3. `features/home` — bucket inbound messages by conversation (`messagesByConvo`), **dedup on
   clientMsgId, sort by seq**; render the selected conversation's thread as bubbles (mine right,
   others left; sender name in groups); live composer (Enter / Send); auto-scroll on new message.

## Verify (headless)
```
alice sends "hello","how are you?","day 4 works!" + a DUP (same clientMsgId twice)
→ bob receives seq=1,2,3,4 in order; alice receives her own echoes;
  seqs strictly increasing; the duplicate is delivered exactly once.
```
In the browser: alice + bob in two windows, same conversation → type & Send → appears in both instantly.

## Why (one line each)
Server `seq` (row-locked) → the ordering truth, gap-free per conversation (ADR-0002). Client sorts by
seq, never wall-clock. clientMsgId + dedup → exactly-once display over at-least-once delivery (ADR-0004).
Sender id from the session, not the body → unspoofable. Fan-out via `/user/queue/messages` → each
recipient's own queue. **Known gaps:** broadcast happens in-transaction (no outbox yet) and is
single-node (multi-pod needs Redis fan-out, ADR-0001) — both deliberate, both documented.

## Decisions referenced
- [ADR-0002 server-assigned seq](../../adr/0002-server-assigned-seq.md) ·
  [ADR-0004 at-least-once + dedup](../../adr/0004-at-least-once-plus-dedup.md) ·
  [ADR-0001 Redis fan-out](../../adr/0001-redis-pubsub-fanout.md) (multi-pod path).
  Schema: [data-model.md](../../data-model.md). Wire: [wire-protocol.md](../../wire-protocol.md) §2.
  Interview deep-dive: doc 05.
