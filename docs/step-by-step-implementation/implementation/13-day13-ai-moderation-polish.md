# Impl 13 — Day 13: AI flagship polish — async moderation + caching + rate-limit ⭐

**Outcome:** the AI subsystem is now **on-demand + async**: a background consumer flags toxic/spam
messages (⚠️ overlay), summaries are **cached** until the thread moves on, and on-demand AI is
**rate-limited** per user. **Status:** ✅ shipped & verified (stub offline; real Groq drop-in).
Completes **Phase 4**. (Streaming summaries deferred — noted below.)

## Prerequisites
- Day 12 (AI assist + the `AiAssistant` seam), Day 9 (Kafka log), Day 7 (Redis).

## Build order (backend)
1. **Moderation seam** — `AiAssistant.moderate(text)` → `Moderation(flagged, category, reason)`.
   `StubAiAssistant`: deterministic keyword match (offline, no key). `GroqAiAssistant`: one-line
   `SAFE` / `FLAG|category|reason` classify, parsed leniently (never over-flags on parse failure).
2. **Async pipeline** — `V6__message_moderation` (one row per checked message; unique `message_id`
   for idempotency). `ModerationConsumer` `@KafkaListener(groupId = "linkup-ai")` — the **3rd**
   consumer group on `message.created` (after Day-9 `linkup-events`, Day-11 `linkup-push`).
   `ModerationService`: TEXT only, idempotent, failures swallowed (passive overlay never disturbs
   delivery). Read via `GET /v1/conversations/{id}/moderation` (membership-checked).
3. **Summary caching** — Redis `ai:summary:{convId}` stores `{seq, summary}`; a summary is served
   from cache while `cachedSeq >= conversation.lastSeq`, else regenerated + re-cached. `SummaryResponse`
   gains a `cached` flag. Authorization is checked **before** the cache read so a hit can't leak.
4. **Rate limiting** — Redis fixed-window `ai:rl:{userId}` (INCR + 60s TTL); over
   `linkup.ai.rate-limit-per-minute` → **429**. `application.yml`: `rate-limit-per-minute`,
   `summary-cache-ttl`.

## Build order (frontend)
- **`AiService.moderation(id)`** + `SummaryResponse.cached`.
- **`Home`** — fetch flags on conversation open → `messageId → reason` map; **⚠️ flagged** mark on
  those bubbles (title = reason). Cleared on conversation switch.

## Infra
- None new — Kafka (Day 9) + Redis (Day 7) already in the stack.

## Verify
- **Moderation:** `e2e/npm run demo:moderation` — an abusive message gets flagged by the `linkup-ai`
  consumer, a clean one doesn't (read back via `GET /moderation`). Green on the stub; real Groq
  classifies when enabled.
- **Caching + rate-limit:** `e2e/npm run demo:ai` — summarize twice → 2nd is `cached=true`; send a new
  message → next summarize is `cached=false` (cache busted by advancing seq).

## Why (one line each)
Moderation is the **async** half of the flagship → a **separate Kafka consumer group** off the same
log, decoupled from delivery (own offset, restart/replay independently) — the exact Day-9/11 pattern,
third instance. Idempotent on `message_id` → at-least-once redelivery never double-flags. **Cache
keyed by seq** → a summary is reused only while the thread hasn't moved, so it's both cheap and never
stale. **Authz before cache read** → a cache hit can't become a read side-channel. **Rate-limit in
Redis** → on-demand AI can't be abused into a cost/DoS vector. Moderation **failures are swallowed**
→ a passive overlay must never disturb messaging. (Streaming summaries via SSE — UX-only, not
headless-provable — are the documented next step.)

## Decisions referenced
- Phase 4 flagship = AI assist (Day 12 on-demand + Day 13 async). Hard scenario #13 (abusive/spam
  moderation). Interview deep-dive: doc 12 (+ moderation section). Third consumer group vs Day 9/11.
