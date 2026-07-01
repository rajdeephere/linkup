# Impl 06 — Day 6: History (cursor pagination) + reconnect sync

**Outcome:** messages persist and load on open (not live-only anymore); scroll-back pages older
messages; a reconnect catches up anything missed while offline. **Status:** ✅ shipped.
**This closes Phase 0** — a correct, ordered, idempotent, offline-tolerant single-server chat.

## Prerequisites
- Day 4–5 done (messages persisted with `seq`; optimistic client).

## Build order (backend) — `com.linkup.message`
1. **Repo queries** — `findByConversationIdOrderBySeqDesc`,
   `…AndSeqLessThanOrderBySeqDesc` (before), `…AndSeqGreaterThanOrderBySeqAsc` (after), all `Pageable`.
2. **`MessageHistoryResponse`** — `{ messages (ascending), hasMore }`.
3. **`MessageService.history(userId, convId, before, after, limit)`** — membership check; cap limit
   ≤100; pick query by cursor; reverse DESC→ASC for the `before`/latest cases; `hasMore = page full`.
4. **`MessageController`** — `GET /v1/conversations/{id}/messages?before|after=<seq>&limit`.

No migration — reads over the Day-4 `messages` table + its `(conversation_id, seq)` index.

## Build order (frontend)
1. `MessageService.history(convId, {before?, after?, limit?})` (REST).
2. `home`:
   - **on open** (`select`) — first time, fetch the latest page and merge; scroll to bottom.
   - **scroll-back** — on scroll near the top, `loadOlder()`: fetch `before=<oldest seq>`, prepend,
     **preserve scroll position** (`scrollTop = newHeight − oldHeight`); stop when `hasMore=false`.
   - **reconnect sync** — on `connection$ === 'connected'`, `after=<newest seq>` for the open convo,
     merge missed messages.
   - **merge** dedups on `clientMsgId` (history/live/sync compose safely); **stick-to-bottom** gating
     so incoming messages don't yank the user out of history.

## Verify
- **Backend (headless):** `?limit=3` → latest 3 ascending, `hasMore:true`; `?before=3` → older page,
  `hasMore:false` at the start; `?after=2` → all newer; non-member → **403**. All passed.
- **Browser:** reload → the thread shows history (was empty before). Scroll up → older messages load.
  Kill the socket, have the other user send, reconnect → missed messages appear in order.

## Why (one line each)
Keyset pagination on `seq` → O(log n) + stable under inserts, unlike OFFSET (ADR-0002 index pays off).
Ascending responses → client just prepends/append, no full re-sort. Reconnect `after=<seq>` cursor →
durable catch-up for the fire-and-forget socket (invariants #3/#4). Merge dedups on `clientMsgId` →
history + live + sync compose with no duplicates (ADR-0004). Scroll-position preserved + at-bottom
gating → history reading isn't interrupted.

## Decisions referenced
- [ADR-0002 seq as ordering/cursor](../../adr/0002-server-assigned-seq.md) ·
  [ADR-0004 dedup](../../adr/0004-at-least-once-plus-dedup.md). Wire: [wire-protocol.md](../../wire-protocol.md) §1.
  Interview deep-dive: doc 06.
