# ADR-0007 — Integrate an SFU for calls (don't build media routing)

**Status:** Proposed (Phase 4) · **Date:** 2026-06-19

## Context
Voice/video calls need WebRTC media routing. SFU/TURN, codecs, congestion control, and NAT traversal
are a multi-year specialty.

## Decision
**Build the signaling, integrate the media.** LinkUp implements WebRTC **signaling** (offer/answer/ICE
over our existing WebSocket) and integrates a **battle-tested SFU** (LiveKit or mediasoup) + **TURN**
for media routing. We do **not** build an SFU.

## Consequences
- Calls feature ships without owning the hardest part of real-time media.
- An extra stateful service (the SFU) + TURN servers to operate.
- Signaling stays in our domain (auth, who-can-call-whom) where it belongs.

## Alternatives
- **Mesh P2P:** no media server, but bandwidth/CPU explode past ~4 participants; falls apart for groups.
- **Build an SFU:** a different multi-year project; not the point of this system.

## Revisit if
Scale/cost demands a self-hosted media plane — still integrate/operate an OSS SFU rather than writing one.
