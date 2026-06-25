# Wire Protocol (v1)

The contract between client and server: REST for request/response, WebSocket/STOMP for real-time.
**Contract rules** (the spine): every send carries a client-generated **`clientMsgId`**; the
server is **idempotent on `(conversationId, clientMsgId)`** and returns the canonical message with
its server-assigned **`seq`**. Clients **dedup on `clientMsgId`** and **order on `seq`** — never
on wall-clock. Receipts and typing/presence are best-effort and never block the send path.

Legend: ✅ shipped · 🔜 next day · ⬜ later phase.

---

## 1. REST (stateless API tier)

```
POST /v1/auth/register                  → 201 {accessToken, userId, username, displayName, deviceId}   ✅
POST /v1/auth/login                     → 200 {accessToken, ...}                                        ✅
GET  /v1/users/me                       → 200 {id, username, displayName, status, createdAt}            ✅

GET  /v1/conversations                  → my conversations (last message, unread count)                 🔜 Day 2
POST /v1/conversations                  → create direct/group                                           🔜 Day 2
POST /v1/conversations/{id}/members     → add members (group)                                           🔜 Day 2
GET  /v1/conversations/{id}/messages    → history, cursor-paginated (?before=seq&limit=50)              ⬜ Day 6
POST /v1/conversations/{id}/read        → advance lastReadSeq                                           ⬜ Day 7
POST /v1/media/presign                  → presigned upload URL (returns blobKey)                        ⬜ Day 10
GET  /v1/users/{id}/presence            → online / last-seen (privacy-gated)                            ⬜ Day 7
```

**Auth:** all routes except `/v1/auth/**` require `Authorization: Bearer <jwt>`.
**Errors:** uniform `ApiError` envelope `{timestamp, status, error, message, fieldErrors}`.
- `400` validation (with `fieldErrors`) · `401` bad/missing credentials (generic, no user enumeration)
  · `403` authenticated-but-forbidden · `404` not found · `409` conflict.

---

## 2. WebSocket / STOMP (real-time tier) — ⬜ from Day 3

```
→ CONNECT                                  (auth token in header → principal + deviceId)
→ SUBSCRIBE /user/queue/messages           (my inbound message stream)
→ SUBSCRIBE /user/queue/receipts           (delivery/read updates for my sent messages)
→ SUBSCRIBE /user/queue/presence           (presence/typing for my conversations)

→ SEND  /app/conversations/{id}/send       { clientMsgId, type, body }     ← idempotent
← MESSAGE /user/queue/messages             { id, seq, senderId, clientMsgId, type, body, createdAt }
                                             ← client dedups on clientMsgId, inserts by seq

→ SEND  /app/conversations/{id}/typing     { state: start|stop }           ← ephemeral, debounced
→ SEND  /app/messages/{id}/receipt         { state: delivered|read }
← MESSAGE /user/queue/presence             { userId, status, lastSeenAt }
← MESSAGE /user/queue/receipts             { messageId, deviceId, state }

→ SEND  /app/calls/{id}/signal             { sdp|ice }                     ← WebRTC signaling ⬜ Day 13
```

**Ack semantics:** the server acks a `send` only **after** durable persist + `seq` assignment,
returning the canonical message. The client renders optimistically (pending state) and reconciles
when the canonical message (with `seq`) arrives. ([ADR-0004](./adr/0004-at-least-once-plus-dedup.md))

**Reconnect/sync:** on reconnect the client sends its last-known `seq` per conversation; the server
streams everything after it. ([ARCHITECTURE §5](./ARCHITECTURE.md)) ⬜ Day 6.

---

## 3. Idempotency & ordering contract (the non-negotiables)

| Rule | Server | Client |
|---|---|---|
| **Idempotent send** | unique `(conversationId, clientMsgId)`; retry returns the same canonical message | reuse the same `clientMsgId` on retry |
| **Ordering** | assigns monotonic `seq` per conversation atomically | sort/insert by `seq`, render in `seq` order |
| **Dedup** | — | drop a message whose `clientMsgId` is already present |
| **No wall-clock trust** | `createdAt` is metadata only | never order by `createdAt` |

---

## 4. Frontend stream surface (RxJS) — ([ADR-0009](./adr/0009-angular-rxjs-streams.md))

The client models the socket as observable streams (`SocketService`); features subscribe:
```
connection$  : 'disconnected' | 'connecting' | 'connected' | 'reconnecting'   (BehaviorSubject)
messages$    : InboundMessage   (deduped on clientMsgId, scanned into seq-ordered state)
receipts$    : ReceiptUpdate
presence$    : PresenceUpdate
typing$      : TypingUpdate
```
Day-1 ships the stub (`connection$`, `messages$`) so features code against a stable surface; Day 3
fills in the real STOMP transport without changing it.
