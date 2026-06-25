# Phase 04 — One flagship subsystem ⭐ (pick ONE, do it well)

**Status:** ⬜ · **Roadmap:** Days 12–13

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
- [ ] The chosen flagship works end-to-end and is demoable.
- [ ] Its failure modes are understood (e.g. E2E ↔ server-feature conflicts; SFU/TURN down; AI latency).

## Maps to
- ADRs depend on choice (0006 / 0007); AI + search have no new ADR (server-side consumers).
- Hard scenarios: E2E new-device (#12), abusive/spam moderation (#13), message-during-call (#15)
