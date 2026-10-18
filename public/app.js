import { KEY_LABELS, DEFAULT_MAPPING, CONTROL_LABELS, PRESETS, validMapping, stateToKeys } from './controller-core.js';

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const store = {
  get(key, fallback) { try { return JSON.parse(localStorage.getItem(`orbit.${key}`)) ?? fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(`orbit.${key}`, JSON.stringify(value)); } catch { /* Private browsing may disable storage. */ } },
};
let mapping = store.get('mapping', DEFAULT_MAPPING);
if (!validMapping(mapping)) mapping = { ...DEFAULT_MAPPING };
let preset = store.get('preset', 'general');
if (![...Object.keys(PRESETS), 'custom'].includes(preset)) preset = 'general';
let basePreset = store.get('basePreset', 'general');
if (!PRESETS[basePreset]) basePreset = 'general';
let deadzone = Number(store.get('deadzone', 0.24));
if (!Number.isFinite(deadzone) || deadzone < 0.1 || deadzone > 0.5) deadzone = 0.24;
let size = Number(store.get('size', 100));
if (!Number.isFinite(size) || size < 80 || size > 110) size = 100;
let haptics = store.get('haptics', true);
let token = store.get('token', null);
let isHost = false;
let hostInfo = null;
let serverStatus = null;
let socket = null;
let authenticated = false;
let playing = false;
let reconnectTimer;
let reconnectAttempt = 0;
let manuallyDisconnected = false;
let wakeLock;
let hostPoll;
let lastSend = 0;
let inputs = 0;
let toastTimer;
let remoteDisplay = false;
const androidApp = /OrbitPadAndroid\//.test(navigator.userAgent);
const buttonPointers = new Map();
const stickPointers = new Map();
const axes = [0, 0, 0, 0];
const pressed = new Set();

function toast(message) {
  $('#toast').textContent = message;
  $('#toast').classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $('#toast').classList.remove('visible'), 3500);
}
async function api(path, data) {
  const response = await fetch(path, data === undefined ? { cache: 'no-store' } : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || '连接失败，请重试');
  return result;
}
function send(value) { if (socket?.readyState === WebSocket.OPEN && authenticated) socket.send(JSON.stringify(value)); }
function vibrate() { if (haptics && navigator.vibrate) navigator.vibrate(9); }
function canSend() { return playing && authenticated && serverStatus?.ready && !serverStatus?.paused && !document.hidden; }
function currentState() { return { buttons: [...pressed], axes: [...axes] }; }
function sendState(force = false) {
  if (!canSend()) return;
  const time = performance.now();
  if (!force && time - lastSend < 16) return;
  lastSend = time;
  send({ type: 'state', ...currentState() });
}
function setInputDisplay(state, currentMapping = mapping) {
  const keys = stateToKeys(state, currentMapping, deadzone);
  const root = $('#input-keys');
  root.replaceChildren();
  if (!keys.size) {
    const span = document.createElement('span'); span.className = 'empty-input'; span.textContent = '等待操作'; root.append(span);
  } else {
    for (const key of keys) { const kbd = document.createElement('kbd'); kbd.textContent = KEY_LABELS[key] || key; root.append(kbd); }
  }
  $('#input-count').textContent = `${String(inputs).padStart(2, '0')} INPUTS`;
}
function paintState(state = currentState()) {
  $$('[data-control]').forEach(button => {
    const active = state.buttons.includes(button.dataset.control);
    button.classList.toggle('pressed', active);
    button.setAttribute('aria-pressed', String(active));
  });
  $$('[data-stick]').forEach(stick => {
    const index = Number(stick.dataset.stick) * 2;
    const x = state.axes[index]; const y = state.axes[index + 1];
    stick.classList.toggle('active', Math.hypot(x, y) > deadzone);
    stick.querySelector('.stick-cap').style.transform = `translate(${x * 19}px, ${y * 19}px)`;
  });
  setInputDisplay(state);
}
function releaseAll() {
  buttonPointers.clear(); stickPointers.clear(); pressed.clear(); axes.fill(0);
  paintState(); send({ type: 'release' });
}

