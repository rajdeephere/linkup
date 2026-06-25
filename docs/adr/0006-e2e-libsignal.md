# ADR-0006 — E2E encryption via libsignal (client-side, opt-in)

**Status:** Proposed (Phase 4) · **Date:** 2026-06-19

## Context
For private conversations, only sender + recipients should be able to read content; the server should
be blind. Crypto is famously easy to get catastrophically wrong.

## Decision
**Never roll your own crypto.** Use **libsignal** (the audited Signal protocol: X3DH key agreement +
Double Ratchet) **client-side**, **opt-in per conversation**. The server stores only **ciphertext** +
**public prekey bundles**; private keys never leave the device. Multi-device uses per-device sessions;
groups use sender-keys.

## Consequences
- Strong, real confidentiality with a battle-tested implementation.
- Server-side **search, AI assist, and push-preview do not work on E2E content** — they must run
  client-side or be disabled for E2E conversations.
- Multi-device E2E is genuinely hard (per-device sessions; sender-key distribution on new device).
- Ship the **non-E2E core first**; E2E is a flagged advanced phase.

## Alternatives
- **Roll-your-own crypto:** a trap; a different multi-year project and a security liability.
- **Server-side encryption only (TLS + at-rest):** protects the wire/disk, not from the server itself.

## Revisit if
Product decides E2E should be default-on → then reconcile every server-side feature (search/AI/push)
with client-side execution up front.
