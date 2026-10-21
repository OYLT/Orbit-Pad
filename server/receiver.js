import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { networkInterfaces, hostname } from 'node:os';
import { randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { WebSocketServer, WebSocket } from 'ws';
import QRCode from 'qrcode';
import { DEFAULT_MAPPING, normalizeState, stateToGamepad, validMapping, legacyMapping, emptyReport, describeReport } from '../public/controller-core.js';

const STATIC = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/controller', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']],
  ['/controller-core.js', ['controller-core.js', 'text/javascript; charset=utf-8']],
  ['/icon.svg', ['icon.svg', 'image/svg+xml']],
  ['/manifest.webmanifest', ['manifest.webmanifest', 'application/manifest+json']],
]);
const emptyState = () => ({ buttons: [], axes: [0, 0, 0, 0] });
const local = req => ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(req.socket.remoteAddress);

export function lanAddresses() {
  const rank = name => /wi-?fi|wlan|wireless|无线/i.test(name) ? 0 : /clash|tun|tap|virtual|vethernet|docker|wsl|vpn/i.test(name) ? 3 : /ethernet|以太网/i.test(name) ? 1 : 2;
  return [...new Set(Object.entries(networkInterfaces()).sort(([a], [b]) => rank(a) - rank(b))
    .flatMap(([, interfaces]) => interfaces.filter(i => i.family === 'IPv4' && !i.internal && !i.address.startsWith('169.254.')).map(i => i.address)))];
}

