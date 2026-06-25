# ADR-0010 — Stateless JWT auth (HS256)

**Status:** Accepted (Phase 0, implemented Day 1) · **Date:** 2026-06-24

## Context
The API and (soon) WebSocket tiers are horizontally scaled — any request or socket can land on any
pod. Server-side sessions would force sticky sessions or a shared session store hit on every request.

## Decision
**Stateless JWT** (HS256). Login mints a signed token carrying `sub`=userId + `username`; every pod
verifies it with a shared secret and rebuilds the principal **without a DB lookup**. No HTTP session
(`SessionCreationPolicy.STATELESS`); CSRF disabled (Bearer header, not an ambient cookie). Secret + TTL
come from env (`LINKUP_JWT_SECRET`, `LINKUP_JWT_TTL`) with a dev fallback.

## Consequences
- Any pod authenticates any request with zero shared state → clean horizontal scale.
- **Cannot instantly revoke** a token before expiry. Mitigations: short TTL (1h) now; refresh-token +
  Redis `jti` denylist if instant revocation becomes a requirement.

## Alternatives
- **Server-side sessions:** easy revocation, but sticky/shared-store cost on a scaled stateful tier.
- **RS256:** needed only when *other* services must verify without minting — adopt if auth splits out.

## Revisit if
A "log out everywhere / ban now" requirement appears → add refresh tokens + a short-lived denylist.
