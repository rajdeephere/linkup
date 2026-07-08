/**
 * LinkUp demo — AI assist (Day 12): thread summarize + smart replies.
 *
 * alice and bob exchange a few messages, then alice asks the backend to (1) summarize the thread
 * and (2) suggest replies. Runs against the StubAiAssistant by default (LINKUP_AI_ENABLED=false),
 * so it's deterministic and green with NO API key; flip the backend to a real provider (Groq) and
 * the same endpoints return real AI output.
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
const authed = (t, e = {}) => ({ Authorization: `Bearer ${t}`, ...e });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const connect = (t) => new Promise((res) => {
  const c = new Client({
    webSocketFactory: () => new WebSocket(`${GW.replace('http', 'ws')}/ws`),
    connectHeaders: { Authorization: `Bearer ${t}` }, reconnectDelay: 0,
    onConnect: () => res(c),
  });
  c.activate();
});

(async () => {
  const alice = await login('alice'), bob = await login('bob');
  const convo = await (await api('/v1/conversations', {
    method: 'POST', headers: authed(alice.accessToken, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ type: 'DIRECT', memberUserIds: [bob.userId] }),
  })).body;

  const ac = await connect(alice.accessToken), bc = await connect(bob.accessToken);
  await sleep(500);
  const say = (c, id, text) => c.publish({
    destination: `/app/conversations/${convo.id}/send`,
    body: JSON.stringify({ clientMsgId: crypto.randomUUID(), type: 'TEXT', body: text }),
  });
  say(ac, convo.id, 'Can we ship the payments migration on Friday?');
  await sleep(200); say(bc, convo.id, "I'm worried about the rollback plan if it fails.");
  await sleep(200); say(ac, convo.id, "Fair. Let's add a feature flag and a dry run first.");
  await sleep(700);

  // 1. Summarize
  const sum = await api(`/v1/conversations/${convo.id}/summarize`, {
    method: 'POST', headers: authed(alice.accessToken),
  });
  const summary = sum.body?.summary || '';
  console.log(`summarize → ${sum.status}`);
  console.log(`  summary: "${summary}"`);

  // 2. Suggest replies
  const sug = await api(`/v1/conversations/${convo.id}/suggest-replies`, {
    method: 'POST', headers: authed(alice.accessToken),
  });
  const suggestions = sug.body?.suggestions || [];
  console.log(`suggest-replies → ${sug.status}`);
  suggestions.forEach((s, i) => console.log(`  ${i + 1}. ${s}`));

  ac.deactivate(); bc.deactivate();
  const ok = sum.status === 200 && summary.length > 0
    && sug.status === 200 && Array.isArray(suggestions) && suggestions.length >= 1;
  console.log(ok
    ? '\n✓ AI assist verified — summary returned + smart replies returned (provider-agnostic; stub or Groq)'
    : '\n✗ AI assist did not behave as expected');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); process.exit(1); });
