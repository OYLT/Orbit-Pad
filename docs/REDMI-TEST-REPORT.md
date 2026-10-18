# Redmi K40 Pro+ 真机验证

日期：2026-10-18（新加坡时间）。本次版本：Android / Windows 0.3.0。

设备：Redmi K40 Pro+（M2012K11C），Android 16 / API 36，Android System WebView 143.0.7499.192。

## 本次结果

45 个真机步骤全部通过。手机与电脑使用同一 Wi-Fi 连接；USB / ADB 用于安装、测试输入和截图，没有使用 USB 端口反向转发。

| 验证范围 | 结果 |
| --- | --- |
| APK 安装、原生连接页、错误配对码提示 | 通过 |
| 正确配对、自动横屏与常亮标志 | 通过 |
| ABXY、LB / RB、LT / RT、View / Menu、四向方向键逐个按下与松开 | 全部通过 |
| 摇杆死区、左摇杆斜向输入 | 通过 |
| 双摇杆同时输入、三指双摇杆 + A，共五个电脑键 | 通过 |
| 分别松开手指、摇杆回中、按键释放 | 通过 |
| 全屏设置入口、按键映射页、手感调节页 | 通过 |
| 设置页系统返回，保持连接并回到手柄 | 通过 |
| 切后台释放与断开、返回 App 自动重连 | 通过 |
| 系统返回确认断开、回到原生连接页 | 通过 |
| Windows 原生窗口视觉检查、暂停与恢复按钮 | 通过 |
| Windows 便携包与解压后的独立启动、桥接就绪、二维码、暂停、恢复、退出 | 通过 |
| Node 服务与输入协议自动检查 | 10 项通过 |

单指按键由 Android 系统 input 工具产生触摸流，组合手势由 Android SDK UiAutomation 注入。电脑端独立读取真实接收状态，对每个步骤断言按键、摇杆与连接状态。测试工具按小面积手指及抬起时零压力构造多指事件，避免将测试触点误描述为满尺寸掌触。未更改手机的网络、安全或识屏设置。

45 个步骤使用不会发送系统键盘的预览接收端。此前 0.2.1 已使用专用 Windows 测试窗口验证手机 A 键产生真实 Space down / up；该历史证据保留在 windows-keyboard-report.json，不将其记作本次新增测试。0.3.0 的键盘桥接实现未改动。

## Material 3 界面

Android 连接页、确认对话框、横屏手柄、设置页、浏览器控制台与使用指南、Windows 接收窗口统一为紫色 Material 3 深色视觉语言。已在真机及实际 Windows 窗口检查显示，修正地址选择框的字体裁切。设计规范见 MATERIAL-DESIGN.md。

## 证据

- artifacts/redmi-test/report.json：45 个步骤的期望与接收端实际状态。
- artifacts/redmi-test/instrumentation.txt：最终通过的运行输出。
- artifacts/redmi-test/three-finger-dual-stick-and-a.png：三指操作。
- artifacts/redmi-test/material-settings-mapping.png：真机映射设置。
- artifacts/redmi-test/material-settings-feel.png：真机手感设置。
- artifacts/redmi-test/settings-back-preserves-connection.png：设置返回后的手柄。
- artifacts/material-native.png：新版原生连接页。
- artifacts/material-final.png：最终正式接收端连接截图。
- releases/windows-self-test.json、windows-extracted-self-test.json：便携软件自检。

## 范围

未验证具体游戏、反作弊、独占输入兼容性及长时间续航、温升或丢包压力。输出为 Windows 键盘映射，不提供 XInput 或模拟量手柄输出。截图上的 RTT 仅为当时的读数。
