import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { once } from 'node:events';
import net from 'node:net';
import { WebSocket } from 'ws';
import { createReceiver } from '../server/receiver.js';
import { KeyboardBridge } from '../server/input.js';
import { DEFAULT_MAPPING, PRESETS } from '../public/controller-core.js';

const input = new KeyboardBridge({ dryRun: true });
const history = [];
const setKeys = input.setKeys.bind(input);
input.setKeys = keys => { history.push([...keys].sort()); setKeys(keys); };
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
async function keys(expected, name) {
  await wait(() => JSON.stringify([...input.keys].sort()) === JSON.stringify([...expected].sort()), name);
  phases.push({ name, keys: [...input.keys].sort(), passed: true }); console.log(`PASS ${name}`);
}
const touch = (id, phase, control, x = 0, y = 0) => call('touch', { id, phase, control, x, y });
const post = (path, data) => fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
let other;
try {
  await call('connect', { address, code: '000000' });
  await wait(async () => (await call('snapshot')).message.includes('配对码不正确'), 'invalid pair code');
  await keys([], 'invalid pair code rejected');
  const connect = async () => { await call('connect', { address, code: host.pairCode }); await wait(async () => (await call('snapshot')).enabled, 'connect'); };
  await connect(); await keys([], 'Rust client authenticates');
  await wait(async () => (await call('snapshot')).rtt !== null, 'heartbeat RTT');
  for (const [button, key] of Object.entries(DEFAULT_MAPPING).slice(0, 14)) {
    await touch(0, 'down', button); await keys([key], `${button} down`);
    await touch(0, 'up', button); await keys([], `${button} released`);
  }
  await touch(0, 'down', 'move', .8, -.8); await touch(1, 'down', 'look', -.8, -.8); await touch(2, 'down', 'a');
  await keys(['D', 'W', 'ArrowLeft', 'ArrowUp', 'Space'], 'three fingers: dual sticks and A');
  await touch(2, 'up', 'a'); await keys(['D', 'W', 'ArrowLeft', 'ArrowUp'], 'button releases independently');
  await touch(1, 'up', 'look'); await keys(['D', 'W'], 'right stick releases independently');
  await touch(0, 'up', 'move'); await keys([], 'all fingers released');
  await touch(0, 'down', 'a'); await touch(1, 'down', 'a'); await touch(0, 'up', 'a'); await keys(['Space'], 'two fingers share button');
  await touch(1, 'up', 'a'); await keys([], 'last finger releases button');
  await touch(0, 'down', 'a'); await keys(['Space'], 'hold before focus loss');
  await call('focus', { value: false }); await keys([], 'focus loss releases');
  await touch(0, 'down', 'a'); await delay(50); await keys([], 'unfocused touches ignored'); await call('focus', { value: true });
  await touch(0, 'down', 'a'); await keys(['Space'], 'hold before settings'); await call('settings', { open: true }); await keys([], 'settings releases');
  await touch(0, 'down', 'a'); await delay(50); await keys([], 'settings blocks touches'); await call('settings', { open: false });
  for (const [name, preset] of Object.entries(PRESETS)) {
    const settings = await call('preset', { name }); assert.deepEqual(settings.mapping, preset.mapping);
    await call('configure', { settings }); await touch(0, 'down', 'b'); await keys([preset.mapping.b], `preset ${name}`); await call('release'); await keys([], `preset ${name} releases`);
  }
  const settings = await call('preset', { name: 'general' }); settings.mapping.a = 'Enter'; settings.preset = 'custom'; settings.deadzone = .5;
  await call('configure', { settings }); await touch(0, 'down', 'a'); await keys(['Enter'], 'custom mapping'); await call('release'); await keys([], 'custom mapping releases');
  await touch(0, 'down', 'move', .4, 0); await delay(50); await keys([], 'deadzone ignores small movement');
  await touch(0, 'move', 'move', .8, 0); await keys(['D'], 'deadzone accepts large movement');
  await post('/api/host/pause', { paused: true }); await wait(async () => (await call('snapshot')).paused, 'host pause'); await keys([], 'host pause releases');
  await post('/api/host/pause', { paused: false }); await wait(async () => (await call('snapshot')).enabled, 'host resume'); await delay(60); await keys([], 'host resume never replays held keys');
  await touch(0, 'down', 'a'); await keys(['Enter'], 'hold before background');
  await call('pause'); await wait(() => !input.keys.size, 'background release'); await keys([], 'background releases');
  await wait(async () => !(await (await fetch(base + '/api/host')).json()).connected, 'background disconnect');
  await touch(0, 'down', 'a'); await call('resume'); await wait(async () => (await call('snapshot')).enabled, 'foreground reconnect'); await delay(60); await keys([], 'foreground reconnect starts neutral');
  input.ready = false; input.emit('status'); await wait(async () => !(await call('snapshot')).ready, 'bridge unavailable'); await touch(0, 'down', 'a'); await delay(40); await keys([], 'unavailable bridge blocks input');
  input.ready = true; input.emit('status'); await wait(async () => (await call('snapshot')).enabled, 'bridge restored');
  // Motion bursts are coalesced but quick button edges survive.
  await touch(0, 'down', 'move');
  for (let i = 0; i < 400; i++) await touch(0, 'move', 'move', i % 2 ? .8 : -.8, 0);
  await call('release'); await delay(120); assert((await call('snapshot')).connected); await keys([], 'motion burst stays below receiver limit');
  const mark = history.length; await touch(0, 'down', 'a'); await touch(0, 'up', 'a');
  await wait(() => history.slice(mark).some(keys => keys.includes('Enter')), 'quick tap down survives');
  await keys([], 'quick tap up survives');
  await touch(0, 'down', 'a'); await keys(['Enter'], 'hold before network drop');
  for (const pair of connections) pair.client.destroy();
  await wait(async () => !(await call('snapshot')).connected, 'network drop detected'); await keys([], 'network drop releases');
  await wait(async () => (await call('snapshot')).enabled, 'network reconnect'); await delay(60); await keys([], 'network reconnect starts neutral');
  await touch(0, 'down', 'a'); await keys(['Enter'], 'hold before silent connection');
  for (const pair of connections) { pair.frozen = true; pair.client.unpipe(pair.upstream); pair.upstream.unpipe(pair.client); }
  await wait(async () => !(await call('snapshot')).connected, 'silent connection timeout'); await keys([], 'silent connection releases');
  await wait(async () => (await call('snapshot')).enabled, 'reconnect after timeout'); await delay(60); await keys([], 'timeout reconnect starts neutral');
  await call('disconnect'); await wait(async () => !(await (await fetch(base + '/api/host')).json()).connected, 'disconnect'); await keys([], 'manual disconnect');
  const token = await (await post('/api/pair', { code: host.pairCode })).json();
  other = new WebSocket(`ws://127.0.0.1:${port}/ws`, { origin: base }); await once(other, 'open');
  const auth = once(other, 'message'); other.send(JSON.stringify({ type: 'auth', token: token.token })); await auth;
  await call('connect', { address, code: host.pairCode });
  await wait(async () => (await call('snapshot')).message.includes('已有手柄连接'), 'busy response'); await keys([], 'busy receiver is terminal');
  other.close(); await once(other, 'close'); other = null; await delay(50);
  await connect(); await touch(0, 'down', 'a'); await keys(['Enter'], 'hold before revoke'); await post('/api/host/reset', {});
  await wait(async () => !(await call('snapshot')).reconnectable, 'pair revoked'); await keys([], 'revocation releases and requires pairing');
  for (const method of ['usb', 'bluetooth']) {
    await call('connect', { address, code: '', method }); await wait(async () => (await call('snapshot')).enabled, `${method} bootstrap`);
    await keys([], `${method} automatic bootstrap`); await touch(0, 'down', 'a'); await keys(['Enter'], `${method} core input`);
    await call('disconnect'); await keys([], `${method} core release`); await delay(50);
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