for (const button of $$('[data-control]')) {
  const control = button.dataset.control;
  button.addEventListener('contextmenu', event => event.preventDefault());
  button.addEventListener('pointerdown', event => {
    if (event.button !== 0 || remoteDisplay) return;
    event.preventDefault();
    button.setPointerCapture(event.pointerId);
    buttonPointers.set(event.pointerId, control);
    pressed.add(control); inputs++; vibrate(); paintState(); sendState(true);
  });
  function up(event) {
    if (!buttonPointers.has(event.pointerId)) return;
    buttonPointers.delete(event.pointerId);
    if (![...buttonPointers.values()].includes(control)) pressed.delete(control);
    paintState(); sendState(true);
  }
  button.addEventListener('pointerup', up);
  button.addEventListener('pointercancel', up);
  button.addEventListener('lostpointercapture', up);
  // Keyboard activation is available for desktop preview and accessibility.
  button.addEventListener('keydown', event => {
    if (![' ', 'Enter'].includes(event.key) || event.repeat || remoteDisplay) return;
    event.preventDefault(); pressed.add(control); inputs++; paintState(); sendState(true);
  });
  button.addEventListener('keyup', event => {
    if (![' ', 'Enter'].includes(event.key)) return;
    event.preventDefault(); pressed.delete(control); paintState(); sendState(true);
  });
}

for (const stick of $$('[data-stick]')) {
  const index = Number(stick.dataset.stick) * 2;
  function move(event) {
    if (stickPointers.get(event.pointerId) !== index) return;
    const rect = stick.getBoundingClientRect();
    const radius = rect.width * 0.42;
    let x = (event.clientX - rect.left - rect.width / 2) / radius;
    let y = (event.clientY - rect.top - rect.height / 2) / radius;
    const length = Math.hypot(x, y);
    if (length > 1) { x /= length; y /= length; }
    axes[index] = Math.round(x * 1000) / 1000;
    axes[index + 1] = Math.round(y * 1000) / 1000;
    paintState(); sendState();
  }
  stick.addEventListener('pointerdown', event => {
    if (event.button !== 0 || remoteDisplay || [...stickPointers.values()].includes(index)) return;
    event.preventDefault(); stick.setPointerCapture(event.pointerId);
    stickPointers.set(event.pointerId, index); inputs++; vibrate(); move(event); sendState(true);
  });
  stick.addEventListener('pointermove', move);
  function up(event) {
    if (stickPointers.get(event.pointerId) !== index) return;
    stickPointers.delete(event.pointerId); axes[index] = 0; axes[index + 1] = 0;
    paintState(); sendState(true);
  }
  stick.addEventListener('pointerup', up);
  stick.addEventListener('pointercancel', up);
  stick.addEventListener('lostpointercapture', up);
  stick.addEventListener('contextmenu', event => event.preventDefault());
}

