# Impl 01 — Day 1: Auth + domain skeleton

**Outcome:** register/login returns a JWT; `GET /v1/users/me` works behind auth; `User`+`Device`
persisted via Flyway; Angular login/home proves the loop; `SocketService` stub establishes the RxJS
surface. **Status:** ✅ shipped & verified (8/8 scenarios).

## Prerequisites
- Java 21, Maven 3.9+, Node 24, Docker, Angular CLI 20.
- Infra running — see [deployment/01-local-docker-dev](../deployment/01-local-docker-dev.md).

## Build order (backend)
1. **Scaffold** `linkup/backend` — `pom.xml` on `spring-boot-starter-parent` 3.4, Java 21. Deps: web,
   security, validation, data-jpa, flyway-core + flyway-database-postgresql, postgresql, jjwt
   (api/impl/jackson), lombok, test + spring-security-test. → *Parent BOM pins compatible versions.*
2. **Config** `application.yml` — datasource (Postgres `:5433`), `jpa.hibernate.ddl-auto=validate`,
   `open-in-view=false`, UTC, `flyway.enabled=true`, `linkup.security.jwt.*` from env with dev fallback,
   `server.port=8081`. → [ADR-0011](../../adr/0011-flyway-schema.md),
   [ADR-0010](../../adr/0010-stateless-jwt-auth.md).
3. **Migration** `db/migration/V1__init_users_and_devices.sql` — `users`, `devices` (+ index). →
   schema owned by Flyway; Hibernate only validates.
4. **Domain** `user/` — `User`, `Device` (+ `UserStatus`, `Platform` enums stored as STRING), repos.
   → *Device is first-class from day 1 because fan-out targets devices, not users.*
5. **Security** `config/SecurityConfig` — stateless, CSRF off, `permitAll` `/v1/auth/**`, BCrypt encoder,
   `AuthenticationManager` bean, CORS for `:4200`. `auth/JwtAuthenticationFilter` runs before
   `UsernamePasswordAuthenticationFilter`.
6. **JWT** `config/JwtProperties` (typed config) + `auth/JwtService` (HS256 issue/verify) +
   `auth/AppUserPrincipal` (carries `userId`) + `auth/CustomUserDetailsService` (login path only).
7. **Auth flow** `auth/AuthService` (register/login → mint JWT, bind a `Device`) + `auth/AuthController`
   + DTOs (`RegisterRequest`/`LoginRequest`/`AuthResponse`, Bean-Validation annotated).
8. **/me** `user/UserController` (`@AuthenticationPrincipal` → load by id → `MeResponse`).
9. **Errors** `common/GlobalExceptionHandler` — uniform `ApiError`; **generic 401** for bad creds
   (no username enumeration); 409 username taken; 400 validation with `fieldErrors`.

## Build order (frontend)
1. Scaffold Angular 20 (`--style=scss --ssr=false`, standalone, signals).
2. `core/auth` — `auth.models`, `TokenStorage` (localStorage; separate to break the interceptor→
   AuthService→HttpClient cycle), `AuthService` (signals for session), functional `authInterceptor`
   (attach Bearer to API origin only), functional `authGuard` (UX gate).
3. `core/realtime/socket.service.ts` — **stub** exposing `connection$` (BehaviorSubject) + `messages$`
   (Subject); real STOMP arrives Day 3. → [ADR-0009](../../adr/0009-angular-rxjs-streams.md).
4. `features/login` (reactive form) + `features/home` (calls `/me`, proves the loop).
5. Wire `app.config` (`provideHttpClient(withInterceptors([authInterceptor]))`) + lazy routes + guard.

## Verify
```bash
BASE=http://localhost:8081
curl -s -o /dev/null -w "%{http_code}\n" $BASE/v1/users/me                       # 403 (protected)
curl -s -X POST $BASE/v1/auth/register -H "Content-Type: application/json" \
  -d '{"username":"alice","displayName":"Alice","password":"password123","platform":"WEB"}'  # 201 + token
TOKEN=...   # accessToken from login
curl -s $BASE/v1/users/me -H "Authorization: Bearer $TOKEN"                       # 200, no passwordHash
```
Expected matrix: no-token 403 · register 201 · login 200 · /me 200 · wrong-pw 401 (generic) ·
dup-username 409 · short-pw 400(+fieldErrors) · tampered-token 403.

## Why (one line each)
Stateless JWT → any pod authenticates any request (scale). Flyway+validate → schema is reviewed code,
not ORM guesswork. Device first-class → no painful retrofit when real-time fan-out lands. Generic 401 →
no username enumeration. SocketService stub → app codes against a stable stream surface before Day 3.

## Decisions referenced
- [ADR-0009 Angular/RxJS](../../adr/0009-angular-rxjs-streams.md) ·
  [ADR-0010 stateless JWT](../../adr/0010-stateless-jwt-auth.md) ·
  [ADR-0011 Flyway schema](../../adr/0011-flyway-schema.md)
