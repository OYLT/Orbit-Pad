# ORBIT PAD · 手机无线手柄

Android App + Windows 原生接收端，将手机变成 Xbox 360 / XInput 手柄。
网页端、浏览器控制台和静态资源服务已删除。HTTP / WebSocket 继续用于原生客户端通信。

## 最新发布：0.5.0 SoftJelly

- [Android APK](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-softjelly/OrbitPad-Android-v0.5.0.apk)
- [Windows x64 接收端](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-softjelly/OrbitPad-Receiver-Windows-x64-v0.5.0-native.zip)
- [更新说明、测试记录与 SHA-256](https://github.com/OYLT/Orbit-Pad/releases/tag/v0.5.0-softjelly)

包含 AndroidLiquidGlass 原生适配、减弱的果冻动效、重力转向、线性扳机和布局编辑增强。应用内版本保持 0.5.0，沿用原签名，可覆盖安装。发布验证见 [SoftJelly 发布说明](docs/RELEASE-SOFTJELLY.md)。

## 本地构建

- Android：`releases/OrbitPad-Android-v0.5.0.apk`，Android 8.0+，ARM64 / x86_64。
- Windows：`releases/OrbitPad-Receiver-Windows-x64-v0.5.0-native.zip`，Windows 10 / 11 x64。
- 延续项目原签名，保留旧模板、映射、布局和手感设置，旧模板自动补充 Xbox 键。

本轮修改从 `v0.5.0-softjelly` 提供，历史 GitHub 发布包不包含这些更新。

## 使用

1. 完整解压 Windows 包，首次运行“安装手柄驱动.cmd”安装 ViGEmBus，再启动 OrbitPadReceiver.exe。
2. 手机安装 APK，在 App 连接页扫描电脑二维码，或输入电脑地址与六位配对码。
3. 保持连接，电脑切换到支持 XInput 的游戏。USB 与蓝牙直连仍受支持，见 [使用说明](docs/README-START.txt)。

手柄按键页按返回键即可回到主页并保持连接，同时释放所有输入。主页右上角实时显示连接状态与延迟，点击状态可管理连接；点击「进入手柄」继续游戏，需要断开时在连接页选择「断开当前连接」。

二维码保留原有数据格式，由 App 内扫码器解析，不再打开网页手柄。

## 手柄与赛车

- Xbox 键使用 Orbit Pad App 图标，通过 Win+G 呼出 Game Bar；按住只触发一次，松开后可再次触发。电脑需安装并启用 Game Bar。
- 所有游戏控件支持可视化调整大小。在主页布局编辑中拖动控件移动、拖右下角缩放、点击打开实时滑块预览。
- LB / LT / RT / RB 可分别调整宽度、高度并保存到模板。
- 手感设置中可开启线性油门 / 刹车，沿扳机实际长边滑动：左/上端 0%，右/下端 100%，显示当前百分比，松手独立归零。关闭时按住全按，默认关闭。
- 手感设置中可开启陀螺仪赛车转向，以重力方向为固定基准，仅使用设备 X、Z 轴分量，映射到左摇杆横轴。屏幕朝上放平时为中心，倾斜转向，回到水平自动回中，无需回正按键。支持满舵角度、转向死区与反向；触摸左摇杆优先，默认关闭，无重力传感器设备禁用开关。
- 切后台、失焦、打开设置、断线、电脑暂停和超时均释放输入。重力传感器只在前台控制时采集，恢复后继续使用重力基准，不把恢复时的倾斜姿态设为中心。旧模板自动移除回正控件，保留其余设置。

轴定义遵循 [Android 传感器坐标](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview#sensors-coords)，X、Z 为设备固定坐标，不随界面横竖屏变化。使用 `TYPE_GRAVITY` 的 X/Z 分量计算 `-atan2(X, Z)`，不读取或保存 Y 轴；X/Z 投影接近零时输出中立。

Game Bar 使用 Windows 标准快捷键，见 [Microsoft 使用说明](https://www.microsoft.com/en-gb/windows/tips/game-bar)。具体赛车游戏兼容性仍需实测。

## 开发与构建

```powershell
npm install
npm run setup:gamepad
npm start
npm test
npm run test:android
npm run build:android
npm run build:windows
python scripts/verify-release.py
```

npm start 在终端显示原生 App 配对地址和配对码。npm run preview 提供不输出系统输入的接收模式，供原生 App 测试，没有浏览器界面。

Android 使用 Java 原生控件、Canvas 与 Rust 核心，Windows 使用 WinForms。server 提供配对协议、映射和输入报告，native/GamepadBridge.cs 负责 ViGEmBus 与 Game Bar。

构建工具与签名密钥位于 .build-tools，不包含在发布包中。构建环境见 [Rust Android](docs/ANDROID-RUST.md)。仅在可信局域网使用，不要将接收端端口转发到公网。