function saveMapping() {
  store.set('mapping', mapping); store.set('preset', preset); store.set('basePreset', basePreset);
  $('#profile-select').value = preset;
  $('#profile-select option[value="custom"]').hidden = preset !== 'custom';
  send({ type: 'mapping', mapping });
  $('#controller .trigger[data-control="lt"] small').textContent = KEY_LABELS[mapping.lt].toUpperCase();
  $('#controller .trigger[data-control="rt"] small').textContent = KEY_LABELS[mapping.rt].toUpperCase();
  paintState();
}
function renderMappings() {
  const grid = $('#mapping-grid'); grid.replaceChildren();
  for (const [control, label] of Object.entries(CONTROL_LABELS)) {
    const row = document.createElement('label'); row.className = 'mapping-row';
    const span = document.createElement('span'); span.textContent = label;
    const select = document.createElement('select'); select.setAttribute('aria-label', `${label} 映射`);
    for (const [key, text] of Object.entries(KEY_LABELS)) { const option = new Option(text, key); select.add(option); }
    select.value = mapping[control];
    select.addEventListener('change', () => { releaseAll(); mapping[control] = select.value; preset = 'custom'; saveMapping(); });
    row.append(span, select); grid.append(row);
  }
}
function openSettings() { releaseAll(); renderMappings(); $('#settings-dialog').showModal(); }
function openHelp() { releaseAll(); $('#help-dialog').showModal(); }
for (const id of ['settings-button', 'mapping-button', 'feature-mapping', 'nav-mapping', 'play-settings']) $(`#${id}`).addEventListener('click', openSettings);
for (const id of ['guide-button', 'nav-help', 'mode-help']) $(`#${id}`).addEventListener('click', openHelp);
$('#nav-controller').addEventListener('click', () => $('#controller-stage').scrollIntoView({ behavior: 'smooth', block: 'center' }));
$$('.close-dialog').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
$$('dialog').forEach(dialog => dialog.addEventListener('click', event => {
  if (event.target !== dialog) return;
  const rect = dialog.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
}));
$$('[data-settings-tab]').forEach(button => button.addEventListener('click', () => {
  $$('[data-settings-tab]').forEach(tab => tab.classList.toggle('selected', tab === button));
  $('#mapping-settings').hidden = button.dataset.settingsTab !== 'mapping';
  $('#feel-settings').hidden = button.dataset.settingsTab !== 'feel';
}));
$('#profile-select').value = preset;
$('#profile-select').addEventListener('change', event => {
  if (!PRESETS[event.target.value]) return;
  releaseAll(); preset = basePreset = event.target.value; mapping = { ...PRESETS[preset].mapping }; saveMapping();
  toast(`已切换到${PRESETS[preset].name}`);
});
$('#restore-mapping').addEventListener('click', () => { releaseAll(); preset = basePreset; mapping = { ...PRESETS[preset].mapping }; saveMapping(); renderMappings(); toast('已恢复预设按键'); });
$('#haptic-toggle').checked = haptics;
$('#haptic-toggle').addEventListener('change', event => { haptics = event.target.checked; store.set('haptics', haptics); vibrate(); });
$('#deadzone-range').value = deadzone * 100;
$('#deadzone-value').textContent = `${Math.round(deadzone * 100)}%`;
$('#deadzone-range').addEventListener('input', event => { releaseAll(); deadzone = Number(event.target.value) / 100; store.set('deadzone', deadzone); $('#deadzone-value').textContent = `${Math.round(deadzone * 100)}%`; send({ type: 'deadzone', value: deadzone }); });
$('#scale-range').value = size;
function applySize() { document.documentElement.style.setProperty('--controller-scale', size / 100); $('#scale-value').textContent = `${size}%`; }
applySize();
$('#scale-range').addEventListener('input', event => { size = Number(event.target.value); store.set('size', size); applySize(); });
for (const range of $$('.range-field input[type="range"]')) {
  const fill = () => range.style.setProperty('--range-fill', `${(Number(range.value) - Number(range.min)) / (Number(range.max) - Number(range.min)) * 100}%`);
  range.addEventListener('input', fill); fill();
}
saveMapping();

