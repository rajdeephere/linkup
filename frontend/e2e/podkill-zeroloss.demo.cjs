/**
 * LinkUp demo — pod-kill → zero message loss (Day 9 · scenario #5). CHAOS: it kills a container.
 *
 * bob's socket is on pod-2; alice's on pod-1. We `docker kill` pod-2 (a hard crash) while alice
 * keeps sending. bob reconnects via the gateway to the surviving pod and **resyncs via the seq
 * cursor** (Day-6) — recovering the messages sent while he was offline. Nothing is lost, because
 * durability lives in Postgres, not the socket.
 *
 * Needs the multi-pod stack up. Restores the killed pod at the end.
 */
const { execSync } = require('child_process');
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const GW = 'http://localhost:8081';
const WS_GW = 'ws://localhost:8081/ws';
const WS_POD1 = 'ws://localhost:8091/ws';
const WS_POD2 = 'ws://localhost:8092/ws';
const KILL = 'linkup-backend2'; // bob's pod

const login = async (u) => (await (await fetch(`${GW}/v1/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: u, password: 'password123', platform: 'WEB' }),
})).json());
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sh = (cmd) => execSync(cmd, { stdio: 'pipe' }).toString().trim();

function bobClient(url, token, onConnect, seqs, senderId) {
  const c = new Client({
    webSocketFactory: () => new WebSocket(url), connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 0,
    onConnect: () => {
      c.subscribe('/user/queue/messages', (f) => {
        const m = JSON.parse(f.body);
        if (m.senderId === senderId) seqs.add(m.seq);
      });
      onConnect && onConnect();
    },
  });
  c.activate();
  return c;
}

(async () => {
  const alice = await login('alice'), bob = await login('bob');
  const convo = await (await fetch(`${GW}/v1/conversations`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${alice.accessToken}` },
    body: JSON.stringify({ type: 'DIRECT', memberUserIds: [bob.userId] }),
  })).json();
  const cid = convo.id;

  // alice → pod-1 (survivor/sender), bob → pod-2 (doomed)
  const bobSeen = new Set();
  const ac = new Client({ webSocketFactory: () => new WebSocket(WS_POD1), connectHeaders: { Authorization: `Bearer ${alice.accessToken}` }, reconnectDelay: 0, onConnect: () => {} });
  ac.activate();
  await sleep(300);
  bobClient(WS_POD2, bob.accessToken, null, bobSeen, alice.userId);
  await sleep(900);

  const send = (body) => ac.publish({ destination: `/app/conversations/${cid}/send`, body: JSON.stringify({ clientMsgId: crypto.randomUUID(), type: 'TEXT', body }) });

  send('before the crash'); await sleep(700);
  const gotLiveBeforeKill = bobSeen.size >= 1;
  const cursorBeforeOutage = Math.max(0, ...bobSeen);

  // 💥 hard-kill bob's pod
  console.log(`💥 docker kill ${KILL} (holds bob's socket)…`);
  sh(`docker kill ${KILL}`);
  await sleep(1500);

  // alice keeps sending while bob is offline — these MISS his socket (no replay)
  send('during outage 1'); send('during outage 2'); send('during outage 3');
  await sleep(700);
  const gotLiveDuringOutage = [...bobSeen].filter((s) => s > cursorBeforeOutage).length;

  // bob reconnects via the GATEWAY → routed to the surviving pod (this is what the real app does)
  let reconnected = false;
  bobClient(WS_GW, bob.accessToken, () => { reconnected = true; }, bobSeen, alice.userId);
  const t0 = Date.now();
  while (!reconnected && Date.now() - t0 < 15000) await sleep(300);

  // …and RESYNCS via the seq cursor (Day 6): fetch everything after his last-known seq
  const res = await (await fetch(`${GW}/v1/conversations/${cid}/messages?after=${cursorBeforeOutage}&limit=100`, { headers: { Authorization: `Bearer ${bob.accessToken}` } })).json();
  res.messages.filter((m) => m.senderId === alice.userId).forEach((m) => bobSeen.add(m.seq));
  await sleep(400);

  console.log(`\nlive before kill:            ${gotLiveBeforeKill ? 'received ✓' : 'MISSED'}`);
  console.log(`live during outage:          ${gotLiveDuringOutage} (expected 0 — socket was dead)`);
  console.log(`bob reconnected to survivor: ${reconnected}`);
  console.log(`total of alice's msgs bob has after resync: ${bobSeen.size} (expected ≥ 4)`);

  // restore the pod
  console.log(`\n↻ docker start ${KILL} (restore)…`);
  try { sh(`docker start ${KILL}`); } catch {}

  const ok = gotLiveBeforeKill && reconnected && bobSeen.size >= 4;
  console.log(ok ? '\n✓ pod kill → reconnect → resync → ZERO message loss' : '\n✗ messages were lost');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); try { execSync(`docker start ${KILL}`); } catch {} process.exit(1); });
