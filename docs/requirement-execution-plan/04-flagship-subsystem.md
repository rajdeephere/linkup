# Phase 04 — One flagship subsystem ⭐ (pick ONE, do it well)

**Status:** ✅ done — **AI assist** (Day 12: summarize + smart replies · Day 13: async moderation + caching + rate-limit) · **Roadmap:** Days 12–13

## Goal
Add **one** deep, standout subsystem — done *well*, not three half-built. Each is its own demo and
war-story. Also a good slot for the optional message-scale store split / edit-delete polish (Day 12).

## Choose one
| Option | Why | Cost / risk | ADR |
|--------|-----|-------------|-----|
| **AI assist** (recommended — best ROI) | Claude API consumer: smart replies, thread summarize, translate, moderation pass | reconciling with E2E (client-side only) | — |
| **E2E encryption** (strongest depth signal) | libsignal client-side, opt-in convos, X3DH + Double Ratchet; server stores only ciphertext | multi-device E2E is hard; kills server-side search/AI/push-preview on E2E convos | [0006](../adr/0006-e2e-libsignal.md) |
| **Voice/video calls** | WebRTC signaling over the socket + integrate LiveKit/mediasoup SFU + TURN | extra stateful SFU + TURN to operate | [0007](../adr/0007-sfu-for-calls.md) |
| **Full-text search** | OpenSearch indexer over non-E2E convos | only non-E2E content is searchable | — |

## Scope
**In:** exactly one of the above, end-to-end with its Angular surface. **Out:** the other three (note
them as "future"); don't start a second flagship until the first is solid.

## Done when
- [x] The chosen flagship works end-to-end and is demoable. **(Day 12 ✅ — AI summarize + smart replies, `demo:ai`)**
- [x] Its failure modes are understood. **(AI latency/outage: on-demand only, off the send path; failure degrades to a string, never a 500)**

## As-built (Days 12–13)
**AI assist**, provider-agnostic via the OpenAI-compatible chat API (Groq default; Ollama/OpenAI =
base-url + model swap). `AiAssistant` interface with a deterministic `StubAiAssistant` default
(no key, demo-green) and a real `GroqAiAssistant` drop-in.
- **On-demand (Day 12):** `POST …/summarize` + `…/suggest-replies`, membership-checked, media reduced
  to a marker (no bytes/ids to the model).
- **Async (Day 13):** moderation via a **3rd Kafka consumer group** (`linkup-ai`) on `message.created`
  → flags toxic/spam (⚠️ overlay, `GET …/moderation`), idempotent per message.
- **Polish (Day 13):** seq-keyed summary caching (Redis) + per-user rate limiting (429).
- **Deferred:** streaming summaries (SSE) — UX-only, not headless-provable.

## Maps to
- ADRs depend on choice (0006 / 0007); AI + search have no new ADR (server-side consumers).
- Hard scenarios: E2E new-device (#12), abusive/spam moderation (#13), message-during-call (#15)
