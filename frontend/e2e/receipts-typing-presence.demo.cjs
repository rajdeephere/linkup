/**
 * LinkUp demo — presence + read receipts + typing across two browsers (Day 7).
 *
 * Two independent browser contexts (alice + bob). Verifies:
 *   • presence  — bob logs in → alice sees the online dot / "online"
 *   • receipts  — alice sends; bob (viewing) marks it read → alice's tick turns blue ✓✓
 *   • typing    — alice types → bob's header shows "Alice is typing…"
 *
 * Screenshots in ./output. Asserts + exits non-zero on failure (doubles as a check).
 * Config: DEMO_APP_URL (default http://localhost:4200), DEMO_HEADED=1 to watch.
 */
const path = require('path');
const { chromium } = require('playwright');

const APP = process.env.DEMO_APP_URL || 'http://localhost:4200';
const HEADED = process.env.DEMO_HEADED === '1' || process.env.DEMO_HEADED === 'true';
const OUT = path.join(__dirname, 'output');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function launch() {
  const opts = { headless: !HEADED };
  try { return await chromium.launch(opts); }
  catch { return await chromium.launch({ ...opts, channel: 'chromium' }); }
}

async function loginAndOpen(page, user, peerLabel) {
  await page.goto(`${APP}/login`, { waitUntil: 'networkidle' });
  await page.getByPlaceholder('alice').fill(user);
  await page.locator('input[type=password]').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await page.waitForSelector('.conn-connected', { timeout: 15000 });
  await page.locator('.convo', { hasText: peerLabel }).first().click();
  await sleep(600);
}

(async () => {
  const browser = await launch();
  const alice = await (await browser.newContext()).newPage();
  const bob = await (await browser.newContext()).newPage();

  // alice first (so bob's later login triggers a presence event to alice)
  await loginAndOpen(alice, 'alice', 'Bob');
  await sleep(800);
  await loginAndOpen(bob, 'bob', 'Alice');
  await sleep(1500);

  // 1) PRESENCE
  const aliceHeader = (await alice.locator('.thread-header .sub').first().textContent()) || '';
  const presenceDot = await alice.locator('.thread-header .online-dot').count();
  console.log(`1 PRESENCE : alice header="${aliceHeader.trim()}" dot=${presenceDot}`);

  // 2) READ RECEIPT — alice sends, bob is viewing → alice's tick goes blue
  const body = 'day 7 receipt check';
  await alice.locator('.composer input').fill(body);
  await alice.locator('.composer input').press('Enter');
  await sleep(1800);
  const readTicks = await alice.locator('.row.mine .bubble .read').count();
  console.log(`2 RECEIPT  : alice blue-tick (read ✓✓) elements = ${readTicks}`);
  await alice.screenshot({ path: path.join(OUT, 'alice-read-tick.png') });

  // 3) TYPING — alice types → bob's header shows typing
  await alice.locator('.composer input').fill('typing a reply');
  await sleep(900);
  const bobTyping = (await bob.locator('.thread-header .sub.typing').first().textContent().catch(() => null)) || '';
  console.log(`3 TYPING   : bob header typing = "${bobTyping.trim()}"`);
  await bob.screenshot({ path: path.join(OUT, 'bob-sees-typing.png') });

  await browser.close();
  const ok = presenceDot > 0 && readTicks > 0 && bobTyping.toLowerCase().includes('typing');
  console.log(ok ? '\n✓ presence + read-receipt + typing verified' : '\n✗ an assertion failed');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); process.exit(1); });
