# Impl 03 — Day 3: WebSocket / STOMP transport

**Outcome:** a JWT-authenticated STOMP-over-WebSocket transport; two clients hold real-time
sessions and the server logs both principals; the Angular `SocketService` goes live.
**Status:** ✅ shipped & verified (valid tokens connect; bad/no token rejected at CONNECT).

## Prerequisites
- Day 1–2 done; infra + backend running.

## Build order (backend) — `com.linkup.realtime`
1. **Dependency** — add `spring-boot-starter-websocket` to `pom.xml`.
2. **`WebSocketConfig`** (`@EnableWebSocketMessageBroker`):
   - endpoint `/ws` (native WebSocket), `setAllowedOriginPatterns("http://localhost:4200")`.
   - broker prefixes: `enableSimpleBroker("/topic","/queue")`, app `/app`, user `/user`.
   - register the auth interceptor on the **client inbound channel**.
3. **`StompAuthChannelInterceptor`** (`ChannelInterceptor`): on `StompCommand.CONNECT`, read the
   `Authorization` native header, verify with the **same `JwtService`** as REST, and
   `accessor.setUser(authentication)` → binds the principal to the session. Missing/invalid → throw
   (rejects CONNECT).
4. **`WebSocketEventListener`** — `@EventListener` for `SessionConnectedEvent` /
   `SessionDisconnectEvent`; logs `principal` (the Day-3 proof; later the presence hook).
5. **Security** — `permitAll` `/ws/**` in `SecurityConfig` (HTTP handshake passes; real auth is the
   CONNECT frame).

## Build order (frontend)
1. `npm install @stomp/stompjs`; set `environment.wsUrl = ws://localhost:8081/ws`.
2. Rewrite `core/realtime/socket.service.ts` — real `@stomp/stompjs` `Client`: JWT in
   `connectHeaders`, `onConnect` subscribes `/user/queue/messages` → `messages$`; `connection$`
   reflects connecting/connected/reconnecting/disconnected; auto-reconnect + heartbeats. **Public
   surface unchanged from the stub.**
3. Wire `features/home` — `socket.connect()` on load, `disconnect()` on logout, a header
   **connection badge** (`toSignal(connection$)`).

## Verify (headless)
```js
// @stomp/stompjs + ws, connectHeaders { Authorization: Bearer <jwt> }
// alice (valid) → CONNECTED   bob (valid) → CONNECTED
// bad token → REJECTED (Invalid token on STOMP CONNECT)
// no token  → REJECTED (Missing bearer token on STOMP CONNECT)
```
Server logs `WS CONNECTED … principal=alice` and `principal=bob` — two authenticated sessions.
In the browser, two tabs both show a green **connected** badge.

## Why (one line each)
WebSocket → full-duplex push (vs polling/SSE). STOMP → sub/send/ack + destinations for free. **Auth at
CONNECT, not the handshake** → browsers can't header the upgrade; bind principal to the session.
Permit `/ws` handshake → real auth is one layer up. Simple broker → fine for one node; Redis fan-out
replaces it when we scale ([ADR-0001](../../adr/0001-redis-pubsub-fanout.md)). Native WS → only dep is
@stomp/stompjs. Stub→live with no surface change → ADR-0009 paying off.

## Decisions referenced
- [ADR-0009 Angular/RxJS socket-as-streams](../../adr/0009-angular-rxjs-streams.md) ·
  [ADR-0001 Redis fan-out](../../adr/0001-redis-pubsub-fanout.md) (the broker's scale path).
  Wire contract: [wire-protocol.md](../../wire-protocol.md) §2. Interview deep-dive: doc 04.
