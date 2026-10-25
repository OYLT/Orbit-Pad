import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { GamepadBridge } from '../server/input.js';
import { emptyReport } from '../server/controller-core.js';

const run = promisify(execFile);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
test('real Windows XInput reads analog reports, watchdog neutral and device removal', { skip: process.platform !== 'win32', timeout: 20000 }, async t => {
  const bridge = new GamepadBridge();
  t.after(() => bridge.stop());
  bridge.start();
  for (let i = 0; !bridge.ready && !bridge.error && i < 150; i++) await delay(50);
  if (!bridge.ready) { t.skip(`ViGEmBus unavailable: ${bridge.error}`); return; }
  const folder = await mkdtemp(join(tmpdir(), 'orbit-xinput-'));
  t.after(() => rm(folder, { recursive: true, force: true }));
  const probe = join(folder, 'Probe.exe');
  await run(join(process.env.WINDIR, 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'), [
    '/nologo', '/target:exe', '/platform:x64', `/out:${probe}`, fileURLToPath(new URL('./GamepadProbe.cs', import.meta.url)),
  ], { windowsHide: true });
  const read = async () => JSON.parse((await run(probe, [], { windowsHide: true })).stdout);
  const expected = { buttons: 0x9201, leftTrigger: 73, rightTrigger: 187, leftX: -12345, leftY: 23456, rightX: 3210, rightY: -21000 };
  bridge.setReport(expected);
  let index = -1;
  for (let i = 0; i < 40 && index < 0; i++) {
    index = (await read()).findIndex(r => JSON.stringify(r) === JSON.stringify(expected));
    if (index < 0) await delay(30);
  }
  assert(index >= 0, 'a Windows XInput slot must expose the exact native report');
  t.diagnostic(`Verified the virtual Xbox 360 controller at XInput slot ${index}.`);
  // Stop parent heartbeats while the child remains alive to exercise its own watchdog.
  clearInterval(bridge.keepAlive);
  await delay(2000);
  assert.deepEqual((await read())[index], emptyReport());
  bridge.setReport(expected);
  await delay(60);
  assert.deepEqual((await read())[index], expected);
  await bridge.stop();
  for (let i = 0; i < 30; i++) {
    if ((await read())[index] === null) return;
    await delay(50);
  }
  assert.fail('virtual controller must be removed after stopping the receiver');
});
