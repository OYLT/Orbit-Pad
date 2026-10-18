import { KeyboardBridge } from './input.js';
import { createReceiver } from './receiver.js';
import { spawn } from 'node:child_process';

const dryRun = process.argv.includes('--dry-run');
const port = Number(process.env.PORT || 3210);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT 必须在 1–65535 之间');
const input = new KeyboardBridge({ dryRun });
input.start();
const receiver = createReceiver({ input, port, ownerToken: process.env.ORBIT_OWNER_TOKEN || '', onShutdown: shutdown });
try {
  const result = await receiver.start();
  console.log(`\n  ORBIT PAD  /  手机无线手柄\n\n  电脑管理端 → http://localhost:${result.port}\n  模式 → ${dryRun ? '预览（不会发送键盘输入）' : 'Windows 键盘映射'}\n`);
  for (const ip of result.addresses) console.log(`  手机连接 → http://${ip}:${result.port}`);
  console.log('\n  手机和电脑请连接同一个 Wi-Fi。按 Ctrl+C 退出。\n');
  if (process.argv.includes('--open') && process.platform === 'win32') {
    const opener = spawn('rundll32.exe', ['url.dll,FileProtocolHandler', `http://localhost:${result.port}`], { windowsHide: true, stdio: 'ignore', detached: true });
    opener.on('error', () => console.log(`请手动打开 http://localhost:${result.port}`));
    opener.unref();
  }
} catch (error) {
  console.error(error.code === 'EADDRINUSE' ? `端口 ${port} 已被占用。请关闭已有接收端，或设置 PORT。` : error.message);
  await input.stop();
  process.exit(1);
}
let closing = false;
async function shutdown() { if (closing) return; closing = true; await receiver.stop(); process.exit(0); }
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
if (/^[1-9][0-9]*$/.test(process.env.ORBIT_PARENT_PID || '')) {
  const parentPid = Number(process.env.ORBIT_PARENT_PID);
  const parentWatchdog = setInterval(() => {
    try { process.kill(parentPid, 0); } catch (error) { if (error.code === 'ESRCH') shutdown(); }
  }, 1500);
  parentWatchdog.unref();
}
