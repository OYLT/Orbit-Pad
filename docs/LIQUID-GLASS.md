# Android Dock Liquid Glass 实现

本地 APK 的悬浮 Dock 已采用真实页面采样、色彩处理、模糊和边缘透镜折射。选中滑块、图标和标签独立绘制，保持清晰；通过“设置 → Dock 玻璃效果”选择自动、柔和或简洁外观。

## 与参考文档的对应关系

当前项目为 Java View / Canvas，并使用现有的无 Gradle 构建脚本。参考文档明确提示，ComposeView 不能自动采样下面的原生 View，并允许按实际结构采用 View 模糊方案。本次沿用这一兼容路线，以 Android 自带的 RenderNode、RenderEffect 和 RuntimeShader 实现文档中的渲染流程，没有迁移到 Kotlin、Compose 或引入 Backdrop / Navigation Compose 依赖。

| 文档要求 | 项目实现 |
| --- | --- |
| 内容在下，Dock 在上 | GlassBackdrop 中的页面和 GlassDock 为兄弟节点 |
| 共用背景来源，避免递归采样 | 仅把页面录入一个 RenderNode，底栏和选中透镜共享来源，Dock 不进入来源 |
| 不逐帧截图为 Bitmap | 使用 GPU display list 录制与重放 |
| 色彩 → 模糊 → 折射 | ColorMatrix 饱和度 → RenderEffect blur → AGSL 胶囊边缘透镜 |
| 防止采样边界接缝 | 采样区向外扩展 28dp，并使用 CLAMP 边缘模式 |
| 清晰的前景 | 玻璃之后绘制选中滑块、边缘高光、图标和文字 |
| 选中与交互状态分开 | AppShell 驱动选中项，GlassDock 独立管理弹簧位置与按压强度 |
| 选中动画 | 按实际测量的标签边界移动弹簧滑块 |
| 按压反馈 | 长按透镜鼓起与果冻回弹，前景轻微放大；不缩放整个背景采样来源 |
| 滚动收起 | 向下累计 56dp 收起标签，向上累计 24dp 展开；保留全部导航入口 |
| 页面状态恢复 | 保存标签滚动位置和手动配对草稿；Bundle 恢复 Activity 重建前的页面 |
| 安全区域与键盘 | 系统栏和刘海 Insets；键盘显示时 GONE；旧系统补充窗口高度检测 |
| 无障碍与大字体 | 按钮角色、选中语义、键盘焦点、中文标签、至少 48dp 高度；字体变大时增加 Dock 高度 |
| 高级效果开关 | 设置页可选择柔和或简洁，设置持久保存 |

## 质量策略

| 档位 | 默认条件 | 效果 |
| --- | --- | --- |
| 完整 | Android 13+ | 饱和度、7dp 底栏模糊、2.5dp 透镜模糊、边缘折射与色散、高光和弹性手势 |
| 标准 | Android 12 / 12L，或选择柔和 | 饱和度、模糊、高光和基础交互 |
| 简化 | Android 8–11、低内存设备、省电模式，或选择简洁 | 色调胶囊、高光和基础交互 |

省电模式变化会触发刷新。GPU 渲染发生运行时异常时，本次界面自动回退到简化材质，并记录 GlassDock 日志。较新系统的图形 API 分隔在内部类中，通过版本检查后才实例化。

## HyperOS 3 沉浸与设置页

主界面显式开启 edge-to-edge，系统导航栏、分隔线和状态栏透明，关闭系统自动对比度底色。页面背景延伸至手势提示线后方；Insets 只加到页面内容和 Dock 的外边距，避免底部出现由根布局 padding 形成的独立色带。保留系统手势提示线，Dock 按导航栏安全区避让；键盘 Insets 单独作用于滚动区域。

Dock 第四项改为“设置”及齿轮图标。原关于内容嵌入设置页的可展开卡片，保留版本、系统信息、项目主页和组件与隐私说明；返回键先收起关于卡片，页面及展开状态支持 Activity 重建恢复。

折射强度与质量模式分别保存。强度提供“关闭、轻柔、标准、强烈”四档，系数为 0 / 0.5 / 1 / 1.6，默认标准。该系数参与透镜位移的 shader uniform 和效果缓存键，切换后立即刷新；关闭仅将透镜位移设为零，保留模糊和高光。柔和、简洁、省电和不支持折射的设备会保留已选强度，设置页提示何时生效。大字体或窄屏将四档改为竖向排列。

## 参考图材质与拖动反馈

底栏改为银灰磨砂胶囊，移除原先的紫色填充选中块。前方独立绘制清晰透镜及克制的彩色边缘，选中图标使用薄荷绿。深色页面保留深色环境下的透明材质，不改变整套页面主题。

长按超过系统阈值后，滑块向两侧和上下鼓起，松手后通过低阻尼弹簧回弹。拖动时，手指速度和位置弹簧速度共同驱动宽度拉伸、纵向压缩和倾斜；减速、反向和松手时，形变有独立的惯性恢复。透镜内背景采用较低模糊，按压时增加折射、轻微放大和边缘色散，图标与文字独立绘制。

