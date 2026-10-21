# Windows 原生手柄输出

当前本地源码将电脑键盘映射替换为 ViGEmBus Xbox 360 虚拟设备。游戏通过标准 XInput 获取状态。Wi-Fi、USB、蓝牙继续使用现有配对与 WebSocket 通道。

## 使用

源码环境运行 `npm install`、`npm run setup:gamepad`，然后运行根目录的 `安装手柄驱动.cmd`。安装完成后运行 `npm start`。已有 ViGEmBus 驱动的电脑无需重复安装。

便携包为 `releases/OrbitPad-Receiver-Windows-x64-v0.5.0-xinput.zip`。完整解压，首次运行包内的 `安装手柄驱动.cmd`，然后启动 `OrbitPadReceiver.exe`。新版 Android 安装包为 `releases/OrbitPad-Android-v0.5.0.apk`。本次仅本地构建，没有更新 GitHub 发布文件。

接收端退出时移除虚拟设备。可运行 Windows 的 `joy.cpl` 查看手柄；游戏中选择 Xbox 手柄操作。具体游戏兼容性尚未逐一验证。

## 控件和迁移

- 双摇杆输出独立的有符号 16 位模拟轴，Y 轴由屏幕向下转换为 XInput 向上。径向死区之外连续缩放行程，斜向保持圆形范围。
- ABXY、方向键、LB / RB、View / Menu 对应原生手柄按钮。映射也支持 L3、R3、禁用以及摇杆方向。
- LT / RT 输出 0–255。默认按住为全按；Android 与网页均可按住后向下滑动减小力度。旧客户端的数字扳机兼容为 0 / 255。
- 映射目标使用手柄控件 ID。旧版手机传来的完整键盘映射采用原生默认对应关系。新版 Android 加载旧设置或模板时保留布局、大小、死区、触感设置，重置键盘映射。
- 暂停、重配对、断线、后台及通信超时会归零全部控件。原生子进程另有 1.6 秒看门狗，父进程异常退出后由管道 EOF 释放并移除设备。
- 预览模式仅计算手柄报告，不创建系统设备。

## 依赖和构建

使用官方 [ViGEmBus 1.22.0](https://github.com/nefarius/ViGEmBus/releases/tag/v1.22.0) 与 [Nefarius.ViGEm.Client 1.21.256](https://www.nuget.org/packages/Nefarius.ViGEm.Client/1.21.256)。准备脚本固定下载地址并验证 SHA-256，从客户端包提取 x64 原生 DLL。驱动安装器保持官方文件和签名，通过安装向导由用户完成安装。

ViGEmBus 已[停止维护](https://docs.nefarius.at/projects/ViGEm/End-of-Life/)。项目使用该官方最终版本，没有自行编写或要求安装未签名的内核驱动。客户端 MIT 许可证位于 `native/ViGEm-LICENSE.txt`。

`npm run build:windows` 编译原生桥接及桌面程序，打包驱动安装器，并运行原生模式自检。无驱动的构建机可显式使用 `powershell -File scripts/build-windows.ps1 -PreviewSelfTest`，报告将标记未测试原生输出。`npm run build:android` 构建并校验原有签名的 APK。

## 验证

2026-10-21 已通过 103 项自动化检查及 Redmi 真机 76 项步骤，其中 31 项与 Windows XInput 实际读值核对一致，详见[原生手柄测试报告](NATIVE-GAMEPAD-TEST-REPORT.md)。

`npm test` 检查模拟量、按钮位、重映射、旧协议兼容、暂停、断线和超时；Windows 下驱动可用时，通过 `XInputGetState` 实际读回已知按钮、双摇杆、双扳机值，并验证原生看门狗归零和退出移除设备。驱动不可用时该集成测试明确标记跳过。

`npm run test:android` 执行 Rust 格式、单元测试、Clippy 及真实 Rust 客户端与接收端的 78 项协议检查，包含扳机模拟量、映射、生命周期、重连、USB / 蓝牙本地握手。

`python scripts/verify-release.py` 检查 APK 和 ZIP 内容，并对解压后的接收端执行原生启动、暂停、恢复、二维码及退出自检。预览协议检查不能代替具体手机或游戏的端到端兼容测试。
