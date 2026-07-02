/**
 * LinkUp demo — optimistic send on a (simulated) slow network.
 *
 * Drives a real browser: logs in, opens a conversation, and sends a message while HOLDING
 * the server's echo for a few seconds — so you can see the message render instantly as
 * `pending` (🕓) and then reconcile to `sent` (✓) when the echo arrives. Proves ADR-0004:
 * at-least-once delivery + client dedup on clientMsgId = exactly-once display.
 *
 * Produces two screenshots in ./output and asserts the transition (so it doubles as a check).
 *
 * Config via env (all optional):
 *   DEMO_APP_URL        default http://localhost:4200
 *   DEMO_WS_URL         default ws://localhost:8081/ws
 *   DEMO_USER / DEMO_PASS   default alice / password123
 *   DEMO_PEER           conversation label to open (default "Bob")
 *   DEMO_ECHO_DELAY_MS  how long to hold the echo (default 3000)
 *   DEMO_HEADED=1       watch it in a visible browser window
 *
 * Prereqs: `npm install` here, then `npx playwright install chromium`. See README.
 */
const path = require('path');
const { chromium } = require('playwright');

const APP = process.env.DEMO_APP_URL || 'http://localhost:4200';
const WS = process.env.DEMO_WS_URL || 'ws://localhost:8081/ws';
const USER = process.env.DEMO_USER || 'alice';
const PASS = process.env.DEMO_PASS || 'password123';
const PEER = process.env.DEMO_PEER || 'Bob';
const ECHO_DELAY_MS = Number(process.env.DEMO_ECHO_DELAY_MS || 3000);
const HEADED = process.env.DEMO_HEADED === '1' || process.env.DEMO_HEADED === 'true';
const OUT = path.join(__dirname, 'output');
const BODY = 'Optimistic send on a throttled network 🚀';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Launch chromium whether the user installed the full build or the headless shell. */
async function launch() {
  const opts = { headless: !HEADED };
  try {
    return await chromium.launch(opts);
  } catch {
    return await chromium.launch({ ...opts, channel: 'chromium' }); // `playwright install chromium`
  }
}

(async () => {
  const browser = await launch();
  const page = await browser.newPage({ viewport: { width: 1100, height: 780 } });

  // Intercept the app's WebSocket: pass client→server through instantly, but delay the
  // server→client message echo (the frame carrying clientMsgId) to mimic a slow link.
  await page.routeWebSocket(WS, (ws) => {
    const server = ws.connectToServer();
    ws.onMessage((m) => server.send(m));
    server.onMessage(async (m) => {
      const text = typeof m === 'string' ? m : m.toString();
      if (text.includes('clientMsgId')) await sleep(ECHO_DELAY_MS);
      ws.send(m);
    });
  });

  // 1. Log in.
  await page.goto(`${APP}/login`, { waitUntil: 'networkidle' });
  await page.getByPlaceholder(USER).fill(USER);
  await page.locator('input[type=password]').fill(PASS);
  await page.getByRole('button', { name: 'Log in' }).click();

  // 2. Open the conversation and wait for the socket to go green.
  await page.locator('.convo', { hasText: PEER }).first().click();
  await page.waitForSelector('.conn-connected', { timeout: 15000 });
  await sleep(800);

  // 3. Send.
  const input = page.locator('.composer input');
  await input.fill(BODY);
  await input.press('Enter');

  // 4. PENDING (echo still held).
  await sleep(500);
  const pending = await page.locator('.bubble.pending').count();
  await page.screenshot({ path: path.join(OUT, '1-pending.png') });
  console.log(`[pending] .bubble.pending = ${pending}  → screenshot output/1-pending.png`);

  // 5. SENT (echo delivered).
  await sleep(ECHO_DELAY_MS + 800);
  const stillPending = await page.locator('.bubble.pending').count();
  const sentTick = (await page.locator('.row.mine .bubble').last().textContent())?.includes('✓');
  await page.screenshot({ path: path.join(OUT, '2-sent.png') });
  console.log(`[sent] still pending = ${stillPending}, sent tick = ${sentTick}  → screenshot output/2-sent.png`);

  await browser.close();

  const ok = pending === 1 && stillPending === 0 && sentTick === true;
  console.log(ok ? '\n✓ optimistic send verified (pending → sent, no duplicate)' : '\n✗ assertions failed');
  process.exit(ok ? 0 : 1);
})().catch((e) => {
  console.error('DEMO ERROR:', e.message);
  process.exit(1);
});
