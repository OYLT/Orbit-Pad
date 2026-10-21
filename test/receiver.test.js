import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { WebSocket } from 'ws';
import { createReceiver } from '../server/receiver.js';
import { GamepadBridge } from '../server/input.js';
import { DEFAULT_MAPPING, normalizeState, stateToGamepad, validMapping, emptyReport, BUTTON_MASKS } from '../public/controller-core.js';

async function setup(t) {
  const input = new GamepadBridge({ dryRun: true });
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

test('missing native components produce an actionable error and no keyboard fallback', { skip: process.platform !== 'win32' }, () => {
  const input = new GamepadBridge({ executable: 'C:\\orbit-pad-nonexistent-fixture\\OrbitPadGamepad.exe' });
  input.start();
  assert.equal(input.mode, 'xinput');
  assert.equal(input.ready, false);
  assert.equal(input.child, undefined);
  assert.match(input.error, /npm run setup:gamepad/);
});

test('native reports preserve analog sticks, triggers and button masks', () => {
  const report = stateToGamepad(normalizeState({ buttons: ['a', 'lb', 'rt'], axes: [0.5, -1, -1, 0] }));
  assert.equal(report.buttons, BUTTON_MASKS.a | BUTTON_MASKS.lb);
  assert.equal(report.rightTrigger, 255);
  assert(report.leftX > 0 && report.leftX < 32767);
  assert(report.leftY > 0);
  assert.equal(report.rightX, -32768);
  assert.equal(report.rightY, 0);
  assert.deepEqual(stateToGamepad({ buttons: [], axes: [0.1, -0.1, 0, 0] }), emptyReport());
  const half = stateToGamepad({ buttons: [], axes: [0.62, 0, 0, 0], triggers: [0.5, 0.25] });
  assert.equal(half.leftX, 16384);
  assert.equal(half.leftTrigger, 128);
  assert.equal(half.rightTrigger, 64);
  assert.deepEqual(stateToGamepad({ buttons: ['up', 'down', 'left', 'right'], axes: [0, 0, 0, 0] }), emptyReport());
  assert(validMapping(DEFAULT_MAPPING));
  assert(!validMapping({ ...DEFAULT_MAPPING, a: '__proto__' }));
  assert(!validMapping({ ...DEFAULT_MAPPING, system: 'a' }));
  for (const value of [
    { buttons: ['unknown'], axes: [0, 0, 0, 0] },
    { buttons: [], axes: [NaN, 0, 0, 0] },
    { buttons: [], axes: [2, 0, 0, 0] },
    { buttons: [], axes: [0, 0, 0, 0], triggers: [1.1, 0] },
    { buttons: [], axes: [0, 0, 0, 0], triggers: [0] },
  ]) assert.throws(() => normalizeState(value));
});

test('native mapping combines targets, disables controls and maps axes continuously', () => {
  const mapping = { ...DEFAULT_MAPPING, a: 'b', b: 'b', x: 'none', moveRight: 'rt' };
  const report = stateToGamepad({ buttons: ['a', 'b', 'x'], axes: [0.62, 0, 0, 0] }, mapping);
  assert.equal(report.buttons, BUTTON_MASKS.b);
  assert.equal(report.rightTrigger, 128);
  assert.equal(report.leftX, 0);
});

test('pairs a phone, combines controls, remaps, and releases on disconnect', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  ws.send(state(['a'], [-1, -1, 0, 0]));
  await waitFor(() => input.report.buttons === BUTTON_MASKS.a && input.report.leftX < 0);
  assert(input.report.leftY > 0);
  ws.send(JSON.stringify({ type: 'mapping', mapping: { ...DEFAULT_MAPPING, a: 'x' } }));
  await waitFor(() => Object.values(input.report).every(v => v === 0));
  ws.send(state(['a']));
  await waitFor(() => input.report.buttons === BUTTON_MASKS.x);
  ws.close();
  await waitFor(() => Object.values(input.report).every(v => v === 0));
});

