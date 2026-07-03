/**
 * LinkUp demo — cross-pod fan-out (Day 8, the signature war-story · scenario #4).
 *
 * alice connects her WebSocket to POD 1 (:8091), bob to POD 2 (:8092). alice sends a message;
 * bob receives it on the OTHER pod — routed via Redis Pub/Sub (ADR-0001), not a shared socket.
 * Proves the stateful WS tier scales horizontally with no sticky sessions.
 *
 * Needs the multi-pod stack up (docker compose up -d --build) and the seed users.
 * Env: DEMO_GW (default :8081), DEMO_POD1 (:8091), DEMO_POD2 (:8092).
 */
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const GW = process.env.DEMO_GW || 'http://localhost:8081';
const POD1 = process.env.DEMO_POD1 || 'ws://localhost:8091/ws';
const POD2 = process.env.DEMO_POD2 || 'ws://localhost:8092/ws';

const login = async (u) => (await (await fetch(`${GW}/v1/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: u, password: 'password123', platform: 'WEB' }),
})).json());
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const connect = (url, token, out) => new Promise((res) => {
  const c = new Client({
    webSocketFactory: () => new WebSocket(url), connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 0,
    onConnect: () => { c.subscribe('/user/queue/messages', (f) => out.push(JSON.parse(f.body))); res(c); },
  });
  c.activate();
});

(async () => {
  const alice = await login('alice'), bob = await login('bob');
  const convo = await (await fetch(`${GW}/v1/conversations`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${alice.accessToken}` },
    body: JSON.stringify({ type: 'DIRECT', memberUserIds: [bob.userId] }),
  })).json();

  const aliceMsgs = [], bobMsgs = [];
  const ac = await connect(POD1, alice.accessToken, aliceMsgs); // alice → pod 1
  await sleep(300);
  const bc = await connect(POD2, bob.accessToken, bobMsgs);     // bob   → pod 2
  await sleep(700);
  console.log('alice → pod 1 (:8091),  bob → pod 2 (:8092)');

  const id = crypto.randomUUID();
  ac.publish({ destination: `/app/conversations/${convo.id}/send`, body: JSON.stringify({ clientMsgId: id, type: 'TEXT', body: 'hello across pods 🛰️' }) });
  await sleep(900);

  const crossed = bobMsgs.find((m) => m.clientMsgId === id);
  const echo = aliceMsgs.find((m) => m.clientMsgId === id);
  console.log(`bob (pod 2) received alice (pod 1)'s message: ${crossed ? `YES  seq=${crossed.seq}` : 'NO'}`);
  console.log(`alice (pod 1) got her own echo:               ${echo ? 'YES' : 'NO'}`);

  ac.deactivate(); bc.deactivate();
  const ok = !!crossed && !!echo;
  console.log(ok ? '\n✓ cross-pod fan-out verified (Redis routed pod-1 → pod-2)' : '\n✗ message did not cross pods');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); process.exit(1); });