function fitController() {
  const stage = $('#controller-stage');
  document.documentElement.style.setProperty('--preview-scale', Math.min((stage.clientWidth - 32) / 700, (stage.clientHeight - 80) / 330, 1.2));
  const factor = Math.min((innerWidth - 48) / 700, (innerHeight - 84) / 330, 1.9);
  document.documentElement.style.setProperty('--fit-scale', Math.max(0.3, factor));
}
async function enterPlay() {
  if (!isHost && !authenticated) { toast('请先输入配对码连接电脑'); $('#code-input').focus(); return; }
  if (!isHost && (!serverStatus?.ready || serverStatus?.paused)) { toast('电脑接收端尚未就绪或已暂停'); return; }
  releaseAll(); playing = true; remoteDisplay = false; document.body.classList.add('play-mode'); fitController();
  updatePlayStatus();
  try { await document.documentElement.requestFullscreen?.(); } catch { /* iOS uses the full-viewport layout. */ }
  try { await screen.orientation?.lock?.('landscape'); } catch { /* Orientation lock is optional. */ }
  try { wakeLock = await navigator.wakeLock?.request('screen'); } catch { /* Available on secure contexts only. */ }
  if (isHost) toast('电脑交互预览：手机配对后可控制游戏');
}
async function exitPlay() {
  releaseAll(); playing = false; document.body.classList.remove('play-mode');
  try { await wakeLock?.release(); } catch { /* Already released. */ }
  try { screen.orientation?.unlock?.(); } catch { /* Unsupported. */ }
  if (document.fullscreenElement) await document.exitFullscreen().catch(() => {});
}
for (const id of ['fullscreen-button', 'feature-fullscreen', 'play-button']) $(`#${id}`).addEventListener('click', enterPlay);
$('#exit-play').addEventListener('click', exitPlay);
$('#dismiss-rotate').addEventListener('click', () => $('#rotate-hint').style.setProperty('display', 'none'));
addEventListener('resize', fitController);
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement && playing && !isHost) releaseAll(); });
addEventListener('keydown', event => { if (event.key === 'Escape' && playing) exitPlay(); });
function updatePlayStatus() {
  $('#play-status').textContent = isHost ? '交互预览' : !authenticated ? '连接已中断' : serverStatus?.paused ? '电脑已暂停' : serverStatus?.mode === 'preview' ? '已连接 · 预览模式' : '已连接 · 控制中';
  $('#stage-mode').textContent = remoteDisplay ? '手机实时输入' : canSend() ? '无线控制中' : '交互预览';
  $('#interaction-hint').textContent = remoteDisplay ? '正在显示手机的实时操作' : canSend() ? '电脑切换到游戏窗口，即可开始操作' : '点击按键或拖动摇杆，试试手感';
}
function updateSystemStatus(status) {
  serverStatus = status;
  const node = $('#system-state');
  node.classList.toggle('error', !status.ready || !!status.error);
  const text = status.error ? '接收端异常' : !status.ready ? '接收端启动中' : status.paused ? '接收已暂停' : status.mode === 'preview' ? '预览模式' : '接收端已就绪';
  node.replaceChildren(); const dot = document.createElement('i'); node.append(dot, document.createTextNode(text));
  node.title = status.error || '';
  $('#phone-status').textContent = status.paused ? '电脑已暂停接收输入' : !status.ready ? '等待接收端就绪' : status.error ? status.error : status.mode === 'preview' ? '已连接 · 预览模式，不发送键盘输入' : '已连接，准备就绪';
  $('#play-button').disabled = !status.ready || status.paused;
  if (status.paused || !status.ready) releaseAll();
  updatePlayStatus();
}

