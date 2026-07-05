/**
 * LinkUp demo — direct-to-blob media upload (Day 10 · ADR-0005 · scenario #11).
 *
 * Proves the media path never streams bytes through the app tier:
 *   1. alice asks the API for a PRESIGNED upload URL (POST /v1/media/presign)
 *   2. alice PUTs the image bytes STRAIGHT to object storage (MinIO) — not through the API
 *   3. alice sends a chat message over STOMP carrying ONLY the blobKey (a tiny reference)
 *   4. bob receives it on his socket, resolves a presigned GET URL, and downloads the bytes
 *      straight from storage — and they match what alice uploaded.
 *
 * Needs the stack up (docker compose up -d --build) + seed users. Node 18+ (global fetch).
 * Env: DEMO_GW (default :8081).
 */
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const GW = process.env.DEMO_GW || 'http://localhost:8081';

// A minimal valid 1x1 PNG (70 bytes) — stands in for "an image the user picked".
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
);

const login = async (u) => (await (await fetch(`${GW}/v1/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: u, password: 'password123', platform: 'WEB' }),
})).json());
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const connect = (token, out) => new Promise((res) => {
  const c = new Client({
    webSocketFactory: () => new WebSocket(`${GW.replace('http', 'ws')}/ws`),
    connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 0,
    onConnect: () => { c.subscribe('/user/queue/messages', (f) => out.push(JSON.parse(f.body))); res(c); },
  });
  c.activate();
});
const authed = (token, extra = {}) => ({ Authorization: `Bearer ${token}`, ...extra });

(async () => {
  const alice = await login('alice'), bob = await login('bob');
  const convo = await (await fetch(`${GW}/v1/conversations`, {
    method: 'POST', headers: authed(alice.accessToken, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ type: 'DIRECT', memberUserIds: [bob.userId] }),
  })).json();

  const aliceMsgs = [], bobMsgs = [];
  const ac = await connect(alice.accessToken, aliceMsgs);
  const bc = await connect(bob.accessToken, bobMsgs);
  await sleep(600);

  // 1. presign — the API hands out a URL; the bytes are NOT in this request/response.
  const presign = await (await fetch(`${GW}/v1/media/presign`, {
    method: 'POST', headers: authed(alice.accessToken, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ filename: 'hello.png', contentType: 'image/png', sizeBytes: PNG.length }),
  })).json();
  console.log(`presigned upload URL issued → blobKey=${presign.blobKey}`);
  const bytesThroughApi = /\/v1\//.test(new URL(presign.uploadUrl).pathname);
  console.log(`upload URL points at storage (not the API):     ${bytesThroughApi ? 'NO' : 'YES'}`);

  // 2. PUT bytes DIRECTLY to storage.
  const put = await fetch(presign.uploadUrl, {
    method: 'PUT', headers: { 'Content-Type': presign.contentType }, body: PNG,
  });
  console.log(`PUT bytes straight to MinIO:                     ${put.ok ? `YES (${put.status})` : `NO (${put.status})`}`);

  // 3. send a chat message carrying ONLY the reference.
  const id = crypto.randomUUID();
  ac.publish({
    destination: `/app/conversations/${convo.id}/send`,
    body: JSON.stringify({
      clientMsgId: id, type: 'IMAGE',
      attachment: { blobKey: presign.blobKey, mimeType: 'image/png', sizeBytes: PNG.length, width: 1, height: 1 },
    }),
  });
  await sleep(900);

  // 4. bob receives the reference and downloads the bytes from storage via a presigned GET.
  const got = bobMsgs.find((m) => m.clientMsgId === id);
  console.log(`bob received the IMAGE message:                  ${got ? `YES seq=${got.seq}` : 'NO'}`);
  console.log(`message carried a reference, not bytes:          ${got?.attachment?.blobKey && !got.body ? 'YES' : 'NO'}`);

  let downloaded = null;
  if (got?.attachment?.blobKey) {
    const dl = await (await fetch(
      `${GW}/v1/media/download-url?key=${encodeURIComponent(got.attachment.blobKey)}`,
      { headers: authed(bob.accessToken) },
    )).json();
    const bytes = Buffer.from(await (await fetch(dl.url)).arrayBuffer());
    downloaded = bytes;
    console.log(`bob downloaded ${bytes.length}B from storage (sent ${PNG.length}B): ${bytes.equals(PNG) ? 'MATCH' : 'MISMATCH'}`);
  }

  ac.deactivate(); bc.deactivate();
  const ok = put.ok && !bytesThroughApi && !!got && !got.body && downloaded && downloaded.equals(PNG);
  console.log(ok
    ? '\n✓ direct-to-blob verified — bytes bypassed the API; the chat path carried only a reference'
    : '\n✗ media path did not behave as expected');
  process.exit(ok ? 0 : 1);
})().catch((e) => { console.error('DEMO ERROR:', e.message); process.exit(1); });
