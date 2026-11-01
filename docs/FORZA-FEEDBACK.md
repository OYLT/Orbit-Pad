# 地平线模板、顶部居中与游戏震动

日期：2026-11-01。本地构建延续 0.5.0 和原签名。

## 布局

编辑器工具栏改为完整屏幕顶部居中，隐藏状态栏的残余 Insets 不再让工具栏下移。手柄设置按钮位于同一画布的顶部中央。

主页新增“极限竞速：地平线”。圆形 A 位于右上方，RT 在其下且同宽，LT 位于最左侧且同宽，LT 上下端分别与 A 顶部、RT 底部对齐。其他按键与摇杆错开，不发生边界矩形重叠；A/B/X/Y 在该模板中默认独立编辑。默认启用线性扳机与游戏震动，陀螺仪仍可按需开启。

## 游戏震动

Windows 的 ViGEm 反馈回调接收游戏 XInputSetState 输出，通过原生进程、Node 接收端、现有 WebSocket 和 Rust 核心传到 Android Vibrator。两个 0–255 马达值按最大强度合并，不能在手机上分别模拟 Xbox 双马达频率或方向盘力反馈。

原来的按键触感与新“游戏震动反馈”是两个开关。普通旧模板默认关闭游戏震动，地平线模板默认开启。新功能需要本次 Android 和 Windows 构建；旧接收端仍可操作手柄，设置中会提示更新反馈能力。服务器只给声明支持反馈的新客户端发送震动消息，兼容旧 Android 客户端。

反馈使用定期刷新与有限时长脉冲，通信停滞不会无限震动。失焦、后台、打开设置、关闭开关、暂停接收、断开或超时都会归零。原生回调只更新马达状态，定时输出避免阻塞驱动线程。

## 验证

- 20 项 Node 测试通过，包括真实 Windows XInput 输入读回、XInputSetState→ViGEm 回调、保持震动时的刷新、归零、客户端能力协商和暂停处理。
- 18 项 Rust 测试、fmt、Clippy 和 82 项协议检查通过，包括地平线对齐/不重叠、旧模板、反馈关闭与过期、生命周期清零。
- Redmi K40 Pro+ / Android 16 真实链路完成 8 个接收端核对阶段，包含 A 输入、游戏震动、顶部工具栏、关闭/重开震动、后台停止、重连中立和断开。
- Android 系统 vibrator_manager 在测试时报告当前马达 VIBRATING，强度 0.5019608，来源 app.orbitpad.controller，脉冲时长 240ms；与 XInput 大马达 128/255 一致。测试通道为 USB / ADB reverse，真实 XInput 输出和反馈均已核对。

记录、系统马达状态与截图位于 artifacts/forza-feedback-final。尚未启动实际《极限竞速：地平线》游戏验证碰撞及路面手感；不同游戏是否发送震动取决于其设置与手柄支持。

参考：[ViGEmClient 官方回调说明](https://github.com/nefarius/ViGEmClient/blob/master/README.md)、[Android VibrationEffect](https://developer.android.com/reference/android/os/VibrationEffect)。