function setPhoneConnected(value) {
  $('#pair-form').hidden = value;
  $('#phone-connected').hidden = !value;
  $('#device-name').textContent = serverStatus?.name || '电脑';
}
function connect() {
  if (!token || document.hidden || manuallyDisconnected) return;
  if (socket && socket.readyState < WebSocket.CLOSING) return;
  clearTimeout(reconnectTimer);
  const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`);
  socket = ws;
  ws.addEventListener('open', () => ws.send(JSON.stringify({ type: 'auth', token })));
  ws.addEventListener('message', event => {
    if (socket !== ws) return;
    const data = JSON.parse(event.data);
    if (data.type === 'authenticated') {
      authenticated = true; reconnectAttempt = 0;
      updateSystemStatus(data); setPhoneConnected(true);
      send({ type: 'mapping', mapping }); send({ type: 'deadzone', value: deadzone });
      toast(data.mode === 'preview' ? '连接成功，电脑当前为预览模式' : '已连接电脑，点击开始控制');
      if (androidApp && data.ready && !data.paused) enterPlay();
    } else if (data.type === 'pong') {
      const rtt = Math.max(0, Math.round(performance.now() - data.sent));
      $('#latency-label').textContent = `${rtt} ms RTT`; $('#play-latency').textContent = `${rtt} ms`;
    } else if (data.type === 'status') updateSystemStatus(data);
    else if (data.type === 'busy') { manuallyDisconnected = true; $('#pair-error').textContent = '已有另一台手机连接，请先断开该设备。'; toast('已有手柄连接这台电脑'); }
    else if (data.type === 'revoked') { token = null; store.set('token', null); manuallyDisconnected = true; toast('电脑已重置配对，请输入新的配对码'); }
  });
  ws.addEventListener('close', event => {
    if (socket !== ws) return;
    authenticated = false; releaseAll(); setPhoneConnected(false);
    $('#latency-label').textContent = 'LINK OFFLINE'; $('#play-latency').textContent = '— ms'; updatePlayStatus();
    if (event.code === 4001) { token = null; store.set('token', null); $('#pair-error').textContent = '配对已失效，请输入电脑上的新配对码。'; }
    if (!manuallyDisconnected && token) {
      $('#pair-error').textContent = '连接中断，正在重新连接…';
      reconnectTimer = setTimeout(connect, Math.min(1000 * 2 ** reconnectAttempt++, 8000));
    }
    if (playing) { exitPlay(); toast('连接已断开，所有按键已释放'); }
  });
  ws.addEventListener('error', () => { $('#pair-error').textContent = '无法连接电脑，请检查 Wi-Fi 和接收端。'; });
}
async function pair(code) {
  $('#pair-submit').disabled = true; $('#pair-error').textContent = '';
  try {
    const device = /iPhone|iPad/.test(navigator.userAgent) ? 'iPhone / iPad' : /Android/.test(navigator.userAgent) ? 'Android 手机' : '浏览器手柄';
    const result = await api('/api/pair', { code, device });
    manuallyDisconnected = true;
    if (socket) { socket.close(); socket = null; }
    authenticated = false;
    token = result.token; store.set('token', token); manuallyDisconnected = false; reconnectAttempt = 0; connect();
  } catch (error) { $('#pair-error').textContent = error.message; }
  finally { $('#pair-submit').disabled = false; }
}
$('#pair-form').addEventListener('submit', event => { event.preventDefault(); const code = $('#code-input').value.trim(); if (/^\d{6}$/.test(code)) pair(code); });
$('#code-input').addEventListener('input', event => event.target.value = event.target.value.replace(/\D/g, ''));
$('#disconnect-button').addEventListener('click', () => {
  releaseAll(); manuallyDisconnected = true; clearTimeout(reconnectTimer);
  token = null; store.set('token', null); socket?.close(); authenticated = false; setPhoneConnected(false);
  $('#pair-error').textContent = ''; toast('已断开连接');
});

function updateQr() {
  const url = $('#network-select').value;
  if (!url) return;
  const ip = new URL(url).hostname;
  $('#qr-image').src = `/api/host/qr?ip=${encodeURIComponent(ip)}&v=${hostInfo?.pairCode}`;
}
async function updateHost() {
  try {
    const info = await api('/api/host');
    updateSystemStatus(info);
    $('#pair-code').textContent = info.pairCode;
    const changed = !hostInfo || info.pairCode !== hostInfo.pairCode || info.urls.join() !== hostInfo.urls.join();
    const oldSelection = $('#network-select').value;
    hostInfo = info;
    if (changed) {
      $('#network-select').replaceChildren(...info.urls.map(url => new Option(url.replace('http://', ''), url)));
      if (info.urls.includes(oldSelection)) $('#network-select').value = oldSelection;
      updateQr();
      $('.qr-wrap').hidden = info.urls.length === 0;
      $('.scan-label').textContent = info.urls.length ? '用手机相机扫码连接' : '未检测到局域网，请先连接 Wi-Fi';
    }
    const state = $('#connection-state');
    state.classList.toggle('connected', info.connected && !info.paused);
    state.classList.toggle('paused', info.paused);
    state.querySelector('span:nth-child(2)').textContent = info.paused ? '接收已暂停' : info.connected ? `${info.device} 已连接` : '等待手机连接';
    $('#pause-button').textContent = info.paused ? '恢复接收' : '暂停接收';
    remoteDisplay = info.connected && !playing;
    if (remoteDisplay) {
      paintState({ buttons: info.buttons, axes: info.axes });
      const root = $('#input-keys'); root.replaceChildren();
      if (info.keys.length) for (const key of info.keys) { const kbd = document.createElement('kbd'); kbd.textContent = KEY_LABELS[key] || key; root.append(kbd); }
      else { const span = document.createElement('span'); span.className = 'empty-input'; span.textContent = '等待手机操作'; root.append(span); }
    } else if (!pressed.size && !stickPointers.size) paintState();
    updatePlayStatus();
  } catch { $('#system-state').classList.add('error'); $('#system-state').textContent = '接收端连接中断'; }
}
$('#network-select').addEventListener('change', updateQr);
$('#copy-address').addEventListener('click', async () => {
  const url = $('#network-select').value;
  if (!url) return toast('请先连接局域网');
  try { await navigator.clipboard.writeText(url); toast('连接地址已复制，在手机浏览器中打开'); }
  catch { toast(`手机打开：${url}`); }
});
$('#pause-button').addEventListener('click', async () => { try { await api('/api/host/pause', { paused: !hostInfo.paused }); await updateHost(); toast(hostInfo.paused ? '已暂停接收，所有按键已释放' : '已恢复接收'); } catch (error) { toast(error.message); } });
$('#reset-button').addEventListener('click', async () => { try { await api('/api/host/reset', {}); await updateHost(); toast('已断开所有设备并生成新配对码'); } catch (error) { toast(error.message); } });

setInterval(() => {
  if (!authenticated || document.hidden) return;
  send({ type: 'ping', sent: performance.now() });
  if (canSend()) sendState(true);
}, 450);
addEventListener('blur', releaseAll);
addEventListener('pagehide', () => { releaseAll(); socket?.close(); });
document.addEventListener('visibilitychange', () => {
  releaseAll();
  if (document.hidden) { clearTimeout(reconnectTimer); socket?.close(); }
  else if (!isHost && token) { connect(); if (playing) navigator.wakeLock?.request('screen').then(lock => wakeLock = lock).catch(() => {}); }
});

async function init() {
  try {
    const response = await fetch('/api/host', { cache: 'no-store' });
    isHost = response.ok && location.pathname !== '/controller';
    document.body.classList.toggle('phone-client', !isHost);
    $('#host-card').hidden = !isHost; $('#phone-card').hidden = isHost;
    if (isHost) {
      await updateHost(); hostPoll = setInterval(updateHost, 800);
    } else {
      updateSystemStatus(await api('/api/status'));
      const code = new URLSearchParams(location.hash.slice(1)).get('pair');
      const nativeToken = new URLSearchParams(location.hash.slice(1)).get('token');
      if (location.hash) history.replaceState(null, '', location.pathname);
      if (androidApp && nativeToken && /^[0-9a-f]{64}$/.test(nativeToken)) { token = nativeToken; store.set('token', token); connect(); }
      else if (code && /^\d{6}$/.test(code)) { $('#code-input').value = code; await pair(code); }
      else if (token) connect();
    }
  } catch { toast('接收端暂时不可用，请检查电脑上的启动窗口'); $('#system-state').textContent = '接收端未连接'; }
}
await init();
fitController();
