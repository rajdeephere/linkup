# ADR-0005 — Direct-to-blob media upload (presigned URLs)

**Status:** Proposed (Phase 3) · **Date:** 2026-06-19

## Context
Images, voice notes, and video are large. Streaming those bytes through the WebSocket/API tier would
melt it under load and couple chat latency to upload size.

## Decision
**Never stream media bytes through the app tier.** The client requests a **presigned URL**, uploads
straight to S3/MinIO, then sends a chat message carrying only the **`blobKey`** (a reference). Delivery
is via CDN.

## Consequences
- The chat path carries tiny reference messages; uploads scale independently on object storage.
- Extra round trip (presign → upload → send) and a server-side validation step (size/type/virus).
- Orphan-blob cleanup needed (uploaded but never referenced).

## Alternatives
- **Proxy bytes through the API:** simpler client flow, but melts the API tier on video and couples
  chat latency to upload throughput.

## Revisit if
A future requirement needs server-side processing of bytes inline (rare) — even then, do it async off
the blob, not in the chat path.