整个 Dock 的水平偏移由手指速度和位移共同驱动，限制在约 10dp，并通过另一组弹簧回中。触摸速度采用屏幕坐标计算，避免 Dock 自身移动造成坐标反馈。背景采样同步加入 Dock 的实际偏移，保证透镜与真实背景对齐。

拖动只预览目标，松手才提交导航。垂直移出、ACTION_CANCEL、主手指离开、窗口失焦和隐藏时取消未提交的选择；边缘拖动使用橡皮筋限位。保留原生点击、键盘焦点和无障碍长按，系统关闭动画时直接跟手并取消弹性偏移。

动作测试保存静止、长按、回弹、左右拖动和归位截图，同时录制 18 秒真实设备演示。运行：

```powershell
python scripts/test-glass-android.py --serial DEVICE_SERIAL --suite motion --output artifacts/liquid-motion
```

当前材质与设置的 37 项回归结果保存在 `artifacts/liquid-motion/regression/`；19 项动作结果、截图和录屏保存在 `artifacts/liquid-motion/`。动作检查覆盖长按不切页、速度拉伸、阻尼跟随、反向、松手选择、取消、边缘限位、多指、无障碍长按及隐藏后的状态清理。性能数据仍为 FrameMetrics 冒烟检查，完整效果没有稳定 120Hz 的保证。

## 左端越界修复与透镜增强

橡皮筋拖动和弹簧回弹可能让滑块坐标暂时小于零。之前把负坐标当作初始化标记，在 onDraw 提前返回，导致只剩图标、整层玻璃消失。现在通过独立的 positionReady 标记区分初始化，绘制、颜色插值和布局恢复都允许合法的负坐标。

删除透镜内部顶部和底部的两段装饰弧线，仅保留外沿。透镜折射位移增益从 1.6 调整到 3.2，边缘作用带从高度的 13% 扩展到 24%，增加静止与按压时的背景放大感；四档强度仍分别控制该效果，关闭档保持无折射。底栏本身的模糊和折射参数不变。

新增 9 项边界像素检查，使用高对比条纹背景和隐藏 Dock 的基线，在实际负坐标状态检查玻璃是否仍可见，覆盖三种质量模式、左右端、反复跨越和松手。该检查在旧 APK 上于 negative-position-keeps-glass-auto 失败，修复后通过；测试图层会在结束后移除。

```powershell
python scripts/test-glass-android.py --serial DEVICE_SERIAL --suite edge --output artifacts/liquid-edge-fix/edge
```

本次结果分别保存在 `artifacts/liquid-edge-fix/edge/`、`artifacts/liquid-edge-fix/motion/` 和 `artifacts/liquid-edge-fix/regression/`。修复前的复现截图和失败记录保存在 `artifacts/liquid-edge-fix/before/`。

## 构建与真机检查

```powershell
python scripts/build-android.py
python scripts/build-android.py --instrumentation
.build-tools\android-sdk\platform-tools\adb.exe -s DEVICE_SERIAL install -r releases\OrbitPad-Android-v0.5.0.apk
python scripts/test-glass-android.py --serial DEVICE_SERIAL --suite glass --output artifacts/liquid-motion/regression
```

完整像素比较套件要求 Android 13+ 的连接设备，不需要电脑接收端。测试 APK 单独编译并使用同一项目密钥签名，不打入正式 APK。测试结束后恢复原有玻璃质量偏好。

37 项 HyperOS 3 真机检查覆盖导航、滚动位置与输入草稿恢复、重复点击、返回键、键盘隐藏、旋转、GPU 与透镜是否启用、真实背景模糊、折射像素差异、背景移动、触摸取消、手势提示线区域的背景像素、四档强度的实际像素差异和保存、嵌入关于区域的展开收起，以及实际 Activity 重建。测试用彩色条纹暂时替代页面内容，通过完整 / 标准 / 简化截图差异验证效果，随后移除测试图层。

测试还使用 Window FrameMetrics 采集三种质量各八次来回平滑滚动的 TOTAL_DURATION，输出帧数、P50、P95、P99 和超过当前屏幕刷新预算的数量。这是设备性能冒烟检查，不等同于隔离环境下的 Macrobenchmark 或 Perfetto 分析，也不构成稳定满帧保证。当前 Redmi K40 Pro+ / Android 16 / 120Hz 的完整效果仍有超出 8.33ms 预算的帧；对帧率敏感时可以切换柔和或简洁。

最新材质的 HyperOS 3 回归结果和截图保存在 `artifacts/liquid-motion/regression/`，包含 `report.json`、`instrumentation.txt`、实际主页和横屏截图，以及用于采样验证的三档对比截图。Android 8–12 的真实设备、系统大字体和实际 TalkBack 操作尚未在本次设备上验证。

核心源码：`GlassBackdrop.java`、`GlassDock.java`、`GlassQualityPolicy.java`、`AppShell.java`。参考平台 API：[RenderNode](https://developer.android.com/reference/android/graphics/RenderNode)、[RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)、[AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)。
