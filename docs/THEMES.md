# Android 主题与外观

## 使用

「主题」页提供全局「跟随系统深色模式 / 浅色 / 深色」选择，以及液态玻璃与 Material 3 两张错落排列的主题卡片。颜色模式适用于两种设计，覆盖页面、手柄、编辑器、原生控件和已打开的手柄设置弹窗。默认跟随系统；监听 `uiMode` 更新时保留当前连接与布局。点击主题卡片时以 400 ms 的扩展裁切和淡入过渡进入二级页，返回时收起；关闭系统动画时直接切换，快速切页和分离时清理动画。窄屏与大字体自动改为单列；返回键先返回主题列表，再返回主页。二级页、滚动位置和选择在 Activity 重建时恢复。

- **液态玻璃**：玻璃质量和折射设置由原设置页迁入。「使用液态玻璃」启用玻璃导航。折射滑杆为 0–160%，步进 0.1%，拖动期间显示精确百分比、更新实际 Dock 与页内真实 GPU 预览并保存。旧四档迁移为 0 / 50 / 100 / 160%，没有新偏好时继续读取旧值。
- **Material 3**：预设鸢尾紫、海盐蓝、青玉绿、蔷薇粉、暖沙金、雾岩灰。使用 Google Material Color Utilities 的 HCT 色调算法生成主色、容器色、中性色、文字和轮廓色。选择配色立即启用 Material 3，并关闭系统动态取色。
- **系统动态取色**：Android 12+ 优先通过 `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` 异步获取当前壁纸颜色，从有效的主色、辅色中选取种子，使用 HCT 生成浅色或深色 Monet 配色。监听壁纸颜色变化，并在恢复前台时刷新。系统未发布壁纸颜色时回退到系统调色板，并显示当前来源；提供手动刷新和从所选壁纸图片取色。图片仅在本地缩小采样，保存颜色种子，不保存图片，不读取受限的壁纸文件或申请全盘存储权限。低版本禁用动态取色开关，保留六组预设。

Material 3 覆盖主页、连接页、设置页、导航栏、模板预览、实际手柄、原生弹窗与控件、布局编辑器、陀螺仪提示和扫码页。Material 3 使用独立的固定底部导航栏，横屏使用全高侧边导航栏；导航占据独立布局空间、贴合窗口边缘，移除悬浮胶囊、外边距和阴影。液态玻璃保留原浮动 Dock。横屏隐藏状态栏，允许内容延伸到刘海区域，不预留状态栏或刘海 Insets，正反横屏一致。切回液态玻璃保留 Monet 偏好；玻璃预览在 Material 3 模式下也独立使用生产渲染器。

玻璃效果继续遵守设备 API、低内存与省电策略：Android 13+ 支持完整折射，Android 12 使用模糊，低版本或简洁模式回退到基础材质。强度始终保存，二级页提示当前限制。

## 验证

构建并签名 ARM64 / x86_64 APK，签名与 16 KB 对齐验证通过。Redmi K40 Pro+（M2012K11C，Android 16 / HyperOS 3）真机检查：

- 主题专项 24 项：错落卡片、二级导航、旧偏好迁移、拖动松手前的数值保存和真实透镜像素变化、0.737 连续强度、设置面板迁移、六组不同配色、系统实际色值、无玻璃 Material 导航、编辑器颜色、切换恢复、Activity 重建与横屏。
- 玻璃回归 37 项：控件、跨页状态、键盘、系统安全区、三种质量、真实背景模糊与折射、连续滑杆保存、关于面板与重建。另记录三档 FrameMetrics 冒烟采样；不保证稳定 120 Hz。
- 全局外观专项 36 项：覆盖两种设计的浅色/深色、文字对比度、手柄与编辑器、已打开设置弹窗的换色、跟随系统配置、固定导航、正反横屏 Insets、卡片动画和清理、实际壁纸种子与色调。报告见 `artifacts/appearance/final/`。
- 当前 HyperOS 壁纸服务返回的主色为 `#E45D30`，旧系统调色板仍为紫色。已验证新实现使用真实壁纸种子，生成橙色系 Monet 浅色方案，替换旧默认紫色。没有更改手机壁纸；壁纸服务监听已接入，但本轮未实际更换壁纸测试事件。跟随系统使用应用配置变更模拟验证，没有更改全局系统深色设置。
- 本轮未使用 Android 8–12 物理设备验证降级效果。

运行：

```powershell
python scripts/build-android.py
python scripts/build-android.py --instrumentation
python scripts/test-glass-android.py --serial DEVICE_SERIAL --suite appearance --output artifacts/appearance/final
python scripts/test-glass-android.py --serial DEVICE_SERIAL --suite themes --output artifacts/appearance/themes-regression
python scripts/test-glass-android.py --serial DEVICE_SERIAL --suite glass --output artifacts/theme-designs/regression
```

全局外观报告与截图：`artifacts/appearance/final/`；主题回归：`artifacts/appearance/themes-regression/`；横屏回归 38 项：`artifacts/appearance/landscape-regression/`。此前玻璃回归：`artifacts/theme-designs/regression/`。测试恢复原有外观偏好。

## 代码与依赖

- `ThemePalette.java`：统一主题角色、HCT 调色板、系统色与原生主题覆盖。
- `AppShell.java`：主题卡片、二级页、颜色选择与连续滑杆。
- `GlassPreview.java`：独立 GlassBackdrop / GlassDock 预览，复用生产 shader。
- `ThemeArtwork.java`：适配主题色的矢量预览插画。
- `MaterialNavigation.java`：固定 Material 底栏与侧栏。
- `WallpaperPalette.java`：异步壁纸取色、颜色变更监听与缓存。
- `ThemeDialogBuilder.java`：原生弹窗与控件的实时主题色。
- `third_party/MaterialColorUtilities/`：Material Components Android 1.10.0 中七个未修改的 HCT 工具类、固定 JAR、来源与校验值。Apache 2.0 许可随 APK 打包，可在设置的关于区域阅读。
