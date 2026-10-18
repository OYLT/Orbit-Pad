import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { EventEmitter } from 'node:events';

const VK = { Space: 0x20, Enter: 0x0d, Escape: 0x1b, Tab: 0x09, Shift: 0x10,
  Control: 0x11, Alt: 0x12, ArrowUp: 0x26, ArrowDown: 0x28, ArrowLeft: 0x25, ArrowRight: 0x27 };
for (const k of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789') VK[k] = k.charCodeAt(0);

export class KeyboardBridge extends EventEmitter {
  constructor({ dryRun = false } = {}) {
    super();
    this.dryRun = dryRun;
    this.ready = dryRun;
    this.keys = new Set();
    this.error = '';
    this.mode = dryRun ? 'preview' : 'keyboard';
  }

  start() {
    if (this.dryRun) return;
    if (process.platform !== 'win32') {
      this.error = '键盘接收端需要 Windows。可以使用 npm run preview 预览。';
      return;
    }
    const script = fileURLToPath(new URL('../native/keyboard.ps1', import.meta.url));
    this.child = spawn('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script], {
      windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    });
    let output = '';
    this.child.stdout.setEncoding('utf8');
    this.child.stdout.on('data', chunk => {
      output += chunk;
      const lines = output.split(/\r?\n/);
      output = lines.pop();
      for (const line of lines) {
        if (line === 'READY') { this.ready = true; this.error = ''; this.emit('status'); }
        if (line.startsWith('ERROR:')) { this.error = line.slice(6); this.emit('status'); }
      }
    });
    this.child.stderr.on('data', chunk => {
      this.error = String(chunk).trim().slice(0, 240);
      this.emit('status');
    });
    const failed = error => {
      this.ready = false;
      this.error = error?.message || '键盘接收进程已退出，请重新启动接收端。';
      this.keys.clear();
      this.emit('status');
    };
    this.child.on('error', failed);
    this.child.on('exit', () => failed());
    this.child.stdin.on('error', failed);
    this.keepAlive = setInterval(() => this.write(), 400);
    this.keepAlive.unref();
  }

  write() {
    if (this.ready && !this.dryRun && this.child?.stdin.writable) {
      this.child.stdin.write(`keys:${[...this.keys].map(k => VK[k]).join(',')}\n`);
    }
  }

  setKeys(keys) {
    this.keys = new Set(keys);
    this.write();
  }

  async stop() {
    clearInterval(this.keepAlive);
    this.setKeys([]);
    if (this.child && this.child.exitCode === null && !this.child.killed) {
      this.child.stdin.end('quit\n');
      await Promise.race([
        new Promise(resolve => this.child.once('exit', resolve)),
        new Promise(resolve => { const timer = setTimeout(() => { this.child.kill(); resolve(); }, 2000); timer.unref(); }),
      ]);
    }
  }
}
