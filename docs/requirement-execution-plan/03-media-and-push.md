# Phase 03 — Media + push notifications

**Status:** ✅ done (Day 10 media + Day 11 push) · **Roadmap:** Days 10–11

## Goal
Send a **photo and a voice note** without blocking the chat path, and get **woken by a push** when the
tab is backgrounded — notified exactly once.

## Scope
**In:** direct-to-blob presigned upload (images/voice/video/files); thumbnail/transcode worker (Kafka
consumer); CDN delivery; push outbox + Kafka consumer → FCM (web push for the Angular PWA) for offline
devices, deduped vs in-app, badge counts. **Out:** E2E, calls.

## Architecture delta
```
   Media (out-of-band):  client ──presign──► POST /v1/media/presign ──PUT──► S3/MinIO ──► CDN
                         chat message carries only blobKey (never bytes)
   Push:  Kafka message.created ──► push consumer ──(presence check + dedup)──► FCM/APNs for OFFLINE devices
```
([ADR-0005 direct-to-blob](../adr/0005-direct-to-blob-media.md),
[ADR-0008 push outbox](../adr/0008-push-outbox.md))

## Done when
- [x] Send a photo + a voice note; a large upload does **not** block the chat path. **(Day 10 ✅)**
- [x] Background the tab → receive a web-push notification; foreground → **no duplicate**. **(Day 11 ✅)**
- [x] Badge counts reflect unread per device. **(Day 11 ✅ — unread rides the push payload → `setAppBadge`)**

## Maps to
- ADRs: [0005](../adr/0005-direct-to-blob-media.md), [0008](../adr/0008-push-outbox.md)
- Hard scenarios: huge upload (#11), duplicate push vs in-app (#10)
