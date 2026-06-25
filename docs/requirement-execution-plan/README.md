# LinkUp — Requirement Execution Plan

> The **what & why** of the build, phase by phase. Each phase is a verifiable milestone with a
> single demoable outcome. This is the altitude above the runbooks: it says *what to build and why
> now*; the [`../step-by-step-implementation/`](../step-by-step-implementation/) docs say *how*.

**Golden rule:** make delivery **correct on one server** before you make it **scale across many.**
Most people invert this — building the fan-out cluster before the message even persists reliably — and
drown. Phases 0–1 are the *real* project; the rest is depth.

## The arc

| Phase | Theme | Demoable outcome | Roadmap days | Status |
|-------|-------|------------------|--------------|--------|
| [00](./00-single-server-correct-chat.md) | Correct chat on one server | ordered, idempotent, offline-tolerant 1:1 + group chat | 1–6 | 🟡 in progress (Day 1 ✅) |
| [01](./01-realtime-ux-receipts-presence.md) | Real-time feel | double-tick receipts, presence, typing, reconnect/sync | 6–7 | ⬜ |
| [02](./02-scale-the-fanout.md) | Scale the fan-out ⭐ | A on pod-1 → B on pod-7; pod-kill zero-loss | 8–9 | ⬜ |
| [03](./03-media-and-push.md) | Media + push | send a photo + voice note; background → push | 10–11 | ⬜ |
| [04](./04-flagship-subsystem.md) | One flagship ⭐ | AI assist **or** E2E **or** calls, end-to-end | 12–13 | ⬜ |
| [05](./05-deploy-and-productize.md) | Deploy + multi-region | live public URL; trace across pods; (stretch) 2nd region | 14–15 | ⬜ |

## How to read a phase doc
Each has: **Goal · Scope (in/out) · Architecture delta · Done-when checklist · Maps to (days/ADRs)**.
A phase isn't done until every "done-when" box is checked **and** you can demo it.

## Relationship to other docs
- **Why each decision** → [`../adr/`](../adr/)
- **How the app works** → [`../ARCHITECTURE.md`](../ARCHITECTURE.md)
- **Step-by-step build/deploy** → [`../step-by-step-implementation/`](../step-by-step-implementation/)
