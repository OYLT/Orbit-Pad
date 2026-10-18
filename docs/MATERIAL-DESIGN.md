# Material Design 3 界面规范

版本：0.3.0。应用自有界面统一采用 Material 3 深色视觉语言。

| 角色 | 色值 |
| --- | --- |
| 背景 / Surface | `#141218` |
| Surface container | `#1D1B20` |
| Surface container high | `#2B2930` |
| Primary / On primary | `#D0BCFF` / `#381E72` |
| Primary container | `#4F378B` |
| Secondary container | `#4A4458` |
| On surface / On surface variant | `#E6E0E9` / `#CAC4D0` |
| Outline | `#938F99` |

覆盖 Android 原生连接页和断开确认框、手机手柄与设置、浏览器配对与电脑控制台、Windows 原生接收窗口。Android 平台控件与 WinForms 自绘控件按同一套颜色、字阶和形状规范绘制；未引入 Material Components SDK。

- 连接输入框采用描边样式，主操作采用圆角填充按钮。
- 常规页面使用 24–28 dp 圆角的色调卡片；对话框使用 28 dp 圆角与遮罩。
- 手柄移除硬件拟物外壳，使用平面色调按键和圆形摇杆。按键的基础几何尺寸为 52 px；全屏按可用宽高缩放。
- 设置页提供分段控件、描边选择框、带进度轨道的 Material 滑块。触感开关具有选中、未选中与键盘焦点状态。
- 自定义控件提供按压或悬停状态；浏览器支持减少动态效果偏好。
- 全屏右上角可直接进入设置。系统返回先关闭设置，再次返回询问是否断开。
- 各平台的系统状态栏、输入法、系统网络权限对话框由操作系统提供。

主要源文件：`public/styles.css`、`public/index.html`、`public/app.js`、`android/app/src/main/java/app/orbitpad/controller/MainActivity.java`、`desktop/OrbitPadReceiver.cs`。
