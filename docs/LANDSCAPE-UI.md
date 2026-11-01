# 横屏应用布局

- 横屏和反向横屏右侧页面独立滚动。液态玻璃使用左侧竖向 Dock，宽度默认 72 dp，随系统字体大小调整，距屏幕边缘 12 dp。Material 3 使用贴左边、全高、96 dp 宽的固定导航栏，页面为其保留布局空间。
- 页面顶部留白从 28 dp 缩减至 12 dp，底部仅保留 24 dp 内容留白，不再预留底部 Dock 的高度。
- 横屏收起系统状态栏，保留边缘滑动临时呼出；系统导航栏继续显示。窗口允许延伸到刘海与挖孔区域；横屏内容不使用系统状态栏或刘海 Insets，不因暂时呼出状态栏改变布局。
- 竖向 Dock 支持点击、长按和纵向拖动，文字和图标保持正向，保留玻璃折射及弹簧动画。滚动右侧页面不会收缩左侧 Dock。
- 键盘显示时暂时隐藏 Dock，关闭后恢复；输入框禁用横屏全屏提取模式。横竖屏切换复用页面，保留选中项、输入内容及滚动状态。
- 竖屏恢复系统状态栏；液态玻璃恢复底部悬浮 Dock，Material 3 恢复固定底部导航栏。手柄及布局编辑器继续使用原有横屏沉浸模式。

## 验证

2026-11-01，已连接的 Redmi M2012K11C，Android 16 / API 36，HyperOS 3。

本轮横屏检查 38 项通过，结果保存在 `artifacts/appearance/landscape-regression/`。此前玻璃导航回归 37 项、手势动画回归 19 项通过；APK 构建、v2/v3 签名及 16 KB 对齐验证通过。

```powershell
python scripts/build-android.py
python scripts/build-android.py --instrumentation
.\.build-tools\android-sdk\platform-tools\adb.exe -s <serial> install -r releases/OrbitPad-Android-v0.5.0.apk
python scripts/test-glass-android.py --serial <serial> --suite landscape --output artifacts/landscape-ui
python scripts/test-glass-android.py --serial <serial> --suite glass --output artifacts/landscape-glass-regression
python scripts/test-glass-android.py --serial <serial> --suite motion --output artifacts/landscape-motion-regression
```

`landscape` 检查两种横屏方向的左右布局、安全边距、触摸尺寸、底部空间、四页导航、纵向拖动、键盘显隐、表单旋转保留、竖屏恢复和渲染器状态。截图、检查明细与 APK SHA-256 写入对应的 `artifacts` 目录。Android 8–10 的键盘遮挡兼容分支已编译，尚未在旧系统设备实测。