export function createReceiver({ input, port = 3210, host = '0.0.0.0', now = Date.now, ownerToken = '', onShutdown } = {}) {
  let pairCode = String(randomInt(100000, 1000000));
  let paused = false;
  let active = null;
  let listeningPort = port;
  const tokens = new Map();
  const attempts = new Map();
  const wss = new WebSocketServer({ noServer: true, maxPayload: 8192, perMessageDeflate: false });
  const addresses = lanAddresses();
  const validHosts = new Set(['localhost', '127.0.0.1', '[::1]', hostname().toLowerCase(), ...addresses]);
  const send = (ws, value) => { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value)); };
  const status = () => ({
    app: 'orbit-pad', version: '0.5.0',
    name: hostname(), port: listeningPort, paused, mode: input.mode,
    ready: input.ready, error: input.error, connected: !!active,
    device: active?.device || null,
  });
  const broadcast = () => { for (const ws of wss.clients) if (ws.authenticated) send(ws, { type: 'status', ...status() }); };
  const release = ws => { if (active === ws) { ws.state = emptyState(); input.setReport(emptyReport()); } };
  const reply = (res, code, value, type = 'application/json; charset=utf-8') => {
    res.writeHead(code, { 'Content-Type': type, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' ws: wss:; frame-ancestors 'none'" });
    res.end(typeof value === 'string' || Buffer.isBuffer(value) ? value : JSON.stringify(value));
  };
  async function body(req) {
    let data = '';
    for await (const chunk of req) {
      data += chunk;
      if (data.length > 2048) throw new Error('请求过大');
    }
    return JSON.parse(data || '{}');
  }
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      let requestHost;
      try { requestHost = new URL(`http://${req.headers.host}`).hostname.toLowerCase(); } catch { return reply(res, 400, { error: '无效主机' }); }
      if (!validHosts.has(requestHost)) return reply(res, 403, { error: '不允许的主机地址' });
      if (req.method === 'POST' && req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) return reply(res, 403, { error: '不允许跨站请求' });

      if (req.method === 'GET' && url.pathname === '/api/status') return reply(res, 200, status());
      if (req.method === 'GET' && url.pathname === '/api/direct/bootstrap') {
        if (!local(req)) return reply(res, 403, { error: '直连握手仅限电脑本地通道' });
        return reply(res, 200, { app: 'orbit-pad', code: pairCode });
      }
      if (url.pathname.startsWith('/api/host')) {
        if (!local(req)) return reply(res, 403, { error: '请在电脑上打开 localhost 管理接收端' });
        if (req.method === 'GET' && url.pathname === '/api/host') {
          const urls = addresses.map(ip => `http://${ip}:${listeningPort}`);
          return reply(res, 200, { ...status(), pairCode, urls, report: input.report, inputs: describeReport(input.report), keys: [],
            buttons: active?.state?.buttons || [], axes: active?.state?.axes || [0, 0, 0, 0] });
        }
        if (req.method === 'GET' && url.pathname === '/api/host/qr') {
          const ip = addresses.includes(url.searchParams.get('ip')) ? url.searchParams.get('ip') : addresses[0];
          if (!ip) return reply(res, 404, { error: '未检测到局域网地址' });
          const link = `http://${ip}:${listeningPort}/controller#pair=${pairCode}`;
          if (url.searchParams.get('format') === 'png') return reply(res, 200, await QRCode.toBuffer(link, { width: 360, margin: 2, color: { dark: '#17221d', light: '#ffffff' } }), 'image/png');
          const svg = await QRCode.toString(link, { type: 'svg', margin: 1, color: { dark: '#17221d', light: '#ffffff' } });
          return reply(res, 200, svg, 'image/svg+xml');
        }
        if (req.method === 'POST' && url.pathname === '/api/host/pause') {
          const data = await body(req);
          if (typeof data.paused !== 'boolean') return reply(res, 400, { error: '无效状态' });
          paused = data.paused;
          if (active) release(active);
          broadcast();
          return reply(res, 200, status());
        }
        if (req.method === 'POST' && url.pathname === '/api/host/reset') {
          tokens.clear();
          if (active) release(active);
          for (const ws of wss.clients) { send(ws, { type: 'revoked' }); ws.close(4001, '重新配对'); }
          active = null;
          pairCode = String(randomInt(100000, 1000000));
          return reply(res, 200, { ok: true });
        }
        if (req.method === 'POST' && url.pathname === '/api/host/shutdown') {
          const candidate = String(req.headers['x-orbit-owner'] || '');
          if (!ownerToken || !onShutdown || Buffer.byteLength(candidate) !== Buffer.byteLength(ownerToken)
              || !timingSafeEqual(Buffer.from(candidate), Buffer.from(ownerToken))) return reply(res, 403, { error: '仅启动此接收端的桌面进程可以关闭服务' });
          reply(res, 202, { ok: true });
          setImmediate(onShutdown);
          return;
        }
        return reply(res, 404, { error: '未找到接口' });
      }
      if (req.method === 'POST' && url.pathname === '/api/pair') {
        const address = req.socket.remoteAddress;
        const bucket = attempts.get(address) || { count: 0, expires: now() + 60000 };
        if (bucket.expires < now()) { bucket.count = 0; bucket.expires = now() + 60000; }
        if (++bucket.count > 12) { attempts.set(address, bucket); return reply(res, 429, { error: '尝试过多，请一分钟后再试' }); }
        attempts.set(address, bucket);
        const data = await body(req);
        const candidate = String(data.code || '');
        if (candidate.length !== pairCode.length || !timingSafeEqual(Buffer.from(candidate), Buffer.from(pairCode))) return reply(res, 401, { error: '配对码不正确，请查看电脑接收端' });
        if (tokens.size >= 64) return reply(res, 429, { error: '配对设备过多，请在电脑端重置配对' });
        const token = randomBytes(32).toString('hex');
        tokens.set(token, { expires: now() + 12 * 60 * 60 * 1000, device: String(data.device || '手机手柄').slice(0, 50) });
        return reply(res, 200, { token });
      }
      if (req.method === 'GET' && STATIC.has(url.pathname)) {
        const [name, mime] = STATIC.get(url.pathname);
        return reply(res, 200, await readFile(new URL(`../public/${name}`, import.meta.url)), mime);
      }
      reply(res, 404, { error: '未找到页面' });
    } catch (error) {
      reply(res, 400, { error: error instanceof SyntaxError ? '请求格式错误' : '请求无法处理' });
    }
  });
  server.on('upgrade', (req, socket, head) => {
    let origin;
    try { origin = new URL(req.headers.origin); } catch { socket.destroy(); return; }
    if (req.url !== '/ws' || origin.host !== req.headers.host || !validHosts.has(origin.hostname.toLowerCase()) || origin.protocol !== 'http:') { socket.destroy(); return; }
    wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws, req));
  });
  wss.on('connection', ws => {
    ws.state = emptyState();
    ws.mapping = { ...DEFAULT_MAPPING };
    ws.deadzone = 0.24;
    ws.lastSeen = now();
    ws.windowStart = now();
    ws.messages = 0;
    const authTimeout = setTimeout(() => { if (!ws.authenticated) ws.close(4001, '请先配对'); }, 5000);
    authTimeout.unref();
    ws.on('message', raw => {
      try {
        if (now() - ws.windowStart > 1000) { ws.windowStart = now(); ws.messages = 0; }
        if (++ws.messages > 240) { release(ws); ws.close(4008, '发送过于频繁'); return; }
        const data = JSON.parse(raw);
        if (!ws.authenticated) {
          const token = tokens.get(data.token);
          if (data.type !== 'auth' || !token || token.expires < now()) { ws.close(4001, '配对已过期'); return; }
          if (active && active.readyState === WebSocket.OPEN) { send(ws, { type: 'busy' }); ws.close(4009, '已有手柄连接'); return; }
          ws.authenticated = true;
          ws.device = token.device;
          ws.expires = token.expires;
          active = ws;
          clearTimeout(authTimeout);
          send(ws, { type: 'authenticated', ...status() });
          return;
        }
        ws.lastSeen = now();
        if (data.type === 'ping') { send(ws, { type: 'pong', sent: data.sent }); return; }
        if (data.type === 'release') { release(ws); return; }
        if (data.type === 'mapping') {
          if (!validMapping(data.mapping) && !legacyMapping(data.mapping)) throw new Error('无效映射');
          release(ws);
          ws.mapping = { ...(validMapping(data.mapping) ? data.mapping : DEFAULT_MAPPING) };
          return;
        }
        if (data.type === 'deadzone') {
          if (!Number.isFinite(data.value) || data.value < 0.1 || data.value > 0.5) throw new Error('无效死区');
          release(ws);
          ws.deadzone = data.value;
          return;
        }
        if (data.type === 'state') {
          const state = normalizeState(data);
          if (active === ws && !paused && input.ready) {
            ws.state = state;
            input.setReport(stateToGamepad(state, ws.mapping, ws.deadzone));
          }
          return;
        }
        throw new Error('未知消息');
      } catch { release(ws); ws.close(4002, '无效的控制数据'); }
    });
    ws.on('error', () => release(ws));
    ws.on('close', () => {
      clearTimeout(authTimeout);
      release(ws);
      if (active === ws) { active = null; broadcast(); }
    });
  });
  const watchdog = setInterval(() => {
    if (active && (now() - active.lastSeen > 1800 || now() > active.expires)) {
      release(active);
      active.close(4000, '连接超时');
      active = null;
    }
    for (const [token, data] of tokens) if (data.expires < now()) tokens.delete(token);
    for (const [ip, data] of attempts) if (data.expires < now()) attempts.delete(ip);
  }, 400);
  watchdog.unref();
  input.on?.('status', () => { if (!input.ready && active) release(active); broadcast(); });
  return {
    server,
    async start() {
      await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, host, resolve); });
      listeningPort = server.address().port;
      return { port: listeningPort, addresses };
    },
    async stop() {
      clearInterval(watchdog);
      if (active) release(active);
      for (const ws of wss.clients) ws.terminate();
      wss.close();
      server.closeIdleConnections();
      await new Promise(resolve => server.close(resolve));
      await input.stop?.();
    },
  };
}
