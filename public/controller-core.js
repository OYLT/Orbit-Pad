export const KEY_LABELS = Object.freeze({
  ...Object.fromEntries('ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map(k => [k, k])),
  ...Object.fromEntries('0123456789'.split('').map(k => [k, k])),
  Space: '空格', Enter: 'Enter', Escape: 'Esc', Tab: 'Tab',
  Shift: 'Shift', Control: 'Ctrl', Alt: 'Alt',
  ArrowUp: '↑', ArrowDown: '↓', ArrowLeft: '←', ArrowRight: '→',
});

export const DEFAULT_MAPPING = Object.freeze({
  a: 'Space', b: 'E', x: 'R', y: 'F', lb: 'Q', rb: 'C',
  lt: 'Control', rt: 'Shift', select: 'Tab', start: 'Escape',
  up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight',
  moveUp: 'W', moveDown: 'S', moveLeft: 'A', moveRight: 'D',
  lookUp: 'ArrowUp', lookDown: 'ArrowDown', lookLeft: 'ArrowLeft', lookRight: 'ArrowRight',
});

export const CONTROL_LABELS = Object.freeze({
  a: 'A · 跳跃', b: 'B · 交互', x: 'X · 装填', y: 'Y · 动作',
  lb: 'LB · 左肩键', rb: 'RB · 右肩键', lt: 'LT · 左扳机', rt: 'RT · 右扳机',
  select: 'View · 背包', start: 'Menu · 暂停',
  up: '方向键 ↑', down: '方向键 ↓', left: '方向键 ←', right: '方向键 →',
  moveUp: '左摇杆 ↑', moveDown: '左摇杆 ↓', moveLeft: '左摇杆 ←', moveRight: '左摇杆 →',
  lookUp: '右摇杆 ↑', lookDown: '右摇杆 ↓', lookLeft: '右摇杆 ←', lookRight: '右摇杆 →',
});

export const PRESETS = Object.freeze({
  general: { name: '通用游戏', mapping: DEFAULT_MAPPING },
  racing: { name: '竞速驾驶', mapping: { ...DEFAULT_MAPPING, a: 'Space', b: 'R', x: 'E', y: 'Q', rt: 'W', lt: 'S', moveUp: 'W', moveDown: 'S' } },
  platform: { name: '平台跳跃', mapping: { ...DEFAULT_MAPPING, a: 'Space', b: 'X', x: 'Z', y: 'C', moveUp: 'ArrowUp', moveDown: 'ArrowDown', moveLeft: 'ArrowLeft', moveRight: 'ArrowRight' } },
});

export function validMapping(mapping) {
  return mapping && typeof mapping === 'object' && !Array.isArray(mapping)
    && Object.keys(mapping).length === Object.keys(DEFAULT_MAPPING).length
    && Object.keys(DEFAULT_MAPPING).every(k => Object.hasOwn(mapping, k) && Object.hasOwn(KEY_LABELS, mapping[k]));
}

export function normalizeState(value) {
  if (!value || !Array.isArray(value.buttons) || !Array.isArray(value.axes)
      || value.buttons.length > 14 || value.axes.length !== 4) throw new Error('无效的手柄状态');
  const buttons = [...new Set(value.buttons)];
  const buttonNames = ['a', 'b', 'x', 'y', 'lb', 'rb', 'lt', 'rt', 'select', 'start', 'up', 'down', 'left', 'right'];
  if (!buttons.every(b => buttonNames.includes(b))) throw new Error('无效按键');
  if (!value.axes.every(v => Number.isFinite(v) && Math.abs(v) <= 1)) throw new Error('无效摇杆');
  return { buttons, axes: [...value.axes] };
}

export function stateToKeys(state, mapping = DEFAULT_MAPPING, deadzone = 0.24) {
  const keys = new Set(state.buttons.map(b => mapping[b]));
  const [mx, my, lx, ly] = state.axes;
  if (mx < -deadzone) keys.add(mapping.moveLeft);
  if (mx > deadzone) keys.add(mapping.moveRight);
  if (my < -deadzone) keys.add(mapping.moveUp);
  if (my > deadzone) keys.add(mapping.moveDown);
  if (lx < -deadzone) keys.add(mapping.lookLeft);
  if (lx > deadzone) keys.add(mapping.lookRight);
  if (ly < -deadzone) keys.add(mapping.lookUp);
  if (ly > deadzone) keys.add(mapping.lookDown);
  return keys;
}
