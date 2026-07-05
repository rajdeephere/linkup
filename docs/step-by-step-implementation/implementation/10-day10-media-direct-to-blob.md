# Impl 10 — Day 10: Media pipeline — direct-to-blob presigned upload ⭐

**Outcome:** send a **photo** and a **voice note** without ever streaming bytes through the app tier
— the client presigns, uploads straight to object storage, and the chat message carries only a
`blobKey`; recipients fetch bytes back via a presigned GET. **Status:** ✅ shipped & verified.
Opens **Phase 3**.

## Prerequisites
- Day 4 (send path / `MessageService`) and Day 8/9 (fan-out + Kafka log — media messages ride the
  same delivery + durable-log path, just with an attachment reference instead of a body).

## Build order (backend)
1. **AWS S3 SDK** — `pom`: `software.amazon.awssdk:s3` (bundles the presigner); `application.yml`:
   `linkup.media.*` (endpoint, region, keys, bucket, `presign-ttl`, `max-upload-bytes`,
   `allowed-content-types`). Bound by `MediaProperties`.
2. **`media.MediaConfig`** — an `S3Presigner` only (no `S3Client`): we sign, we never proxy bytes.
   `pathStyleAccessEnabled(true)` for MinIO; `endpointOverride` = the **browser-reachable** host,
   because the endpoint is part of the SigV4 signature.
3. **`media.MediaService`** — `presignUpload`: validate content-type allowlist + size cap → build a
   namespaced key `media/{userId}/{uuid}/{name}` → presigned **PUT** (Content-Type signed in).
   `presignDownload`: presigned **GET** for a `media/…` key. Bucket stays **private**.
4. **`media.MediaController`** — `POST /v1/media/presign`, `GET /v1/media/download-url?key=`.
5. **Message attachment** — `V4__message_attachments.sql` adds nullable `blob_key/mime_type/
   size_bytes/width/height/duration_ms`; `Message` fields; `MessageResponse.attachment`
   (`MessageAttachment`). `SendMessageRequest` gains an optional `attachment` and `body` is no
   longer `@NotBlank`; `MessageService.validateContent` enforces the cross-field rule (TEXT→body,
   media→attachment with an allowed mimeType, re-checked server-side).

## Build order (frontend)
1. **`MediaService`** — `upload(file)`: `POST /v1/media/presign` → `fetch(PUT)` bytes straight to
   storage → return the `{blobKey, mime, size, width/height, durationMs}` reference (image dims /
   audio duration probed locally). `resolveUrl(blobKey)`: cached presigned GET for display.
2. **`SocketService.sendAttachment`** — publishes `{clientMsgId, type, attachment}` (no body); same
   `clientMsgId` echo-reconcile contract as text.
3. **`Home`** — 📎 image picker + 🎤 voice recorder (`MediaRecorder` → webm/opus). Optimistic media
   bubble with an object-URL preview while uploading; an `effect` resolves presigned GET URLs for
   inbound media; bubbles render `<img>` / `<audio>` by type; failed upload/send → retry.

## Infra
- `docker-compose`: **MinIO** (`server /data`, API `:9000`, console `:9001`, `mc ready`
  healthcheck) + a one-shot **`minio-init`** (`mc mb linkup-media`, private). Backends get
  `LINKUP_MEDIA_ENDPOINT=http://localhost:9000` (+ keys/bucket) and `depends_on: minio (healthy)`.

## Verify
- **Direct-to-blob (chaos-free):** `e2e/npm run demo:media` — alice presigns, PUTs a PNG straight to
  MinIO (URL points at storage, **not** `/v1/…`), sends an `IMAGE` message carrying only the
  `blobKey`; bob receives it, presigns a GET, downloads from storage → **bytes match**, no body.
- **In the app:** 📎 an image and 🎤 a voice note render in the other browser; the network tab shows
  the PUT/GET hitting `:9000`, while the WebSocket frame is a tiny JSON reference.

## Why (one line each)
Never stream media through the app tier (ADR-0005) → chat latency is decoupled from upload size and
uploads scale on storage. Presigned URLs → the client talks to storage directly but only for a
short, scoped window. Private bucket + presigned GET → no public objects; access is per-request and
expiring. Content-type/size validated **before** presign **and** the mimeType re-checked on send →
the client is never trusted. Message carries a `blobKey`, not bytes → the same fan-out + Kafka log
path works unchanged. (Orphan-blob cleanup — uploaded-but-never-referenced — is the next hardening.)

## Decisions referenced
- ADR-0005 (direct-to-blob media). Hard scenario #11 (huge upload doesn't block the chat path).
  Interview deep-dive: doc 10. War-story #8.
