# Android Rust 核心与原生界面

当前版本为 [0.5.0](https://github.com/OYLT/Orbit-Pad/releases/tag/v0.5.0)。Android 包名保持 `app.orbitpad.controller`，versionCode 6，沿用已有发布密钥。新增 Dock 四页、扫码、模板布局与 USB / 蓝牙直连，见 [0.5.0 说明](V0.5.0.md)。

## 实现

- `android/rust/src/model.rs`：局域网地址验证、22 项映射与三种预设、手感参数校验、触点归属、摇杆归一化、手柄布局。
- `android/rust/src/lib.rs`：会话与输入状态、后台和焦点处理、设置状态、JNI 入口。触摸和界面查询只访问内存，不在主线程联网。
- `android/rust/src/transport.rs`：HTTP 配对、WebSocket 认证、500 ms 心跳、RTT、3 秒无响应检测、最长 4 秒间隔重连。阻塞连接操作有超时，旧会话结果通过代次编号丢弃。
- `NativeBridge.java` / `MainActivity.java`：Android 生命周期、原生输入框和设置控件、Canvas 绘制、MotionEvent 转发、横屏、常亮与系统触觉反馈。
- `AppShell.java`：Dock 四页与模板管理；`ScanActivity.java` / `QrDecoder.java`：相机、图片二维码识别；`BluetoothProxy.java`：已配对设备的 RFCOMM 字节通道。

手柄与设置均包含在 APK 中，无 WebView，也不加载电脑端 HTML / JavaScript。Android 通过局域网或 USB / 蓝牙字节通道上的 HTTP / WebSocket 连接电脑，不提供离线控制电脑或 XInput 输出。

运动事件合并后约每 16 ms 发出，按钮按下、抬起保留顺序，防止高刷新率触摸触发接收端频率限制。切后台、失焦、打开设置、修改映射、电脑暂停或输入桥接不可用都会清空触点。断线、超时与返回前台后从中立状态开始，不恢复此前按住的按键。配对撤销、过期或接收端繁忙会返回连接页。

映射和手感设置保存于 Android 私有偏好文件。旧版 WebView 的 localStorage 不会自动迁移；需要重新设置映射。配对 token 只在 Rust 内存保存，退出 App 进程后需重新输入配对码；切后台后返回可复用当前 token 重连。

## 构建

在 Windows x64 上安装 JDK 17+、Python 3.11+，然后执行：

```powershell
npm run build:android
npm run test:android
npm test
```

构建脚本将 Rust 1.90.0、Android NDK r28c / API 35 工具放到 `.build-tools/`，不修改系统 PATH。Rust 使用 `Cargo.lock` 锁定依赖；工具下载从官方源校验哈希。也可通过 `ANDROID_NDK_HOME` 指向已安装的兼容 NDK。

输出为 `releases/OrbitPad-Android-v0.5.0.apk`，包括 `arm64-v8a` 和 `x86_64`。原生库以未压缩形式打包，检查 ELF LOAD 段和 APK ZIP 的 16 KB 对齐，并验证 JNI 导出入口。APK 使用原有密钥签名并执行 apksigner、zipalign 校验。实现方式参考 [jni-rs 0.21 文档](https://docs.rs/jni/0.21.1/jni/) 与 [Android 16 KB 页支持文档](https://developer.android.com/guide/practices/page-sizes)。

`python scripts/verify-release.py` 检查当前 APK 和Windows 0.5.0 便携包，并生成 SHA256SUMS.txt；不会重新打包或发布 Windows 软件。

## 0.5.0 验证

Rust 单元测试 8 项、实际核心协议检查 76 项、Node / Windows 接收端测试 10 项通过，格式和 Clippy 检查无警告。最终 APK 在 Redmi K40 Pro+ / Android 16 上通过 97 项步骤，包括真实 USB 与蓝牙 RFCOMM 通道。详见 [本版真机报告](REDMI-V0.5.0-TEST-REPORT.md)。

## 0.4.0 历史验证

日期：2026-10-19，新加坡时间。

| 检查 | 结果 |
| --- | --- |
| Rust 单元测试 | 6 项通过：地址、映射校验、无效输入、触点归属、生命周期和设置释放 |
| Rust ↔ 真实接收端协议 | 70 项通过：全部按钮、双摇杆与三指、映射、死区、焦点、设置、后台、电脑暂停、桥接状态、运动突发、快速点击、断网、静默超时、重连、繁忙、撤销 |
| 原有 Node / Windows 接收端测试 | 10 项通过 |
| Rust 格式、Clippy | 通过，无警告 |
| 两种 Android ABI 编译、JNI 导出、16 KB ELF / ZIP 对齐、APK 签名 | 通过 |
| 新版 Android 真机界面、触摸、生命周期和设置保存 | Redmi K40 Pro+ / Android 16，62 项步骤通过 |

协议测试运行实际 Rust 引擎，并连接生产接收端的内存输入桥接；本轮没有向 Windows 应用发送系统按键。TCP 代理用来模拟断线和静默丢包，验证两端释放与 Rust 自动重连。测试报告在 `artifacts/android-rust/protocol-report.json`；原生构建记录在 `releases/android-rust-build.json`。

本次真机证据见 [Redmi Rust APK 测试报告](REDMI-RUST-TEST-REPORT.md)。保留的 `docs/REDMI-TEST-REPORT.md` 是 0.3.0 的历史报告。独立 instrumentation APK 和本次两个测试套件的执行方式：

```powershell
python scripts/build-android.py --instrumentation
python scripts/test-redmi.py --serial <设备序列号> --address <电脑Wi-Fi地址:端口> --port <端口> --output artifacts/redmi-rust-v0.4.0
python scripts/test-redmi.py --serial <设备序列号> --address <电脑Wi-Fi地址:端口> --port <端口> --suite settings --output artifacts/redmi-rust-v0.4.0-settings
```

使用 `scripts/test-redmi.py` 前须先安装新版 APK、启动预览接收端，并替换实际设备序列号、Wi-Fi 接收地址与端口。设置套件通过原生 UI 修改参数，验证 Activity 重建后的恢复，并在结束时还原测试前的设置。该测试不会打入正式 APK。
