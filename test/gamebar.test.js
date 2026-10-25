import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { once } from 'node:events';
import { GamepadBridge } from '../server/input.js';
import { emptyReport, BUTTON_MASKS } from '../server/controller-core.js';

const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
test('Xbox emits one balanced Win+G chord per press and ignores held heartbeats', { skip:process.platform!=='win32', timeout:15000 }, async t=>{
  const bridge=new GamepadBridge();t.after(()=>bridge.stop());bridge.start();
  for(let i=0;!bridge.ready&&!bridge.error&&i<150;i++)await delay(30);
  if(!bridge.ready){t.skip(bridge.error);return;}
  const folder=await mkdtemp(join(tmpdir(),'orbit-gamebar-'));t.after(()=>rm(folder,{recursive:true,force:true}));
  const executable=join(folder,'Probe.exe');
  await promisify(execFile)(join(process.env.WINDIR,'Microsoft.NET/Framework64/v4.0.30319/csc.exe'),[
    '/nologo','/target:exe','/platform:x64','/reference:System.Windows.Forms.dll',`/out:${executable}`,
    fileURLToPath(new URL('./GameBarProbe.cs',import.meta.url)),
  ],{windowsHide:true});
  const probe=spawn(executable,[],{windowsHide:true});t.after(()=>{if(probe.exitCode===null)probe.kill();});
  let output='';const ready=once(probe.stdout,'data');probe.stdout.on('data',chunk=>output+=chunk);
  await ready;assert.match(output,/READY/);const exited=once(probe,'exit');
  const pressed={...emptyReport(),buttons:BUTTON_MASKS.xbox};bridge.setReport(pressed);
  await delay(900);bridge.setReport(pressed);await delay(100);
  bridge.setReport(emptyReport());await delay(80);bridge.setReport(pressed);await delay(200);bridge.setReport(emptyReport());
  const [code]=await exited;assert.equal(code,0);
  const events=JSON.parse(output.trim().split(/\r?\n/).at(-1));
  const chord=['91:down','71:down','71:up','91:up'];assert.deepEqual(events,[...chord,...chord]);
});
