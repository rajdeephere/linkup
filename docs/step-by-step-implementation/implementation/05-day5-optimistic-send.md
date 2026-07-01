# Impl 05 — Day 5: Optimistic send

**Outcome:** a sent message renders **instantly** as `pending`, then reconciles to `sent` when
the server echo (with the real `seq`) arrives — matched by `clientMsgId`, no flicker, no
duplicate. Failed sends show a **retry**. **Status:** ✅ shipped (frontend-only; backend
unchanged). Regression: backend send/receive still passes.

## Prerequisites
- Day 4 done (the send path + `clientMsgId` dedup already exist).

## Build order (frontend)
1. **Model** `ChatMessage extends Message` with `status: 'pending' | 'sent' | 'failed'` and a
   `localSeq` (client order for messages that don't have a server `seq` yet).
2. **`SocketService.send(convId, body, clientMsgId): boolean`** — caller now supplies the
   `clientMsgId` (so it can render optimistically and reconcile the echo); returns `false` if the
   socket isn't connected.
3. **`home` send flow:**
   - `sendMessage()` — generate `clientMsgId`, push a `pending` `ChatMessage` **immediately**, clear
     the input, then `socket.send(...)`; if it returns false → mark `failed`.
   - `onMessage(echo)` — find the pending message by `clientMsgId`; if present, **replace it with the
     confirmed message** (`status: sent`, real `seq`/`id`); else it's a new message from someone else.
   - `retry(m)` — set `pending` again and re-send with the **same** `clientMsgId` (idempotent).
   - **Ordering** (`mutate` + sort): confirmed messages by server `seq`; `pending`/`failed` sort
     **after** them by `localSeq`. The `@for` tracks `clientMsgId`, so a pending bubble morphs into
     sent **in place** (no re-mount, no flicker).
4. **Template/status** — per-bubble indicator on my own messages: `🕓` pending · `✓` sent ·
   `✗ retry` (a button) failed; pending bubbles are dimmed, failed bubbles get a danger border.

## Verify
- **Browser (the real test):** throttle the network (DevTools → Slow 3G), send → the bubble appears
  **instantly** (🕓), then flips to ✓ when the echo lands. Disconnect the socket, send → the bubble
  shows **✗ retry**; reconnect and tap retry → it sends.
- **Regression (headless):** a raw STOMP send still delivers to the recipient with a server `seq`
  (backend unchanged).

## Why (one line each)
Render before the round-trip → the UI feels **instant** even on a slow link. Reconcile by
`clientMsgId` → the optimistic bubble becomes the canonical message with its real `seq`, exactly once
(ADR-0004). Track by `clientMsgId` → morph-in-place, no flicker. `localSeq` keeps un-acked messages
ordered until a server `seq` exists (ADR-0002). Retry reuses the same `clientMsgId` → a resend is
idempotent, never a duplicate.

> **Known edge (noted, not handled yet):** we mark `failed` only when the socket is *down* (send never
> reached the server). A message that reaches the server but whose echo is lost would sit `pending`
> forever — a proper **ack-timeout** (mark failed after N seconds without an echo) is the hardening
> step, alongside refresh tokens.

## Decisions referenced
- [ADR-0004 at-least-once + dedup](../../adr/0004-at-least-once-plus-dedup.md) ·
  [ADR-0002 server-assigned seq](../../adr/0002-server-assigned-seq.md) ·
  [ADR-0009 socket-as-streams](../../adr/0009-angular-rxjs-streams.md). Interview deep-dive: doc 05 (Q7).
