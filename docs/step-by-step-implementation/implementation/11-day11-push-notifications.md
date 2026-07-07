# Impl 11 — Day 11: Push notifications — outbox + Kafka consumer, deduped vs in-app ⭐

**Outcome:** an **offline** device gets woken by a push when a message arrives; an **online** device
does **not** (in-app delivery already covered it); each device is notified **once** per message.
**Status:** ✅ shipped & verified (LoggingPushSender; real FCM is a config-flag drop-in). Completes
**Phase 3**.

## Prerequisites
- Day 9 (Kafka `message.created` log — push is a second consumer group on it) and Day 7 (Redis
  presence — the online/offline signal that drives dedup).

## Build order (backend)
1. **`push_outbox` (V5)** — one row per (message, device); unique `(message_id, device_id)` is the
   idempotency key. `PushOutbox` entity + `PushOutboxRepository`
   (`existsByMessageIdAndDeviceId`, `findByRecipientUserIdOrderByCreatedAtDesc`).
2. **`push.PushConsumer`** — `@KafkaListener(groupId = "linkup-push")` on `message.created`: a
   **separate consumer group** from Day 9's `linkup-events`, so it gets its own copy + offset.
3. **`push.PushService`** — for each recipient (participants except sender): **online → skip**
   (dedup vs in-app); **offline →** for each device with a push token, idempotent-insert an outbox
   row and dispatch. Unread = `seq − lastReadSeq`; body = text preview or `📷 Photo`/`🎤 Voice note`.
4. **`PushSender`** — `LoggingPushSender` (default, `linkup.push.fcm.enabled=false`) proves the
   pipeline with no external creds; `FcmPushSender` (HTTP v1 via `RestClient`) is
   `@ConditionalOnProperty` and drops in when FCM is configured.
5. **Token registration** — `PUT /v1/devices/{deviceId}/push-token` (`DeviceService` checks the
   device is the caller's). **Notification center** — `GET /v1/notifications` (the outbox, newest
   first) — also what the demo asserts against.

## Build order (frontend)
- **`PushService.init(deviceId)`** — register `push-sw.js`, request Notification permission, and
  register the device's push token (a **stub** in dev; a real FCM token is the one-line swap). Called
  from `Home` after auth.
- **`public/push-sw.js`** — minimal SW: on `push` → `showNotification` + `navigator.setAppBadge`
  (badge counts); on `notificationclick` → focus/open the app. No fetch handler.

## Infra
- None new — Kafka (Day 9) + Redis presence (Day 7) already in the stack. FCM off by default.

## Verify
- **Push + dedup (chaos-free):** `e2e/npm run demo:push` — carol registers a token but stays
  **offline**; alice sends → carol gets a push (**outbox SENT**, title/body/unread populated). carol
  connects (**online**); alice sends again → **zero** new pushes (in-app covered it). One
  notification per (message, device). (Needs seed user `carol`.)
- **In logs:** a pod shows `PUSH → token=…xxxxxx | Alice — you around? (convo=…, unread=1)`.

## Why (one line each)
Push is a **different reliability domain** than in-app delivery, so it's a **separate Kafka consumer
group** off the durable log — independent offset, restartable, decoupled from live sockets. **Presence
drives dedup**: an online recipient already has it, so pushing would double-notify. The **outbox** makes
it durable + gives a retry surface; the unique `(message,device)` key makes at-least-once redelivery
**idempotent**. Body carries a **preview + ids**, never message bytes. **LoggingPushSender vs
FcmPushSender** = swap one bean; the pipeline is identical — that's the whole point of the sender
abstraction. (A standalone retry-dispatcher for the PENDING backlog is the next hardening.)

## Decisions referenced
- ADR-0008 (push outbox, deduped vs in-app). Hard scenario #10 (duplicate push vs in-app). Redis
  presence (Day 7) + Kafka log (Day 9). Interview deep-dive: doc 11. War-story #9.
