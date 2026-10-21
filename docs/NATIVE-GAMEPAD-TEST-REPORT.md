# 原生手柄测试报告

日期：2026-10-21（新加坡时间）。设备：Redmi K40 Pro+ / M2012K11C，Android 16 / API 36。APK 0.5.0，保留原签名覆盖安装，手机经真实 Wi-Fi 连接电脑。本轮没有修改正式应用代码。

## 结果

103 项自动化检查和 76 项真机步骤全部通过。真机中 31 项同时使用 Windows 的 `XInputGetState` 独立读取虚拟设备，每步最终读值与接收端手柄报告完全一致。

| 检查 | 数量 | 结果 |
| --- | ---: | --- |
| Node / Windows 原生手柄 | 15 | 通过 |
| Rust 单元测试 | 10 | 通过 |
| Rust 客户端至接收端协议 | 78 | 通过 |
| 真机基础触控与生命周期 | 45 | 预览模式通过 |
| 真机原生映射与设置保存 | 17 | XInput 实际读回通过 |
| 真机到 Windows 原生手柄完整链路 | 14 | XInput 实际读回通过 |

Rust 格式和 Clippy 检查通过。APK / ZIP 内容校验、最终 APK 签名和解压后的 Windows 接收端原生启动、暂停、恢复、二维码及退出自检均通过。

## 实际验证值

- LT 全按为 255，向下滑动半行程为 128；RT 四分之一力度为 64。XInput 实际读值一致。
- 三指同时操作两只摇杆和 A：按钮位为 4096，左轴为 (20277, 20276)，右轴为 (-20277, 20277)，XInput 实际读值一致。
- A 重映射为 R3：按钮位为 128；界面重建并重连后映射仍生效。
- 50% 死区抑制小幅触控，大幅摇杆输入连续输出模拟量。80% 尺寸和触觉开关保存并恢复。
- 取消触控、切后台、断开及重连后的全部按钮、轴和扳机均归零。Windows 独立测试也验证原生看门狗归零和进程退出后虚拟设备移除。

## 证据

原始结果：[汇总](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-xinput/native-gamepad-summary.json)、[45 项基础触控](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-xinput/native-gamepad-baseline.json)、[17 项原生设置](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-xinput/native-gamepad-settings-xinput.json)、[14 项完整链路](https://github.com/OYLT/Orbit-Pad/releases/download/v0.5.0-xinput/native-gamepad-native-xinput.json)。instrumentation 日志保留在本地 artifacts/native-gamepad 各套件目录。设置界面截图位于 `artifacts/native-gamepad/baseline/material-settings-mapping.png`，已检查手柄映射标签和说明。

最终 APK SHA-256：`ce2e6f592794f9d3e8e46e0984c4461ad497b6ab98a7e4b2c2ab11e4af66cca7`。三个真机套件均记录这一相同哈希。Windows 包校验见 `releases/SHA256SUMS.txt` 与 `releases/windows-extracted-self-test.json`。

## 复现

先在端口 3215 启动接收端。基础套件使用预览模式；settings 和 native 套件使用原生模式，并按实际 XInput 设备槽位指定 `--xinput-slot`。

```powershell
npm test
npm run test:android
python scripts/verify-release.py
python scripts/build-android.py --instrumentation
python scripts/test-redmi.py --serial 1f7c1da1 --address 10.243.161.88:3215 --port 3215 --suite native --native-output --xinput-slot 0 --output artifacts/native-gamepad/native-xinput
```

本轮未重新验证手机的实际 USB / 蓝牙传输，也未验证具体游戏、长时间稳定性、耗电或温升。协议自动化中的 USB / 蓝牙本地握手不能替代相应真机链路测试。测试结束已断开手机并停止本次接收服务，XInput 四个槽位均为空，虚拟设备已移除。
