// Native receiver input mapping and XInput report conversion.
export const CONTROL_LABELS = Object.freeze({
  a: 'A', b: 'B', x: 'X', y: 'Y', lb: 'LB · 左肩键', rb: 'RB · 右肩键',
  lt: 'LT · 左扳机', rt: 'RT · 右扳机', select: 'View', start: 'Menu', xbox: 'Xbox · Game Bar',
  up: '方向键 ↑', down: '方向键 ↓', left: '方向键 ←', right: '方向键 →',
  moveUp: '左摇杆 ↑', moveDown: '左摇杆 ↓', moveLeft: '左摇杆 ←', moveRight: '左摇杆 →',
  lookUp: '右摇杆 ↑', lookDown: '右摇杆 ↓', lookLeft: '右摇杆 ←', lookRight: '右摇杆 →',
});
export const OUTPUT_LABELS = Object.freeze({ ...CONTROL_LABELS, l3: 'L3 · 左摇杆按下', r3: 'R3 · 右摇杆按下', none: '禁用' });
export const DEFAULT_MAPPING = Object.freeze(Object.fromEntries(Object.keys(CONTROL_LABELS).map(k => [k, k])));
export const PRESETS = Object.freeze({
  general: { name: '通用游戏', mapping: DEFAULT_MAPPING },
  racing: { name: '竞速驾驶', mapping: DEFAULT_MAPPING },
  platform: { name: '平台跳跃', mapping: DEFAULT_MAPPING },
});
export const BUTTON_MASKS = Object.freeze({ up: 0x0001, down: 0x0002, left: 0x0004, right: 0x0008,
  start: 0x0010, select: 0x0020, l3: 0x0040, r3: 0x0080, lb: 0x0100, rb: 0x0200,
  xbox: 0x0400, a: 0x1000, b: 0x2000, x: 0x4000, y: 0x8000 });
const legacyKeys = new Set([... 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', 'Space', 'Enter', 'Escape', 'Tab',
  'Shift', 'Control', 'Alt', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']);
const mappingShape = mapping => mapping && typeof mapping === 'object' && !Array.isArray(mapping)
  && [Object.keys(DEFAULT_MAPPING).length, Object.keys(DEFAULT_MAPPING).length - 1].includes(Object.keys(mapping).length)
  && Object.keys(mapping).every(k => Object.hasOwn(DEFAULT_MAPPING, k))
  && Object.keys(DEFAULT_MAPPING).every(k => k === 'xbox' || Object.hasOwn(mapping, k));
export const validMapping = mapping => !!mappingShape(mapping)
  && Object.values(mapping).every(k => typeof k === 'string' && Object.hasOwn(OUTPUT_LABELS, k));
export const legacyMapping = mapping => !!mappingShape(mapping) && Object.values(mapping).every(k => legacyKeys.has(k));
export const emptyReport = () => ({ buttons: 0, leftTrigger: 0, rightTrigger: 0, leftX: 0, leftY: 0, rightX: 0, rightY: 0 });

export function normalizeState(value) {
  if (!value || !Array.isArray(value.buttons) || !Array.isArray(value.axes)
      || value.buttons.length > 17 || value.axes.length !== 4) throw new Error('无效的手柄状态');
  const buttons = [...new Set(value.buttons)];
  const names = [...Object.keys(BUTTON_MASKS), 'lt', 'rt'];
  if (!buttons.every(b => names.includes(b))) throw new Error('无效按键');
  if (!value.axes.every(v => Number.isFinite(v) && Math.abs(v) <= 1)) throw new Error('无效摇杆');
  const triggers = value.triggers ?? [Number(buttons.includes('lt')), Number(buttons.includes('rt'))];
  if (!Array.isArray(triggers) || triggers.length !== 2
      || !triggers.every(v => Number.isFinite(v) && v >= 0 && v <= 1)) throw new Error('无效扳机');
  return { buttons, axes: [...value.axes], triggers: [...triggers] };
}

// Radial deadzone keeps diagonals circular; the remaining travel is continuous.
export function stateToGamepad(state, mapping = DEFAULT_MAPPING, deadzone = 0.24) {
  const report = emptyReport();
  const axes = [0, 0, 0, 0];
  const directions = { moveLeft: [0, -1], moveRight: [0, 1], moveUp: [1, 1], moveDown: [1, -1],
    lookLeft: [2, -1], lookRight: [2, 1], lookUp: [3, 1], lookDown: [3, -1] };
  const apply = (target, value) => {
    if (!value || target === 'none') return;
    if (Object.hasOwn(BUTTON_MASKS, target)) { if (value >= 0.5) report.buttons |= BUTTON_MASKS[target]; }
    else if (target === 'lt') report.leftTrigger = Math.max(report.leftTrigger, Math.round(value * 255));
    else if (target === 'rt') report.rightTrigger = Math.max(report.rightTrigger, Math.round(value * 255));
    else if (Object.hasOwn(directions, target)) { const [axis, sign] = directions[target]; axes[axis] += sign * value; }
  };
  for (const button of state.buttons) if (!['lt', 'rt'].includes(button)) apply(mapping[button] ?? button, 1);
  const triggers = state.triggers ?? [Number(state.buttons.includes('lt')), Number(state.buttons.includes('rt'))];
  apply(mapping.lt, triggers[0]); apply(mapping.rt, triggers[1]);
  for (let i = 0; i < 4; i += 2) {
    const x = state.axes[i], y = state.axes[i + 1], length = Math.hypot(x, y);
    if (length <= deadzone) continue;
    const scale = (Math.min(1, length) - deadzone) / (1 - deadzone) / length;
    const [up, down, left, right] = i === 0 ? ['moveUp', 'moveDown', 'moveLeft', 'moveRight'] : ['lookUp', 'lookDown', 'lookLeft', 'lookRight'];
    apply(mapping[x < 0 ? left : right], Math.abs(x) * scale);
    apply(mapping[y < 0 ? up : down], Math.abs(y) * scale);
  }
  if ((report.buttons & 3) === 3) report.buttons &= ~3;
  if ((report.buttons & 12) === 12) report.buttons &= ~12;
  [report.leftX, report.leftY, report.rightX, report.rightY] = axes.map(v => {
    const n = Math.max(-1, Math.min(1, v)); return Math.round(n * (n < 0 ? 32768 : 32767));
  });
  return report;
}

export function describeReport(report) {
  const parts = Object.entries(BUTTON_MASKS).filter(([, bit]) => report.buttons & bit).map(([id]) => OUTPUT_LABELS[id]);
  if (report.leftTrigger) parts.push(`LT ${Math.round(report.leftTrigger / 255 * 100)}%`);
  if (report.rightTrigger) parts.push(`RT ${Math.round(report.rightTrigger / 255 * 100)}%`);
  if (report.leftX || report.leftY) parts.push(`L (${report.leftX}, ${report.leftY})`);
  if (report.rightX || report.rightY) parts.push(`R (${report.rightX}, ${report.rightY})`);
  return parts;
}

