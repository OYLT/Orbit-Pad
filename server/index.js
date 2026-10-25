import { GamepadBridge } from './input.js';
import { createReceiver } from './receiver.js';

const dryRun = process.argv.includes('--dry-run');
const port = Number(process.env.PORT || 3210);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT 必须在 1–65535 之间');
const input = new GamepadBridge({ dryRun });
input.start();
const receiver = createReceiver({ input, port, ownerToken: process.env.ORBIT_OWNER_TOKEN || '', onShutdown: shutdown });
try {
  const result = await receiver.start();
  console.log(`\n  ORBIT PAD  /  手机无线手柄\n\n  原生 App 接收服务 → 端口 ${result.port}\n  模式 → ${dryRun ? '预览（不会输出系统手柄输入）' : 'Xbox 360 原生手柄 / XInput'}\n`);
  for (const ip of result.addresses) console.log(`  Android App 手动配对地址 → ${ip}:${result.port}`);
  console.log('\n  手机和电脑请连接同一个 Wi-Fi。按 Ctrl+C 退出。\n');
  const info = await (await fetch(`http://127.0.0.1:${result.port}/api/host`)).json();
  console.log(`  配对码 → ${info.pairCode}\n`);
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
