# Orbit Pad 0.5.0 SoftJelly

发布标签：`v0.5.0-softjelly`。发布日期：2026-10-25。

本版本发布 2026-10-22 完成的 Android 与 Windows 原生手柄更新。Android 包名、应用内版本 0.5.0 和原发布签名保持不变。

## 更新

- Dock 适配 AndroidLiquidGlass 的 AGSL 源码与分层方案，提供真实背景模糊、边缘折射、方向高光、蓝色选中前景和定向切页。APK 内包含 Apache 2.0 许可与署名。
- 减弱果冻效果：降低按压膨胀、拖动拉伸、倾斜和弹簧冲量，提高阻尼，让回弹更快收敛。
- 增加基于设备 X/Z 重力分量的赛车转向，屏幕朝上放平自动回中，支持死区、满舵角度与反向，触摸左摇杆优先。
- 可视化拖动与缩放游戏控件，肩键独立设置宽高；线性油门与刹车沿控件长边输入并独立释放。
- Xbox 控件通过 Win+G 呼出 Game Bar，每次按下触发一次。返回主页保持连接并释放输入，主页显示状态和延迟。
- 移除网页手柄、浏览器控制台与静态资源，保留原生客户端所需的 HTTP / WebSocket、配对和二维码接口。

## 安装

手机安装 Release 附件 `OrbitPad-Android-v0.5.0.apk`；它与本地 `OrbitPad-Android-v0.5.0-KyantLiquidGlass-SoftJelly.apk` 字节一致，SHA-256 为 `c3991e2e9b4e32f0cfbae1bb8fab2b4c795bd3daa5f97a37950d96b98c0c0173`。

Windows 完整解压 `OrbitPad-Receiver-Windows-x64-v0.5.0-native.zip`，首次运行“安装手柄驱动.cmd”，再启动 `OrbitPadReceiver.exe`。已安装 ViGEmBus 的电脑可直接启动。Android 支持 8.0+、ARM64 / x86_64；Windows 支持 10 / 11 x64。完整玻璃效果要求 Android 13+，较旧系统使用模糊或简洁外观。

## 发布前验证（2026-10-25）

- Node：18 项全部通过，包含 Windows XInput 实际读回、看门狗归零、Game Bar 快捷键与网页路由移除。
- Java：39 项 X/Z 重力计算检查通过。
- Rust：13 项单元测试、fmt、Clippy 和构建通过。
- Rust / 接收端协议：80 项检查通过。
- APK：v2 / v3 签名校验通过，沿用原证书；ARM64 / x86_64 原生库与压缩包完整性通过。
- Windows：发布包解压后自检通过；随包使用说明已同步为当前版本，服务器和原生桥接源码与工作区一致。

既有真机记录：SoftJelly 最终 APK 的动作 19 项、左端边界 9 项均通过，记录中的 APK SHA-256 与发布 APK 一致，报告随 Release 提供。先前 Kyant 玻璃版本的 86 项检查见 [玻璃实现说明](LIQUID-GLASS.md)，它对应减弱果冻效果之前的 APK。赛车功能与此前真机验证范围见 [赛车验证记录](NATIVE-RACING-VALIDATION.md)。本次发布没有重新进行手机真机操作。

具体赛车游戏的驾驶手感、Game Bar 在具体游戏中的实际显示，以及生产 USB / 蓝牙完整真机流程仍需进一步验证。

安装包校验值见 Release 附件 `SHA256SUMS.txt`。