test('pausing releases all keys and rejects input until resumed', async t => {
  const { input, pair, connect, post } = await setup(t);
  const ws = await connect(await pair());
  ws.send(state(['rt']));
  await waitFor(() => input.report.rightTrigger === 255);
  assert.equal((await post('/api/host/pause', { paused: true })).status, 200);
  assert.deepEqual(input.report, emptyReport());
  ws.send(state(['a']));
  ws.send(JSON.stringify({ type: 'ping', sent: 1 }));
  await once(ws, 'message');
  assert.deepEqual(input.report, emptyReport());
  await post('/api/host/pause', { paused: false });
  ws.send(state(['b']));
  await waitFor(() => input.report.buttons === BUTTON_MASKS.b);
});

test('invalid state closes socket and releases held keys', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  ws.send(state(['a']));
  await waitFor(() => input.report.buttons === BUTTON_MASKS.a);
  const closed = once(ws, 'close');
  ws.send(state(['a'], [4, 0, 0, 0]));
  const [code] = await closed;
  assert.equal(code, 4002);
  assert.deepEqual(input.report, emptyReport());
});

test('stalled client is disconnected and keys are released', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  const closed = once(ws, 'close');
  ws.send(state(['a']));
  await waitFor(() => input.report.buttons === BUTTON_MASKS.a);
  const [code] = await closed;
  assert.equal(code, 4000);
  assert.deepEqual(input.report, emptyReport());
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
  await waitFor(() => input.report.buttons === BUTTON_MASKS.a);
  const closed = once(first, 'close');
  await post('/api/host/reset', {});
  await closed;
  assert.deepEqual(input.report, emptyReport());
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

test('Windows native bridge reports missing components or driver without keyboard fallback', { skip: process.platform !== 'win32' }, async t => {
  const input = new GamepadBridge();
  input.start();
  try {
    await waitFor(() => input.ready || input.error);
    assert.equal(input.mode, 'xinput');
    assert.deepEqual(input.report, emptyReport());
    if (input.ready) {
      assert.equal(input.error, '');
      t.diagnostic('ViGEmBus is available; virtual Xbox 360 device created.');
    } else {
      assert.match(input.error, /ViGEmBus|组件缺失/);
      t.diagnostic('Native driver is unavailable; verified explicit failure with no keyboard fallback.');
    }
  } finally { await input.stop(); }
});

test('legacy keyboard mapping is migrated to native defaults for old phone clients', async t => {
  const { input, pair, connect } = await setup(t);
  const ws = await connect(await pair());
  ws.send(JSON.stringify({ type: 'mapping', mapping: Object.fromEntries(Object.keys(DEFAULT_MAPPING).map(k => [k, 'Space'])) }));
  ws.send(state(['a', 'rt'], [0.62, 0, 0, 0]));
  await waitFor(() => input.report.buttons === BUTTON_MASKS.a);
  assert.equal(input.report.leftX, 16384);
  assert.equal(input.report.rightTrigger, 255);
  ws.close();
});

test('explicit analog triggers override legacy digital trigger buttons', async t => {
  const { input, pair, connect, get } = await setup(t);
  const ws = await connect(await pair());
  ws.send(JSON.stringify({ type: 'state', buttons: ['lt', 'rt'], axes: [0, 0, 0, 0], triggers: [0, 0.5] }));
  await waitFor(() => input.report.rightTrigger === 128);
  assert.equal(input.report.leftTrigger, 0);
  const host = await (await get('/api/host')).json();
  assert.deepEqual(host.report, input.report);
  assert.deepEqual(host.keys, []);
  assert.deepEqual(host.inputs, ['RT 50%']);
  ws.close();
});

test('desktop-only shutdown requires the launcher capability', async t => {
  const input = new GamepadBridge({ dryRun: true });
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
