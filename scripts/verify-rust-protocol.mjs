import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { once } from 'node:events';
import net from 'node:net';
import { WebSocket } from 'ws';
import { createReceiver } from '../server/receiver.js';
import { GamepadBridge } from '../server/input.js';
import { DEFAULT_MAPPING, PRESETS, BUTTON_MASKS } from '../server/controller-core.js';

const input = new GamepadBridge({ dryRun: true });
const history = [];
const activeControls = r => [
  ...Object.entries(BUTTON_MASKS).filter(([, bit]) => r.buttons & bit).map(([id]) => id),
  ...(r.leftTrigger ? ['lt'] : []), ...(r.rightTrigger ? ['rt'] : []),
  ...(r.leftX ? [r.leftX > 0 ? 'moveRight' : 'moveLeft'] : []),
  ...(r.leftY ? [r.leftY > 0 ? 'moveUp' : 'moveDown'] : []),
  ...(r.rightX ? [r.rightX > 0 ? 'lookRight' : 'lookLeft'] : []),
  ...(r.rightY ? [r.rightY > 0 ? 'lookUp' : 'lookDown'] : []),
].sort();
const setReport = input.setReport.bind(input);
input.setReport = report => { history.push(activeControls(report)); setReport(report); };
const receiver = createReceiver({ input, port: 0, host: '127.0.0.1' });
const { port } = await receiver.start();
const base = `http://127.0.0.1:${port}`;
// A local TCP proxy lets the test sever or blackhole a live connection without
// changing the production receiver or its protocol.
const connections = new Set();
const proxy = net.createServer(client => {
  const upstream = net.connect(port, '127.0.0.1');
  const pair = { client, upstream, frozen: false }; connections.add(pair);
  client.pipe(upstream); upstream.pipe(client);
  client.on('error', () => upstream.destroy());
  upstream.on('error', () => { if (!pair.frozen) client.destroy(); });
  upstream.on('close', () => { if (!pair.frozen) client.destroy(); });
  client.on('close', () => { upstream.destroy(); connections.delete(pair); });
});
proxy.listen(0, '127.0.0.1'); await once(proxy, 'listening');
const address = `127.0.0.1:${proxy.address().port}`;
let host = await (await fetch(`${base}/api/host`)).json();
const driver = spawn(fileURLToPath(new URL('../android/rust/target/debug/examples/core-driver.exe', import.meta.url)), [], { windowsHide: true, stdio: ['pipe', 'pipe', 'inherit'] });
const queue = [];
const lines = createInterface({ input: driver.stdout });
lines.on('line', line => { const request = queue.shift(); if (request) request.resolve(JSON.parse(line)); });
driver.on('error', error => { for (const p of queue.splice(0)) p.reject(error); });
driver.on('exit', code => { for (const p of queue.splice(0)) p.reject(new Error(`Rust core exited: ${code}`)); });
const call = (op, data = {}) => new Promise((resolve, reject) => { queue.push({ resolve, reject }); driver.stdin.write(JSON.stringify({ op, ...data }) + '\n'); });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const phases = [];
async function wait(predicate, description) {
  const deadline = Date.now() + 7000;
  while (!await predicate()) { if (Date.now() > deadline) throw new Error(`Timed out: ${description}; ${JSON.stringify(await call('snapshot'))}`); await delay(15); }
}
async function controls(expected, name) {
  await wait(() => JSON.stringify(activeControls(input.report)) === JSON.stringify([...expected].sort()), name);
  phases.push({ name, report: { ...input.report }, passed: true }); console.log(`PASS ${name}`);
}
const touch = (id, phase, control, x = 0, y = 0) => call('touch', { id, phase, control, x, y });
const post = (path, data) => fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
let other;
try {
  await call('connect', { address, code: '000000' });
  await wait(async () => (await call('snapshot')).message.includes('配对码不正确'), 'invalid pair code');
  await controls([], 'invalid pair code rejected');
  const connect = async () => { await call('connect', { address, code: host.pairCode }); await wait(async () => (await call('snapshot')).enabled, 'connect'); };
  await connect(); await controls([], 'Rust client authenticates');
  const pedals = await call('preset', { name: 'general' }); pedals.linear_triggers = true; await call('configure', { settings: pedals });
  await touch(0, 'down', 'lt', -1, 0); await touch(0, 'move', 'lt', 0, 0);
  await wait(() => input.report.leftTrigger === 128, 'analog trigger travel reaches receiver');
  phases.push({ name: 'Rust analog trigger travel', report: { ...input.report }, passed: true });
  await call('release'); await controls([], 'analog trigger released');
  await wait(async () => (await call('snapshot')).rtt !== null, 'heartbeat RTT');
  for (const [button, key] of Object.entries(DEFAULT_MAPPING).filter(([key]) => !key.startsWith('move') && !key.startsWith('look'))) {
    await touch(0, 'down', button); await controls([key], `${button} down`);
    await touch(0, 'up', button); await controls([], `${button} released`);
  }
  await touch(0, 'down', 'move', .8, -.8); await touch(1, 'down', 'look', -.8, -.8); await touch(2, 'down', 'a');
  await controls(['moveRight', 'moveUp', 'lookLeft', 'lookUp', 'a'], 'three fingers: dual sticks and A');
  await touch(2, 'up', 'a'); await controls(['moveRight', 'moveUp', 'lookLeft', 'lookUp'], 'button releases independently');
  await touch(1, 'up', 'look'); await controls(['moveRight', 'moveUp'], 'right stick releases independently');
  await touch(0, 'up', 'move'); await controls([], 'all fingers released');
  await touch(0, 'down', 'a'); await touch(1, 'down', 'a'); await touch(0, 'up', 'a'); await controls(['a'], 'two fingers share button');
  await touch(1, 'up', 'a'); await controls([], 'last finger releases button');
  await touch(0, 'down', 'a'); await controls(['a'], 'hold before focus loss');
  await call('focus', { value: false }); await controls([], 'focus loss releases');
  await touch(0, 'down', 'a'); await delay(50); await controls([], 'unfocused touches ignored'); await call('focus', { value: true });
  await touch(0, 'down', 'a'); await controls(['a'], 'hold before settings'); await call('settings', { open: true }); await controls([], 'settings releases');
  await touch(0, 'down', 'a'); await delay(50); await controls([], 'settings blocks touches'); await call('settings', { open: false });
  for (const [name, preset] of Object.entries(PRESETS)) {
    const settings = await call('preset', { name }); assert.deepEqual(settings.mapping, preset.mapping);
    await call('configure', { settings }); await touch(0, 'down', 'b'); await controls([preset.mapping.b], `preset ${name}`); await call('release'); await controls([], `preset ${name} releases`);
  }
  const settings = await call('preset', { name: 'general' }); settings.mapping.a = 'x'; settings.preset = 'custom'; settings.deadzone = .5;
  await call('configure', { settings }); await touch(0, 'down', 'a'); await controls(['x'], 'custom mapping'); await call('release'); await controls([], 'custom mapping releases');
  await touch(0, 'down', 'move', .4, 0); await delay(50); await controls([], 'deadzone ignores small movement');
  await touch(0, 'move', 'move', .8, 0); await controls(['moveRight'], 'deadzone accepts large movement');
  await post('/api/host/pause', { paused: true }); await wait(async () => (await call('snapshot')).paused, 'host pause'); await controls([], 'host pause releases');
  await post('/api/host/pause', { paused: false }); await wait(async () => (await call('snapshot')).enabled, 'host resume'); await delay(60); await controls([], 'host resume never replays held keys');
  await touch(0, 'down', 'a'); await controls(['x'], 'hold before background');
  await call('pause'); await wait(() => Object.values(input.report).every(v => v === 0), 'background release'); await controls([], 'background releases');
  await wait(async () => !(await (await fetch(base + '/api/host')).json()).connected, 'background disconnect');
  await touch(0, 'down', 'a'); await call('resume'); await wait(async () => (await call('snapshot')).enabled, 'foreground reconnect'); await delay(60); await controls([], 'foreground reconnect starts neutral');
  input.ready = false; input.emit('status'); await wait(async () => !(await call('snapshot')).ready, 'bridge unavailable'); await touch(0, 'down', 'a'); await delay(40); await controls([], 'unavailable bridge blocks input');
  input.ready = true; input.emit('status'); await wait(async () => (await call('snapshot')).enabled, 'bridge restored');
  // Motion bursts are coalesced but quick button edges survive.
  await touch(0, 'down', 'move');
  for (let i = 0; i < 400; i++) await touch(0, 'move', 'move', i % 2 ? .8 : -.8, 0);
  await call('release'); await delay(120); assert((await call('snapshot')).connected); await controls([], 'motion burst stays below receiver limit');
  const mark = history.length; await touch(0, 'down', 'a'); await touch(0, 'up', 'a');
  await wait(() => history.slice(mark).some(keys => keys.includes('x')), 'quick tap down survives');
  await controls([], 'quick tap up survives');
  await touch(0, 'down', 'a'); await controls(['x'], 'hold before network drop');
  for (const pair of connections) pair.client.destroy();
  await wait(async () => !(await call('snapshot')).connected, 'network drop detected'); await controls([], 'network drop releases');
  await wait(async () => (await call('snapshot')).enabled, 'network reconnect'); await delay(60); await controls([], 'network reconnect starts neutral');
  await touch(0, 'down', 'a'); await controls(['x'], 'hold before silent connection');
  for (const pair of connections) { pair.frozen = true; pair.client.unpipe(pair.upstream); pair.upstream.unpipe(pair.client); }
  await wait(async () => !(await call('snapshot')).connected, 'silent connection timeout'); await controls([], 'silent connection releases');
  await wait(async () => (await call('snapshot')).enabled, 'reconnect after timeout'); await delay(60); await controls([], 'timeout reconnect starts neutral');
  await call('disconnect'); await wait(async () => !(await (await fetch(base + '/api/host')).json()).connected, 'disconnect'); await controls([], 'manual disconnect');
  const token = await (await post('/api/pair', { code: host.pairCode })).json();
  other = new WebSocket(`ws://127.0.0.1:${port}/ws`, { origin: base }); await once(other, 'open');
  const auth = once(other, 'message'); other.send(JSON.stringify({ type: 'auth', token: token.token })); await auth;
  await call('connect', { address, code: host.pairCode });
  await wait(async () => (await call('snapshot')).message.includes('已有手柄连接'), 'busy response'); await controls([], 'busy receiver is terminal');
  other.close(); await once(other, 'close'); other = null; await delay(50);
  await connect(); await touch(0, 'down', 'a'); await controls(['x'], 'hold before revoke'); await post('/api/host/reset', {});
  await wait(async () => !(await call('snapshot')).reconnectable, 'pair revoked'); await controls([], 'revocation releases and requires pairing');
  for (const method of ['usb', 'bluetooth']) {
    await call('connect', { address, code: '', method }); await wait(async () => (await call('snapshot')).enabled, `${method} bootstrap`);
    await controls([], `${method} automatic bootstrap`); await touch(0, 'down', 'a'); await controls(['x'], `${method} core input`);
    await call('disconnect'); await controls([], `${method} core release`); await delay(50);
  }
  const version = (await call('snapshot')).version;
  const reportDirectory = new URL(`../artifacts/android-rust-v${version}/`, import.meta.url);
  await mkdir(reportDirectory, { recursive: true });
  await writeFile(new URL('protocol-report.json', reportDirectory), JSON.stringify({ ok: true, version, phases }, null, 2));
  console.log(`${phases.length} Rust/receiver checks passed`);
} finally {
  other?.terminate(); driver.kill(); lines.close();
  for (const pair of connections) { pair.client.destroy(); pair.upstream.destroy(); }
  proxy.close(); await receiver.stop();
}
