# LinkUp — Demos & guarantee checks (Playwright)

Browser-driven demos that **prove the delivery guarantees visually** — and double as smoke
checks (they assert, then exit non-zero on failure). Kept in its own package so Playwright is
never pulled into the app's Docker build.

## Setup (one time)

```bash
cd frontend/e2e
npm install                 # installs playwright
npx playwright install chromium   # one-time browser download (~150–400 MB)
```

## Run

The LinkUp stack must be up (`docker compose -f ../../infra/docker-compose.yml up -d --build`)
with the seed users present.

```bash
npm run demo:optimistic     # optimistic send: pending 🕓 → sent ✓
```

Screenshots land in `e2e/output/` (`1-pending.png`, `2-sent.png`). Watch it live in a real
window with `DEMO_HEADED=1 npm run demo:optimistic`.

## What each demo shows

| Script | Proves | ADR / scenario |
|--------|--------|----------------|
| `optimistic-send.demo.cjs` | The message renders instantly as **pending** even while the server's echo is held (simulated slow link), then reconciles to **sent** by `clientMsgId` — no duplicate, no flicker | ADR-0004 · Day 5 |

It works by **intercepting the WebSocket** (`page.routeWebSocket`) and delaying only the
server→client echo frame, so the pending window is long enough to see and screenshot.

## Config (env vars)

| Var | Default | Meaning |
|-----|---------|---------|
| `DEMO_APP_URL` | `http://localhost:4200` | frontend URL |
| `DEMO_WS_URL` | `ws://localhost:8081/ws` | WebSocket URL |
| `DEMO_USER` / `DEMO_PASS` | `alice` / `password123` | login |
| `DEMO_PEER` | `Bob` | conversation label to open |
| `DEMO_ECHO_DELAY_MS` | `3000` | how long to hold the echo |
| `DEMO_HEADED` | (unset) | `1` to show a visible browser |

## Adding more demos
Drop another `*.demo.cjs` here and add a `demo:<name>` script. Good candidates as they ship:
cross-pod fan-out, pod-kill zero-loss, reconnect sync — the §9 hard scenarios.
