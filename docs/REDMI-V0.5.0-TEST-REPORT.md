# Redmi K40 Pro+：Orbit Pad 0.5.0 真机验证

日期：2026-10-19（新加坡时间）。设备 M2012K11C，Android 16 / API 36，ARM64，系统页大小 4096 字节。正式 APK 为 0.5.0 / versionCode 6，已覆盖安装，沿用原发布签名。

最终 APK SHA-256：`ec932af6fe115f2cdf9b18e05c041a05ac7158d83804be90b1957da99c661f1b`。

## 结果

六个套件共 **97 项真机步骤全部通过**，每份报告均记录相同的最终 APK 哈希。电脑预览接收端独立核对按键与连接状态，不向其他 Windows 应用发送测试按键。

| 套件 | 步骤 | 覆盖范围 |
| --- | ---: | --- |
| baseline | 45 | 配对校验、全部按钮按下与释放、双摇杆、三指、设置入口、后台释放、重连、断开 |
| settings | 17 | 三种预设、A → Enter、死区 50%、尺寸 80%、触觉开关、Activity 重建后恢复 |
| shell | 15 | Dock 四页、默认扫码与四种连接选择、布局拖动和大小、模板保存与加载、实际二维码图片解码配对 |
| usb | 9 | 真实 USB 自动配对、A 键、三指五键、取消释放、后台断开、前台重连、主动断开 |
| camera | 2 | 实际扫码 Activity 打开相机预览对象、退出释放相机 |
| bluetooth | 9 | 已配对电脑的真实 RFCOMM 自动配对、A 键、三指五键、取消释放、后台断开、前台重连、主动断开 |

USB 使用 Redmi 的物理数据线，以 ADB reverse 从手机本机端口 3210 转发到预览接收端 3215。蓝牙使用 Redmi 与电脑 LAPTOP-MHB9SRM0 的系统配对，Windows 原生 Winsock 服务与 Android BluetoothSocket 承载协议，未使用 PAN 或网络共享。局域网套件使用电脑 `10.243.161.88:3215`。

三指检查同时按左摇杆右上、右摇杆左上和 A，由接收端确认 `D`、`W`、`ArrowLeft`、`ArrowUp`、`Space` 五个按键；切后台和断开后确认所有键释放。测试结束恢复测试前的模板设置与临时授予的相机、蓝牙连接权限；系统蓝牙配对保留。

二维码测试把实际接收端生成的 PNG 交给手机 ZXing 解码，再由 Rust 校验和配对。相机启停另行检查，尚未验证将实体相机对准显示器的完整光学扫码流程。未验证具体游戏兼容性、长期稳定性、耗电或温升。

## 证据与其他检查

每个套件的原始报告与 instrumentation 输出保存在 `artifacts/redmi-v0.5.0-<套件>/`，报告包含设备、版本、APK 哈希、传输方式和各步期望 / 实际状态。界面截图已复制到 `docs/images/home-v0.5.0.png` 和 `docs/images/connect-v0.5.0.png`。

Rust 单元测试 8 项、实际 Rust 核心到接收端的协议检查 76 项、Node / Windows 接收端测试 10 项通过；Rust fmt / Clippy 无警告。协议原始记录为 `artifacts/android-rust-v0.5.0/protocol-report.json`。Android 构建检查两种 ABI、JNI 导出、16 KB ELF / ZIP 对齐及 APK 签名；Windows 包与解压后自检均通过。

```powershell
python scripts/build-android.py --instrumentation
python scripts/test-redmi.py --serial <序列号> --address <电脑IP:端口> --port <端口> --suite <套件> --output artifacts/redmi-v0.5.0-<套件>
```

执行前安装正式 APK 并启动预览接收端；USB / 蓝牙套件还需启用对应的本机直连服务，蓝牙先完成系统配对。可用 `--bluetooth-name` 指定已配对电脑名。测试代码未打入正式 APK。
