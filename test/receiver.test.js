import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { WebSocket } from 'ws';
import { createReceiver } from '../server/receiver.js';
import { KeyboardBridge } from '../server/input.js';
import { DEFAULT_MAPPING, normalizeState, stateToKeys, validMapping } from '../public/controller-core.js';

async function setup(t) {
  const input = new KeyboardBridge({ dryRun: true });
  const receiver = createReceiver({ input, port: 0, host: '127.0.0.1' });
  const { port } = await receiver.start();
  const base = `http://127.0.0.1:${port}`;
  t.after(() => receiver.stop());
  const get = path => fetch(`${base}${path}`);
  const post = (path, data) => fetch(`${base}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: base }, body: JSON.stringify(data) });
  const host = await (await get('/api/host')).json();
  const pair = async () => (await (await post('/api/pair', { code: host.pairCode, device: 'Test Phone' })).json()).token;
  async function connect(token) {
    const ws = new WebSocket(`ws://127.0.0.1:${port}/ws`, { origin: base });
    await once(ws, 'open');
    const response = once(ws, 'message');
    ws.send(JSON.stringify({ type: 'auth', token }));
    const [raw] = await response;
    assert.equal(JSON.parse(raw).type, 'authenticated');
    return ws;
  }
  return { input, receiver, base, get, post, host, pair, connect };
}
const waitFor = async predicate => {
  const start = Date.now();
  while (!predicate()) {
    if (Date.now() - start > 3000) throw new Error('condition timed out');
    await new Promise(resolve => setTimeout(resolve, 15));
  }
};
const state = (buttons = [], axes = [0, 0, 0, 0]) => JSON.stringify({ type: 'state', buttons, axes });

test('joystick deadzone, diagonal movement, and duplicate key mappings', () => {
  assert.deepEqual([...stateToKeys({ buttons: ['a', 'b'], axes: [0.8, -0.8, 0, 0] })].sort(), ['D', 'E', 'Space', 'W']);
  assert.equal(stateToKeys({ buttons: [], axes: [0.2, -0.2, 0, 0] }).size, 0);
  assert.deepEqual([...stateToKeys({ buttons: ['up'], axes: [0, 0, 0, -1] })], ['ArrowUp']);
  assert(validMapping(DEFAULT_MAPPING));
  assert(!validMapping({ ...DEFAULT_MAPPING, a: '__proto__' }));
  assert(!validMapping({ ...DEFAULT_MAPPING, system: 'Escape' }));
  assert.throws(() => normalizeState({ buttons: ['unknown'], axes: [0, 0, 0, 0] }));
  assert.throws(() => normalizeState({ buttons: [], axes: [NaN, 0, 0, 0] }));
  assert.throws(() => normalizeState({ buttons: [], axes: [2, 0, 0, 0] }));
});

test('pairs a phone, combines controls, remaps, and releases on disconnect', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  ws.send(state(['a'], [-1, -1, 0, 0]));
  await waitFor(() => input.keys.size === 3);
  assert.deepEqual([...input.keys].sort(), ['A', 'Space', 'W']);
  ws.send(JSON.stringify({ type: 'mapping', mapping: { ...DEFAULT_MAPPING, a: 'Z' } }));
  await waitFor(() => input.keys.size === 0);
  ws.send(state(['a']));
  await waitFor(() => input.keys.has('Z'));
  ws.close();
  await waitFor(() => input.keys.size === 0);
});

test('pausing releases all keys and rejects input until resumed', async t => {
  const { input, pair, connect, post } = await setup(t);
  const ws = await connect(await pair());
  ws.send(state(['rt']));
  await waitFor(() => input.keys.has('Shift'));
  assert.equal((await post('/api/host/pause', { paused: true })).status, 200);
  assert.equal(input.keys.size, 0);
  ws.send(state(['a']));
  ws.send(JSON.stringify({ type: 'ping', sent: 1 }));
  await once(ws, 'message');
  assert.equal(input.keys.size, 0);
  await post('/api/host/pause', { paused: false });
  ws.send(state(['b']));
  await waitFor(() => input.keys.has('E'));
});

