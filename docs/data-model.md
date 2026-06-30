# Data Model & Schema

The domain model and how it maps to storage. **Heart of the system:** conversations contain
messages; **delivery state is per-recipient-device**; **history is an append-only, ordered log
per conversation**.

Polyglot by design ([ADR-0003](./adr/0003-polyglot-persistence.md)): relational **metadata** in
Postgres, **messages** in Cassandra at scale. **Phase 0 keeps everything in Postgres** — the
split is the deliberate "scale" chapter, not day-1 complexity.

---

## 1. Domain model (target)

```
User           (id, displayName, username, status, createdAt)

Device         (id, userId, platform[web|ios|android], pushToken,
                publicIdentityKey, lastSeenAt, createdAt)
                -- multi-device is first-class; fan-out targets DEVICES, not users

Conversation   (id, type[direct|group], title, createdAt, lastMessageAt)
                -- 'direct' = exactly 2 participants; 'group' = N

Participant    (conversationId, userId, role[member|admin], joinedAt,
                lastReadSeq, mutedUntil)
                -- lastReadSeq drives unread counts + read receipts

Message        (id, conversationId, senderId, clientMsgId,
                seq, type[text|image|voice|video|file|system],
                body, mediaRef, createdAt, editedAt, deletedAt)
                -- seq        = MONOTONIC per-conversation sequence (ordering truth)
                -- clientMsgId = sender-generated UUID for idempotent send + dedup

Receipt        (messageId, recipientDeviceId, state[sent|delivered|read], updatedAt)
                -- per-device delivery/read state; aggregated for the double-tick

Attachment     (id, messageId, kind, blobKey, mimeType, sizeBytes,
                width, height, durationMs, thumbnailKey)

PushOutbox     (id, deviceId, messageId, payload, sent, createdAt)
                -- decouples "must notify offline device" from delivery
```

---

## 2. Key design rules

- **UUID primary keys**, application-assigned — non-enumerable, generatable before insert,
  shard/region-friendly (no central sequence). ([ADR-0011](./adr/0011-flyway-schema.md) covers schema ownership.)
- **`seq` is the ordering truth**, assigned server-side atomically per conversation. Clients sort
  and sync on `seq`; `createdAt` is display-only. ([ADR-0002](./adr/0002-server-assigned-seq.md))
- **`(conversationId, clientMsgId)` is unique** — the idempotency key that makes a retried send a
  no-op and lets the client dedup. ([ADR-0004](./adr/0004-at-least-once-plus-dedup.md))
- **Receipts are per-device** — the double-tick aggregates per-recipient-device state.
- **Enums stored as strings**, never ordinals (reordering must not corrupt rows).
- **Timestamps are `timestamptz`, stored/compared in UTC.**

---

## 3. Storage mapping

| Entity | Phase 0 store | Target store | Why |
|---|---|---|---|
| User, Device | Postgres | Postgres | relational, transactional, low volume |
| Conversation, Participant | Postgres | Postgres | joins, membership, read cursors |
| **Message** | **Postgres** | **Cassandra/Scylla** | append-only, write-heavy, "last N by convo" = wide-column partition (`partition=conversationId`, `clustering=seq DESC`) |
| Receipt | Postgres | Postgres/Cassandra | per-device state |
| Presence, typing | — | **Redis (TTL)** | high-churn, ephemeral, never durable |
| `seq` counter | Postgres row / `FOR UPDATE` | Redis `INCR` | atomic per-conversation increment |
| Attachment blob | — | S3/MinIO + CDN | bytes never touch the chat path ([ADR-0005](./adr/0005-direct-to-blob-media.md)) |

No cross-store transaction across Postgres↔Cassandra → handled via outbox/idempotency, not 2PC.

---

## 4. Current schema (shipped)

- **`V1__init_users_and_devices.sql`** (Day 1) — `users`, `devices` (+ `idx_devices_user_id`).
- **`V2__conversations_and_participants.sql`** (Day 2) — `conversations`, `participants`
  (unique `(conversation_id, user_id)`, `last_read_seq`, role; FK indexes).
- **`V3__messages.sql`** (Day 4) — `messages` (`seq`, `client_msg_id`, type, body, edited/deleted
  tombstones; unique `(conversation_id, seq)` and `(conversation_id, client_msg_id)`; index
  `(conversation_id, seq)`) + `conversations.last_seq` (the per-conversation seq counter).

Schema is owned by **Flyway**; Hibernate runs `ddl-auto=validate` (asserts mappings match, never
mutates). ([ADR-0011](./adr/0011-flyway-schema.md))

---

## 5. Planned migrations (as phases land)

| Migration | Adds | Phase / Day |
|---|---|---|
| `V4__receipts.sql` | `receipts` (per recipient device) — drives the double-tick + unread | Day 7 |
| `V5__attachments.sql` | `attachments` (blobKey, thumbnails) | Day 10 |
| `V6__push_outbox.sql` | `push_outbox` | Day 11 |

Each is written, reviewed, and applied as the matching feature is built — never ahead of it.
