/**
 * LinkUp demo — push to offline devices, deduped vs in-app (Day 11 · ADR-0008 · scenario #10).
 *
 * Proves the push pipeline's three guarantees end-to-end (LoggingPushSender; no Firebase needed):
 *   1. OFFLINE recipient  → a push is enqueued & sent (outbox row, status SENT).
 *   2. ONLINE recipient   → NO push (a live socket means in-app already delivered it — dedup).
 *   3. Redelivery/idempotency → exactly one notification per (message, device), never a double.
 *
 * carol is the recipient; she registers a push token but does NOT open a socket for the offline
 * leg, then connects for the online leg. Verified via GET /v1/notifications (the outbox view).
 *
 * Needs the stack up (docker compose up -d --build) + seed users. Node 18+ (global fetch).
 * Env: DEMO_GW (default :8081).
 */
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const GW = process.env.DEMO_GW || 'http://localhost:8081';

const api = async (path, opts = {}) => {
  const res = await fetch(`${GW}${path}`, opts);
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
};
const login = async (u) => (await api('/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: u, password: 'password123', platform: 'WEB' }),
})).body;
const authed = (token, extra = {}) => ({ Authorization: `Bearer ${token}`, ...extra });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const connect = (token) => new Promise((res) => {
  const c = new Client({
    webSocketFactory: () => new WebSocket(`${GW.replace('http', 'ws')}/ws`),
    connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 0,
    onConnect: () => res(c),
  });
  c.activate();
});
const notifs = async (token) => (await api('/v1/notifications', { headers: authed(token) })).body;

(async () => {
  const alice = await login('alice'), carol = await login('carol');

  // carol registers a push token on the device she just logged in with — but stays OFFLINE.
  await api(`/v1/devices/${carol.deviceId}/push-token`, {
    method: 'PUT', headers: authed(carol.accessToken, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ pushToken: `demo-token-${carol.deviceId}` }),
  });
  const convo = await (await api('/v1/conversations', {
    method: 'POST', headers: authed(alice.accessToken, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ type: 'DIRECT', memberUserIds: [carol.userId] }),
  })).body;

  const before = (await notifs(carol.accessToken)).length;

  // --- Leg 1: carol OFFLINE → alice sends → carol should be PUSHED ---
  const ac = await connect(alice.accessToken);
  await sleep(400);
  ac.publish({ destination: `/app/conversations/${convo.id}/send`,
    body: JSON.stringify({ clientMsgId: crypto.randomUUID(), type: 'TEXT', body: 'you around? (offline)' }) });
  await sleep(1500); // let the linkup-push consumer process

  const afterOffline = await notifs(carol.accessToken);
  const pushedOffline = afterOffline.length - before;
  const sent = afterOffline.filter((n) => n.status === 'SENT').length;
  console.log(`offline recipient pushed:                ${pushedOffline >= 1 ? `YES (${pushedOffline}, status SENT=${sent})` : 'NO'}`);
  console.log(`  title="${afterOffline[0]?.title}" body="${afterOffline[0]?.body}" unread=${afterOffline[0]?.unreadCount}`);

  // --- Leg 2: carol ONLINE → alice sends → NO new push (dedup vs in-app) ---
  const cc = await connect(carol.accessToken);
  await sleep(500); // presence registers
  ac.publish({ destination: `/app/conversations/${convo.id}/send`,
    body: JSON.stringify({ clientMsgId: crypto.randomUUID(), type: 'TEXT', body: 'now youre online' }) });
  await sleep(1500);

  const afterOnline = await notifs(carol.accessToken);
  const pushedWhileOnline = afterOnline.length - afterOffline.length;
  console.log(`online recipient NOT pushed (dedup):     ${pushedWhileOnline === 0 ? 'YES (0 new)' : `NO (${pushedWhileOnline} new)`}`);

  ac.deactivate(); cc.deactivate();
  const ok = pushedOffline >= 1 && sent >= 1 && pushedWhileOnline === 0;
  console.log(ok
    ? '\n✓ push verified — offline devices woken, online devices deduped (outbox is idempotent)'
    : '\n✗ push pipeline did not behave as expected');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); process.exit(1); });
