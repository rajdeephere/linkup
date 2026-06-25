<div align="center">

# LinkUp Frontend — The Client

**The web client for LinkUp — the lens that proves the delivery guarantees are real**
(ordering, dedup, receipts, reconnect). The delivery engine stays the star; this makes its correctness visible.

![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)
![RxJS](https://img.shields.io/badge/RxJS-7-B7178C?logo=reactivex&logoColor=white)

</div>

> Part of the **LinkUp monorepo** — siblings: [`../backend`](../backend), [`../infra`](../infra),
> design & decisions in [`../docs`](../docs).

---

## Stack & conventions

- **Angular 20** — standalone components (no NgModules), **signals** for UI state, **RxJS** for async streams.
- **Reactive forms**, a functional **HTTP interceptor** + **route guard**, lazy-loaded routes.
- Pinned to Angular **v20** (this machine runs Node 24.12; newest Angular wants ≥ 24.15).

## Status — Day 1 ✅

- Login / register screen (reactive form) + home page proving the auth loop (`GET /v1/users/me`).
- `authInterceptor` — attaches `Authorization: Bearer <token>` to API calls (our origin only).
- `authGuard` — UX gate on protected routes (real security is server-side).
- **`SocketService` stub** — establishes the RxJS stream surface (`connection$`, `messages$`) now; the
  real WebSocket/STOMP transport lands Day 3 without changing it.

## Run

Backend must be up on `http://localhost:8081` (see [`../backend`](../backend)).

```bash
npm install
npm start          # ng serve → http://localhost:4200
npm run build      # production build → dist/
npm test           # Karma/Jasmine
```

Open **http://localhost:4200** → register → home page shows your `/me` profile.

## Layout

```
src/
├── environments/environment.ts        apiBaseUrl (:8081), wsUrl (Day 3)
└── app/
    ├── app.config.ts                  providers: router + HttpClient(authInterceptor)
    ├── app.routes.ts                  lazy routes; home guarded by authGuard
    ├── core/
    │   ├── auth/                       AuthService · TokenStorage · interceptor · guard · models
    │   └── realtime/socket.service.ts  RxJS stream stub (real WS = Day 3)
    └── features/
        ├── login/                      login + register (reactive form)
        └── home/                       authenticated landing (proves /me)
```

## Architecture notes (the "why", one line each)

- **Signals for state, RxJS for events** — `session()`/`loading()` are signals; HTTP and (soon) the socket are observables.
- **Interceptor over per-call headers** — auth handled once; token attached only to our API origin.
- **`TokenStorage` is separate from `AuthService`** — breaks a circular dependency (interceptor → AuthService → HttpClient → interceptor).
- **Guard is UX, not security** — the server is the real gate.
- **Stub the socket early** — code the app against a stable stream interface so Day 3's transport drops in without churn.

## Roadmap (grows *with* the backend, never ahead)

| After backend day | Adds |
|---|---|
| 2 | conversation-list view (REST) |
| 3 | live `SocketService` over STOMP |
| 4–6 | chat thread (`seq`-ordered) · optimistic send + dedup · history + reconnect banner |
| 7+ | receipt ticks · presence/typing · media bubbles … |

Design & decisions → [`../docs`](../docs).
