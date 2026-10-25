import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { EventEmitter } from 'node:events';
import { emptyReport } from './controller-core.js';

export class GamepadBridge extends EventEmitter {
  constructor({ dryRun = false, executable = fileURLToPath(new URL('../native/bin/OrbitPadGamepad.exe', import.meta.url)) } = {}) {
    super();
    this.dryRun = dryRun;
    this.executable = executable;
    this.ready = dryRun;
    this.report = emptyReport();
    this.error = '';
    this.mode = dryRun ? 'preview' : 'xinput';
  }

  start() {
    if (this.dryRun || this.child) return;
    if (process.platform !== 'win32') {
      this.error = '原生手柄接收端需要 Windows。可以使用 npm run preview 预览。';
      return;
    }
    if (!existsSync(this.executable)) {
      this.error = '原生手柄组件缺失，请运行 npm run setup:gamepad，或重新完整解压 Windows 接收端。';
      return;
    }
    this.stopping = false;
    this.error = '';
    this.ready = false;
    this.child = spawn(this.executable, [], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    const failed = error => {
      clearInterval(this.keepAlive); clearTimeout(this.startupTimer);
      this.ready = false;
      if (!this.stopping) this.error = this.error || error?.message || '原生手柄进程已退出，请重新启动接收端。';
      this.report = emptyReport();
      this.emit('status');
    };
    let output = '';
    this.child.stdout.setEncoding('utf8');
    this.child.stdout.on('data', chunk => {
      output += chunk;
      const lines = output.split(/\r?\n/);
      output = lines.pop();
      for (const line of lines) {
        if (line === 'READY') {
          clearTimeout(this.startupTimer);
          this.ready = true; this.error = ''; this.write(); this.emit('status');
        }
        if (line.startsWith('ERROR:')) failed(new Error(line.slice(6)));
      }
    });
    this.child.stderr.on('data', chunk => failed(new Error(String(chunk).trim().slice(0, 240))));
    this.child.on('error', failed);
    this.child.on('exit', () => failed());
    this.child.stdin.on('error', failed);
    this.keepAlive = setInterval(() => this.write(), 400);
    this.keepAlive.unref();
    this.startupTimer = setTimeout(() => {
      failed(new Error('原生手柄启动超时，请检查 ViGEmBus 驱动。'));
      this.child.kill();
    }, 15000);
    this.startupTimer.unref();
  }

  write() {
    if (this.ready && !this.dryRun && this.child?.stdin.writable) {
      const r = this.report;
      this.child.stdin.write(`pad:${[r.buttons, r.leftTrigger, r.rightTrigger, r.leftX, r.leftY, r.rightX, r.rightY].join(',')}\n`);
    }
  }

  setReport(report) {
    this.report = { ...report };
    this.write();
  }

  async stop() {
    this.stopping = true;
    clearInterval(this.keepAlive); clearTimeout(this.startupTimer);
    this.setReport(emptyReport());
    const child = this.child;
    if (child && child.exitCode === null && child.signalCode === null && !child.killed) {
      await new Promise(resolve => {
        const timer = setTimeout(() => { child.kill(); resolve(); }, 2000);
        child.once('exit', () => { clearTimeout(timer); resolve(); });
        child.stdin.end('quit\n');
      });
    }
    this.ready = this.dryRun;
    this.child = null;
  }
}