test('invalid state closes socket and releases held keys', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  ws.send(state(['a']));
  await waitFor(() => input.keys.size === 1);
  const closed = once(ws, 'close');
  ws.send(state(['a'], [4, 0, 0, 0]));
  const [code] = await closed;
  assert.equal(code, 4002);
  assert.equal(input.keys.size, 0);
});

test('stalled client is disconnected and keys are released', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  const closed = once(ws, 'close');
  ws.send(state(['a']));
  await waitFor(() => input.keys.has('Space'));
  const [code] = await closed;
  assert.equal(code, 4000);
  assert.equal(input.keys.size, 0);
});

test('reset revokes the paired token and a second phone cannot take over', async t => {
  const { input, pair, connect, post, base } = await setup(t);
  const token = await pair();
  const first = await connect(token);
  const second = new WebSocket(`${base.replace('http:', 'ws:')}/ws`, { origin: base });
  await once(second, 'open');
  const busy = once(second, 'message');
  second.send(JSON.stringify({ type: 'auth', token }));
  assert.equal(JSON.parse((await busy)[0]).type, 'busy');
  first.send(state(['a']));
  await waitFor(() => input.keys.size === 1);
  const closed = once(first, 'close');
  await post('/api/host/reset', {});
  await closed;
  assert.equal(input.keys.size, 0);
  const revoked = new WebSocket(`${base.replace('http:', 'ws:')}/ws`, { origin: base });
  await once(revoked, 'open');
  const rejected = once(revoked, 'close');
  revoked.send(JSON.stringify({ type: 'auth', token }));
  assert.equal((await rejected)[0], 4001);
});

test('wrong pairing codes, cross-site requests, and excess attempts are rejected', async t => {
  const { get, post, base } = await setup(t);
  assert.equal((await get('/controller')).status, 200);
  const status = await (await get('/api/status')).json();
  assert.equal(status.pairCode, undefined);
  const external = await fetch(`${base}/api/host/pause`, { method: 'POST', headers: { Origin: 'http://attacker.example', 'Content-Type': 'application/json' }, body: '{"paused":true}' });
  assert.equal(external.status, 403);
  for (let i = 0; i < 12; i++) assert.equal((await post('/api/pair', { code: '000000' })).status, 401);
  assert.equal((await post('/api/pair', { code: '000000' })).status, 429);
});

test('Windows native receiver compiles and shuts down without sending keys', { skip: process.platform !== 'win32' }, async () => {
  const input = new KeyboardBridge();
  input.start();
  try {
    const start = Date.now();
    while (!input.ready && !input.error && Date.now() - start < 15000) await new Promise(resolve => setTimeout(resolve, 50));
    assert.equal(input.error, '');
    assert.equal(input.ready, true);
    assert.equal(input.keys.size, 0);
  } finally { await input.stop(); }
});

test('desktop-only shutdown requires the launcher capability', async t => {
  const input = new KeyboardBridge({ dryRun: true });
  let shutdownRequested = false;
  const receiver = createReceiver({ input, port: 0, host: '127.0.0.1', ownerToken: 'private-launcher-secret', onShutdown: () => { shutdownRequested = true; } });
  const { port } = await receiver.start();
  t.after(() => receiver.stop());
  const url = `http://127.0.0.1:${port}/api/host/shutdown`;
  assert.equal((await fetch(url, { method: 'POST' })).status, 403);
  assert.equal((await fetch(url, { method: 'POST', headers: { 'X-Orbit-Owner': 'wrong' } })).status, 403);
  assert.equal(shutdownRequested, false);
  assert.equal((await fetch(url, { method: 'POST', headers: { 'X-Orbit-Owner': 'private-launcher-secret' } })).status, 202);
  await waitFor(() => shutdownRequested);
});

test('native receiver QR endpoint returns a valid PNG image', async t => {
  const { get, host } = await setup(t);
  if (!host.urls.length) return;
  const response = await get('/api/host/qr?format=png');
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('content-type'), 'image/png');
  const png = Buffer.from(await response.arrayBuffer());
  assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  assert.equal(png.readUInt32BE(16), 360);
  assert.equal(png.readUInt32BE(20), 360);
});
