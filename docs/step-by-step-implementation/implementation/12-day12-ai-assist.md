# Impl 12 — Day 12: AI assist — thread summarize + smart replies ⭐ (the flagship)

**Outcome:** open a busy conversation, hit **✨ Summarize** for a 2–3 line recap; tap **💡** for
three context-aware reply chips in the composer. **Status:** ✅ shipped & verified (StubAiAssistant;
real Groq is a config-flag drop-in). Opens **Phase 4** (one flagship subsystem — AI assist chosen).

## Prerequisites
- Day 6 (history read — the AI context comes from `MessageService.history`, which also does the
  membership check).

## Build order (backend)
1. **`ai.AiProperties`** — `linkup.ai.*` (enabled, base-url, api-key, model, max-history).
2. **`ai.AiAssistant`** — the seam: `summarize(List<AiMessage>)` + `suggestReplies(...)`.
   `AiMessage` = display name + `mine` flag + text (media reduced to a marker — no ids/bytes to the model).
3. **`ai.StubAiAssistant`** (`@ConditionalOnProperty enabled=false`, default) — deterministic output,
   no key/network, so the feature + demo run offline.
4. **`ai.GroqAiAssistant`** (`@ConditionalOnProperty enabled=true`) — a plain `RestClient` against the
   **OpenAI-compatible** `/chat/completions` API (Groq default; Ollama/OpenAI = base-url + model swap).
   No vendor SDK. Failure degrades to a friendly string, never a 500.
5. **`ai.AiService`** — builds context from `MessageService.history` (inherits the membership check),
   resolves sender names via `ParticipantRepository`, delegates to the assistant.
6. **`ai.AiController`** — `POST /v1/conversations/{id}/summarize`, `.../suggest-replies`.
   `application.yml`: `linkup.ai.*` (enabled=false default).

## Build order (frontend)
- **`AiService`** — `summarize()` / `suggestReplies()` per conversation.
- **`Home`** — ✨ Summarize button (header) → dismissible **summary banner**; 💡 button (composer)
  → **smart-reply chips** above the input; tapping a chip fills the draft. AI state clears on
  conversation switch.

## Infra
- None new — Groq is a hosted API (no container). Compose passes `LINKUP_AI_*` from the host so
  `LINKUP_AI_ENABLED=true LINKUP_AI_API_KEY=… docker compose up` uses the real provider; default is
  the stub.

## Verify
- **AI assist (chaos-free):** `e2e/npm run demo:ai` — alice/bob exchange 3 messages; `POST
  /summarize` returns a non-empty recap; `POST /suggest-replies` returns ≥1 chips. Green on the stub
  (no key); flip `LINKUP_AI_ENABLED=true` + a Groq key and the same endpoints return real output.
- **In the app:** ✨ Summarize shows a banner; 💡 shows reply chips; a tapped chip lands in the composer.

## Why (one line each)
AI is an **on-demand feature**, not on the send path — a slow/unavailable model never blocks
messaging (failure degrades to a string). The **`AiAssistant` interface + stub** keeps the whole app
provable offline with no key and makes the real provider a one-bean swap. **OpenAI-compatible HTTP,
not a vendor SDK** → Groq / Ollama / OpenAI differ only by base-url + model, so we're not locked in.
Context is **names + text only** (media → marker) → no ids, tokens, or bytes leave the box.
Authorization is **reused** from `MessageService.history` → the AI endpoints can't read a conversation
you're not in. (Async moderation — a third Kafka consumer group — is the documented next AI story.)

## Decisions referenced
- Phase 4 flagship = **AI assist** (chosen over E2E / calls / search). Provider-agnostic via the
  OpenAI-compatible API; provider abstraction mirrors the FCM/media sender pattern. Interview
  deep-dive: doc 12.
