# ORBIT PAD · 手机无线手柄

Android App + Windows 原生接收端，将手机变成 Xbox 360 / XInput 手柄。
网页端、浏览器控制台和静态资源服务已删除。HTTP / WebSocket 继续用于原生客户端通信。

## 最新发布：0.5.0 Layout Editor

- [Android APK](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-layout-editor/OrbitPad-Android-v0.5.0.apk)
- [Windows x64 接收端](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-layout-editor/OrbitPad-Receiver-Windows-x64-v0.5.0-native.zip)
- [更新说明、测试记录与 SHA-256](https://github.com/OYLT/Orbit-Pad/releases/tag/v0.5.0-layout-editor)

包含分组布局编辑、横向配置卡片、陀螺仪启动自动校准与 F1 转向预设，并保留液态玻璃 Dock 和线性扳机。应用内版本保持 0.5.0，沿用原签名，可覆盖安装。发布验证见 [本版发布说明](docs/RELEASE-LAYOUT-EDITOR.md)。

## 本地构建

- Android：`releases/OrbitPad-Android-v0.5.0.apk`，Android 8.0+，ARM64 / x86_64。
- Windows：`releases/OrbitPad-Receiver-Windows-x64-v0.5.0-native.zip`，Windows 10 / 11 x64。
- 延续项目原签名，保留旧模板、映射、布局和手感设置，旧模板自动补充 Xbox 键。

本地构建另含手动陀螺仪校准更新：删除回正按键，进入手柄显示悬浮提示，点击「开始校准」才采样。此更新尚未上传到上方 GitHub 发布链接，安装本地 APK 可体验。

## 使用

1. 完整解压 Windows 包，首次运行“安装手柄驱动.cmd”安装 ViGEmBus，再启动 OrbitPadReceiver.exe。
2. 手机安装 APK，在 App 连接页扫描电脑二维码，或输入电脑地址与六位配对码。
3. 保持连接，电脑切换到支持 XInput 的游戏。USB 与蓝牙直连仍受支持，见 [使用说明](docs/README-START.txt)。

手柄按键页按返回键即可回到主页并保持连接，同时释放所有输入。主页右上角实时显示连接状态与延迟，点击状态可管理连接；点击「进入手柄」继续游戏，需要断开时在连接页选择「断开当前连接」。

应用横屏时，Dock 改为左侧竖排，页面在右侧独立滚动，不再保留底部 Dock 空间。横屏自动收起系统状态栏，可从屏幕边缘滑动临时呼出；竖屏恢复状态栏和底部 Dock。布局避让刘海和系统导航区，旋转保留当前页面和表单输入。验证方式见 [横屏布局说明](docs/LANDSCAPE-UI.md)。

二维码保留原有数据格式，由 App 内扫码器解析，不再打开网页手柄。

## 手柄与赛车

- 编辑器的显示控件、重置布局、保存并返回已集中在屏幕顶部正中；手柄设置按钮也位于顶部正中。
- 新增「极限竞速：地平线」模板：右上圆形 A，下方等宽长条 RT；最左侧等宽 LT 与 A 顶部、RT 底部对齐；其余控件错开摆放，默认启用线性油门与游戏震动。
- 「手感调节 → 游戏震动反馈」接收游戏真实 XInput 双马达输出，合并为手机马达强度；独立于原来的按键触感开关。使用此功能需同时更新 Android App 和 Windows 接收端，并在游戏中打开手柄震动。切后台、失焦、进入设置、断线或关闭开关会停止震动。

- Xbox 键使用 Orbit Pad App 图标，通过 Win+G 呼出 Game Bar；按住只触发一次，松开后可再次触发。电脑需安装并启用 Game Bar。
- 编辑与使用手柄共用全屏画布，忽略系统栏和刘海安全区，不留固定比例边空，按键可调整至屏幕四边。点击控件打开悬浮圆角二级菜单，在位置、尺寸、显示之间切换；拖动调整位置，拖右下角改变尺寸。
- 方向键与 XABY 默认分别绑定为一组，可一起移动、缩放，也可在悬浮菜单中解绑，单独编辑。
- XABY 和 LB / LT / RT / RB 共用连续宽高调节，按实际屏幕边界限制尺寸，不再使用旧的肩键长宽上限。
- 主页使用横向配置卡片，逐张显示各自保存的布局、大小和显隐。滑动、左右按钮或键盘方向键均可切换，配合高斯模糊与堆叠动画；关闭系统动画时直接切换。
- 编辑完成后选择「保存并返回」更新当前配置，系统预设和自定义模板均独立保留样式，也可另存为新模板。
- 手感设置中可开启线性油门 / 刹车，沿扳机实际长边滑动：左/上端 0%，右/下端 100%，显示当前百分比，松手独立归零。关闭时按住全按，默认关闭。
- 手感设置中可开启陀螺仪赛车转向，使用旋转向量传感器计算相对手机屏幕法线的转角，映射到左摇杆横轴。每次进入已启用陀螺仪的手柄、开启功能或恢复控制时，显示圆角悬浮校准提示菜单；点击「开始校准」后才开启传感器采样，保持手机稳定约 0.6 秒，将当前握持姿态设为中心。等待和校准期间转向输出为零；可选择「暂不校准」或「取消校准」，稍后通过「设置 → 手感调节 → 陀螺仪校准」再次打开提示。手柄及编辑器已删除回正按键。
- 转向比仅提供「F1 标准」与「自定义」。F1 档总行程 360°，左右各 180° 满舵；自定义保留旧设置，可选择单侧 15°–450°。支持跨圈累计角度、转向死区与反向，触摸左摇杆优先。默认关闭，无旋转传感器设备禁用开关。
- 切后台、失焦、打开设置、断线、电脑暂停和超时均释放输入；旋转传感器仅在前台控制且点击开始校准后采集，恢复控制后重新提示，等待用户开始。保留旧模板与自定义手感设置；旧模板的回中控件数据可加载，但不再显示该按键。

F1 档采用常见模拟方向盘预设，实际赛车设置可能不同；360° 总行程定义可参考 [Logitech 官方说明](https://support.logi.com/hc/en-nz/articles/8358055253271-In-Game-Settings-for-Pro-Wheels)。当前校准流程与验证见 [手动陀螺仪校准说明](docs/MANUAL-GYRO-CALIBRATION.md)。

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
