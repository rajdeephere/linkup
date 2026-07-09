/**
 * LinkUp demo — async AI moderation (Day 13 · scenario #13).
 *
 * The `linkup-ai` Kafka consumer (a 3rd consumer group off message.created) classifies every TEXT
 * message in the background and flags toxic/spam content. No user action. alice sends one abusive
 * message and one clean message; we then read GET /moderation and assert the abusive one is flagged
 * and the clean one is not. Deterministic on the StubAiAssistant (keyword-based, no key); with real
 * Groq (LINKUP_AI_ENABLED=true) the same pipeline uses model classification.
 *
 * Needs the stack up + seed users. Node 18+ (global fetch). Env: DEMO_GW (default :8081).
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
const authed = (t, e = {}) => ({ Authorization: `Bearer ${t}`, ...e });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const connect = (t, out) => new Promise((res) => {
  const c = new Client({
    webSocketFactory: () => new WebSocket(`${GW.replace('http', 'ws')}/ws`),
    connectHeaders: { Authorization: `Bearer ${t}` }, reconnectDelay: 0,
    onConnect: () => { c.subscribe('/user/queue/messages', (f) => out.push(JSON.parse(f.body))); res(c); },
  });
  c.activate();
});

(async () => {
  const alice = await login('alice'), bob = await login('bob');
  const convo = await (await api('/v1/conversations', {
    method: 'POST', headers: authed(alice.accessToken, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ type: 'DIRECT', memberUserIds: [bob.userId] }),
  })).body;

  const echoes = [];
  const ac = await connect(alice.accessToken, echoes);
  await sleep(400);

  const toxicId = crypto.randomUUID(), cleanId = crypto.randomUUID();
  const send = (id, text) => ac.publish({
    destination: `/app/conversations/${convo.id}/send`,
    body: JSON.stringify({ clientMsgId: id, type: 'TEXT', body: text }),
  });
  send(toxicId, "you are an idiot, shut up");   // matches the stub's harassment keywords
  send(cleanId, 'looking forward to the release on Friday');
  await sleep(600);

  // Resolve server message ids from the echoes.
  const toxicMsg = echoes.find((m) => m.clientMsgId === toxicId);
  const cleanMsg = echoes.find((m) => m.clientMsgId === cleanId);

  // The moderation consumer is async — poll GET /moderation until both are processed.
  let flaggedIds = new Set();
  for (let i = 0; i < 15; i++) {
    await sleep(700);
    const res = await api(`/v1/conversations/${convo.id}/moderation`, { headers: authed(alice.accessToken) });
    flaggedIds = new Set((res.body || []).map((f) => f.messageId));
    if (flaggedIds.has(toxicMsg?.id)) break;   // toxic one has been flagged
  }

  const toxicFlagged = flaggedIds.has(toxicMsg?.id);
  const cleanFlagged = flaggedIds.has(cleanMsg?.id);
  const flags = (await api(`/v1/conversations/${convo.id}/moderation`, { headers: authed(alice.accessToken) })).body || [];
  const toxicFlag = flags.find((f) => f.messageId === toxicMsg?.id);

  console.log(`abusive message flagged:   ${toxicFlagged ? `YES (${toxicFlag?.category}: ${toxicFlag?.reason})` : 'NO'}`);
  console.log(`clean message NOT flagged: ${!cleanFlagged ? 'YES' : 'NO'}`);

  ac.deactivate();
  const ok = toxicFlagged && !cleanFlagged;
  console.log(ok
    ? '\n✓ moderation verified — abusive message flagged by the async linkup-ai consumer; clean one left alone'
    : '\n✗ moderation did not behave as expected');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); process.exit(1); });
