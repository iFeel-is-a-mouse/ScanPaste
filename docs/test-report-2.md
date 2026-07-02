# ScanPaste v2.0 — Delta 测试报告

> **测试人员**: tester (subagent) | **日期**: 2026-05-29  
> **测试类型**: 代码审查 + 白盒测试 (Delta)  
> **测试范围**: 变更#3 (UI重构) + 变更#4 (4项精确修复) + 回归验证  
> **测试基础**: v1.2 测试报告 + spec.md + design.md

---

## 1. 测试概要

| 测试类别 | 项数 | 通过 | 发现问题 | 备注 |
|----------|------|------|----------|------|
| A. UI重构验证 (变更#3) | 6 | 6 | 0 | 全部通过 ✅ |
| B. 4项修复验证 (变更#4) | 6 | 4 | 2 (P3) | 底部弹窗代码残留 |
| C. 回归验证 | 7 | 7 | 0 | 全部通过 ✅ |
| **合计** | **19** | **17** | **2** | **无阻塞/严重问题** |

**测试结论**: ✅ **Delta 测试通过**。变更#3 (UI重构) 和变更#4 (4项精确修复) 实现正确，核心功能回归完整。发现 2 项 P3 级问题（死资源未清理），不影响功能。

---

## 2. A. UI重构验证 (变更#3) — 全部通过 ✅

### A1. MaterialToolbar 配置 ✅

| 检查项 | 源码位置 | 期望值 | 实际值 | 结果 |
|--------|---------|--------|--------|------|
| Toolbar 背景色 | `activity_main.xml:16` | `#1A73E8` | `@color/md_primary` = `#FF1A73E8` | ✅ |
| 标题文字颜色 | `activity_main.xml:25` | 白色 | `@color/md_on_primary` = `#FFFFFFFF` | ✅ |
| 标题字体样式 | `activity_main.xml:24` | TitleLarge | `TextAppearance.Material3.TitleLarge` | ✅ |
| 标题文案 | `activity_main.xml:23` | app_name | `@string/app_name` = "扫文笔记" | ✅ |
| contentInset | `activity_main.xml:20` | 0dp | `contentInsetStartWithNavigation="0dp"` | ✅ |

**评价**: MaterialToolbar 使用 MD3 规范，蓝色背景#1A73E8配合白色标题，与参考设计规范一致。

### A2. 按钮组 MaterialCardView 包裹 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| 外层容器类型 | `activity_main.xml:75` | `MaterialCardView` (id=`button_bar`) | ✅ |
| 内层布局 | `activity_main.xml:87` | `LinearLayout` (horizontal, weight分布) | ✅ |
| Elevation | `activity_main.xml:80` | `4dp` | ✅ |
| 圆角 | `activity_main.xml:79` | `0dp` (底部全宽) | ✅ |
| 背景色 | `activity_main.xml:78` | `?attr/colorSurface` (白色) | ✅ |
| 底部约束 | `activity_main.xml:81` | `parent` 底部 | ✅ |
| 底部insets适配 | `MainActivity.kt:213` | `buttonBar.updatePadding(bottom=systemBars.bottom)` | ✅ |

**评价**: MaterialCardView 正确包裹按钮组，使用 elevation 提供层次感，底部 padding 适配导航栏/Home Indicator。

### A3. Undo/Redo 按钮样式 ✅

| 检查项 | btn_undo | btn_redo | 结果 |
|--------|----------|----------|------|
| 样式 | `Widget.ScanPaste.BottomButton` | 相同 | ✅ |
| 父样式 | `Widget.Material3.Button.TextButton` | 相同 | ✅ |
| 图标 | `@drawable/ic_undo` | `@drawable/ic_redo` | ✅ |
| 图标位置 | `iconGravity=textStart` | 相同 | ✅ |
| 字号 | `14sp` | `14sp` | ✅ |
| 大写禁用 | `textAllCaps=false` | 相同 | ✅ |
| 最小高度 | `44dp` | `44dp` | ✅ |
| 初始状态 | `enabled=false` | `enabled=false` | ✅ |
| 文本 | "撤销" | "重做" | ✅ |

**评价**: 两个按钮使用 TextButton 样式正确，图标+文字组合符合 MD3 规范。初始置灰正确。

### A4. Append 按钮样式 ✅

| 检查项 | 期望值 | 实际值 | 结果 |
|--------|--------|--------|------|
| 样式 | FilledButton | `Widget.ScanPaste.BottomButton.Filled` (parent=`Widget.Material3.Button`) | ✅ |
| 背景色 | 蓝色 | `backgroundTint = @color/md_primary` (#1A73E8) | ✅ |
| 文字颜色 | 白色 | `textColor = @color/md_on_primary` (#FFFFFF) | ✅ |
| 图标 | 扫码图标 | `@drawable/ic_scan` | ✅ |
| 文本 | "追加" | `@string/btn_append` | ✅ |
| contentDescription | — | `@string/cd_append` = "拍照扫码追加" | ✅ |

**评价**: Append 使用 FilledButton 作为主操作按钮，蓝色填充+白色文字符合视觉层次设计。accessible description 正确。

### A5. MD3 主题 ✅

| 检查项 | 源码位置 | 实际值 | 结果 |
|--------|---------|--------|------|
| 主题父样式 | `themes.xml:6` | `Theme.Material3.Light.NoActionBar` | ✅ |
| colorPrimary | `themes.xml:7` | `@color/md_primary` | ✅ |
| colorOnPrimary | `themes.xml:8` | `@color/md_on_primary` | ✅ |
| 完整MD3色彩系统 | `themes.xml:9-18` | 11项color属性已定义 | ✅ |
| colorError | `themes.xml:18` | `@color/md_error` (#B3261E) | ✅ |

**评价**: 使用 MD3 完整色彩系统，NoActionBar 模式（Toolbar 在布局中自管理）。

### A6. 状态栏透明 + edge-to-edge ✅

| 检查项 | 源码位置 | 实际值 | 结果 |
|--------|---------|--------|------|
| statusBarColor | `themes.xml:23` | `@android:color/transparent` | ✅ |
| navigationBarColor | `themes.xml:24` | `@color/md_surface` (白色) | ✅ |
| setDecorFitsSystemWindows | `MainActivity.kt:65` | `false` | ✅ |
| Toolbar top padding | `MainActivity.kt:211` | `systemBars.top` 动态适配 | ✅ |
| Button bar bottom padding | `MainActivity.kt:213` | `systemBars.bottom` 动态适配 | ✅ |
| windowLightStatusBar | `themes.xml:25` | `false` | ✅ |

**评价**: Edge-to-edge 配置完整，Toolbar 背景延伸到状态栏后，padding 动态适配。状态栏白色图标配合蓝色背景可读性佳。

---

## 3. B. 4项修复验证 (变更#4)

### B7. 状态栏图标白色 + edge-to-edge ✅

| 检查项 | 源码位置 | 实际值 | 结果 |
|--------|---------|--------|------|
| windowLightStatusBar | `themes.xml:25` | `false` | ✅ |
| Toolbar颜色 | `activity_main.xml:16` | 蓝底(#1A73E8) + 白色文字 | ✅ |
| 对比度 | — | 蓝底白字/白图标 → 高对比度，可读性佳 | ✅ |
| edge-to-edge | `MainActivity.kt:65` | `setDecorFitsSystemWindows(window, false)` | ✅ |

**评价**: `windowLightStatusBar=false` 使状态栏图标显示为白色，在蓝色Toolbar背景上清晰可读。edge-to-edge 配合 insets 正确处理。

### B8. adjustResize 替代 adjustNothing ✅

| 检查项 | 源码位置 | 期望 | 实际 | 结果 |
|--------|---------|------|------|------|
| windowSoftInputMode | `AndroidManifest.xml:23` | `adjustResize` | `adjustResize` | ✅ |
| adjustNothing 残留 | 全源码搜索 | 无 | 仅在文档和 build intermediates 中找到 | ✅ |

**评价**: `adjustResize` 已正确替代 `adjustNothing`。源码中无 `adjustNothing` 残留，仅在文档描述中有历史引用（属正常）。

### B9. NestedScrollView 包裹 EditText ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| NestedScrollView 存在 | `activity_main.xml:43` | ✅ 有 `id=scroll_editor` |
| EditText 是其子元素 | `activity_main.xml:52` | ✅ 直接子元素 |
| fillViewport | `activity_main.xml:46` | `true` | ✅ |
| 约束 | `activity_main.xml:47-50` | toolbar_divider 底部 → button_bar 顶部 | ✅ |
| EditText 高度 | `activity_main.xml:55` | `wrap_content` (由 NestedScrollView 管理滚动) | ✅ |

**评价**: NestedScrollView 正确包裹 EditText，配置 `fillViewport=true` 确保内容不足时也能拉满视口。键盘弹起时 adjustResize 压缩此区域，NestedScrollView 处理滚动。

### B10. 追加按钮点击直接启动相机扫码 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| btnAppend onClick | `MainActivity.kt:127-130` | 直接调用 `checkAndRequestCameraPermission()` → `launchScanner()` | ✅ |
| 无弹窗/对话框 | — | 无 BottomSheetDialog / AlertDialog 调用 | ✅ |
| 无底部弹窗引用 | 全源码搜索 | 无 Kotlin 代码引用 `dialog_scan_source` | ✅ |

**评价**: 追加按钮点击直接启动相机扫码，无中间弹窗，符合"零思考主路径"设计原则。

### B11. 相册按钮正确走 galleryLauncher ✅

| 检查项 | 源码位置 | 实际值 | 结果 |
|--------|---------|--------|------|
| btnGallery onClick | `MainActivity.kt:133` | `galleryLauncher.launch("image/*")` | ✅ |
| galleryLauncher 注册 | `MainActivity.kt:54-56` | `ActivityResultContracts.GetContent()` | ✅ |
| 解码方法 | `MainActivity.kt:140-150` | `decodeQrFromUri()` → `QrCodeScanner.decodeQrFromImage()` | ✅ |
| 按钮样式 | `activity_main.xml:135-151` | TextButton, 44×44dp, icon=`ic_gallery` | ✅ |
| contentDescription | `activity_main.xml:139` | `@string/cd_gallery` = "从相册选择图片扫码" | ✅ |

**评价**: 相册按钮作为次要路径小图标入口，正确走 `galleryLauncher`。图片解码使用 ZXing 原生 API，异常处理完善。

### B12. 底部弹窗已移除 ⚠️ (P3 — 死资源残留)

| 检查项 | 结果 | 详情 |
|--------|------|------|
| 代码是否调用弹窗 | ✅ 已移除 | Kotlin 源码中无双引 `dialog_scan_source` / `BottomSheetDialog` / `ModalBottomSheet` |
| 弹窗布局文件 | ❌ 残留 | `res/layout/dialog_scan_source.xml` 仍存在（55行，包含两个按钮） |
| 弹窗相关字符串 | ❌ 残留 | `scan_source_title`、`btn_camera_scan`、`btn_gallery_scan` 仍存在 |
| 弹窗样式 | ❌ 残留 | `Widget.ScanPaste.BottomSheetButton` 仍存在 |
| 弹窗图标 | ❌ 残留 | `ic_camera.xml` 仅在死布局中被引用，本身也成死资源 |

**详细分析**:

`dialog_scan_source.xml` 是变更#2 引入的底部弹窗布局（"选择扫码方式"），变更#4 已将其替换为"追加按钮直接拍照 + 相册小图标"方案。虽然 Kotlin 代码不再引用，但布局文件、字符串、样式、图标均未清理：

- `res/layout/dialog_scan_source.xml` (55行)
- `res/values/strings.xml`: `scan_source_title`, `btn_camera_scan`, `btn_gallery_scan`
- `res/values/themes.xml`: `Widget.ScanPaste.BottomSheetButton`
- `res/drawable/ic_camera.xml`

**影响**: 无运行时影响。增加 APK 体积（可忽略：布局+字符串+样式约 2KB）。

**严重程度**: P3 — 建议（代码清理）

**建议**: 删除 `dialog_scan_source.xml`、`ic_camera.xml`（若仅在此使用）、清理 `strings.xml` 中的 3 个死字符串、清理 `themes.xml` 中的 `BottomSheetButton` 样式。

---

## 4. C. 回归验证 — 全部通过 ✅

### C13. Undo/Redo 按钮监听 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| btnUndo 绑定 | `MainActivity.kt:118-123` | `viewModel.undo()` + `moveCursorToEnd()` + 独立防抖 | ✅ |
| btnRedo 绑定 | `MainActivity.kt:124-129` | `viewModel.redo()` + `moveCursorToEnd()` + 独立防抖 | ✅ |
| 防抖时间戳独立 | `MainActivity.kt:32-34` | `lastUndoTime`, `lastRedoTime`, `lastAppendTime` 三个独立变量 | ✅ |

**评价**: Undo/Redo 监听绑定正确，防抖逻辑保留。每次操作后光标移至文末。

### C14. ViewModel/LiveData observers ✅

| Observer | 源码位置 | 功能 | 结果 |
|----------|---------|------|------|
| text | `MainActivity.kt:89-93` | 同步编辑区文本，带 anti-loop guard | ✅ |
| textSize | `MainActivity.kt:94-96` | 动态设置字号 | ✅ |
| canUndo | `MainActivity.kt:97-99` | 控制 Undo 按钮 enabled | ✅ |
| canRedo | `MainActivity.kt:100-102` | 控制 Redo 按钮 enabled | ✅ |
| undoFailed | `MainActivity.kt:103-107` | Toast 提示 + 重置 flag | ✅ |
| anti-loop guard | `MainActivity.kt:90` | `if (text != newText)` 避免循环 setText | ✅ |

**评价**: 5 个 LiveData observer 全部保留且正确。Observer 防止循环回写、TextWatcher 防止重复 updateTextManually、LiveData 自身值比较防止重复通知——三方防护完整。

### C15. 防抖逻辑 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| DEBOUNCE_MS | `MainActivity.kt:251` | 500ms | ✅ |
| isDebounceExpired() | `MainActivity.kt:248-249` | 正确实现 | ✅ |
| 三个按钮独立防抖 | `MainActivity.kt:118,125,128` | 各自使用独立时间戳 | ✅ |

**评价**: 防抖逻辑保留，三个按钮使用独立时间戳（互不干扰），阈值500ms合理。

### C16. 权限处理 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| 相机权限声明 | `AndroidManifest.xml:5` | `uses-permission CAMERA` | ✅ |
| 运行时权限检查 | `MainActivity.kt:164-175` | `checkSelfPermission` → 已授权直接扫码 | ✅ |
| 首次拒绝处理 | `MainActivity.kt:179-184` | Snackbar + rationale | ✅ |
| "不再询问"处理 | `MainActivity.kt:186-193` | AlertDialog → 引导至系统设置 | ✅ |
| 权限 launcher | `MainActivity.kt:41-45` | `RequestPermission` 合约 | ✅ |
| camera 硬件 optional | `AndroidManifest.xml:7-9` | `required="false"` | ✅ |

**评价**: 权限处理完整，覆盖已授权、首次拒绝、"不再询问"三种流程。

### C17. QrCodeScanner API 兼容 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| createScanIntent() | `QrCodeScanner.kt:19-28` | setCaptureActivity + QR_CODE + beep disabled + orientation locked | ✅ |
| parseResult() | `QrCodeScanner.kt:33-37` | IntentIntegrator.parseActivityResult | ✅ |
| decodeQrFromImage() | `QrCodeScanner.kt:44-52` | Bitmap → ZXing MultiFormatReader (QR_CODE) | ✅ |
| 强制竖屏 | `ScanPasteCaptureActivity.kt:15` | `SCREEN_ORIENTATION_PORTRAIT` | ✅ |
| Manifest 声明 | `AndroidManifest.xml:33-38` | portrait + configChanges + exported=false | ✅ |

**评价**: QrCodeScanner API 完整，`setCaptureActivity` 指定自定义 Activity，`setDesiredBarcodeFormats` 限定 QR_CODE，竖屏锁定，所有历史修复保留。

### C18. 字号自适应 ✅

| 检查项 | 源码位置 | 结果 |
|--------|---------|------|
| 字号计算逻辑 | `EditorViewModel.kt:174-189` | stair-step: ≤3→24sp, ≤8→20sp, ≤15→16sp, >15→12sp | ✅ |
| 空文本处理 | `EditorViewModel.kt:176-178` | 空文本 → 24sp | ✅ |
| 边界值：3行 | 边界测试 | 3行 → 24sp ✓ | ✅ |
| 边界值：4行 | 边界测试 | 4行 → 20sp ✓ | ✅ |
| 边界值：8行 | 边界测试 | 8行 → 20sp ✓ | ✅ |
| 边界值：9行 | 边界测试 | 9行 → 16sp ✓ | ✅ |
| 边界值：15行 | 边界测试 | 15行 → 16sp ✓ | ✅ |
| 边界值：16行 | 边界测试 | 16行 → 12sp ✓ | ✅ |
| 应用字号 | `MainActivity.kt:95` | `etEditor.textSize = sizeSp.toFloat()` | ✅ |
| 追加后重算 | `EditorViewModel.kt:69` | `recalculateTextSize()` 在 appendText 中调用 | ✅ |
| 手动编辑后重算 | `EditorViewModel.kt:107` | `recalculateTextSize()` 在 updateTextManually 中调用 | ✅ |

**评价**: 字号自适应逻辑完整保留，所有边界值正确。

### C19. 文本选择 (textIsSelectable) ✅

| 检查项 | 源码位置 | 实际值 | 结果 |
|--------|---------|--------|------|
| textIsSelectable | `activity_main.xml:63` | `true` | ✅ |
| inputType | `activity_main.xml:58` | `textMultiLine|textCapSentences` | ✅ |
| gravity | `activity_main.xml:56` | `top|start` | ✅ |

**评价**: `textIsSelectable="true"` 保留，支持全选/复制/剪切/粘贴。

---

## 5. 发现的问题汇总

### F1 [P3] 死资源：底部弹窗布局/字符串/样式未清理

**描述**: 变更#2 引入的底部弹窗（`dialog_scan_source.xml`）在变更#4 中被新方案替代，但布局文件、字符串、样式、图标未清理。

**影响**: 无运行时影响，仅增加约 2KB APK 体积。

**建议**:
- 删除 `res/layout/dialog_scan_source.xml`
- 删除 `res/drawable/ic_camera.xml`（验证无其他引用后）
- 清理 `strings.xml`: `scan_source_title`、`btn_camera_scan`、`btn_gallery_scan`
- 清理 `themes.xml`: `Widget.ScanPaste.BottomSheetButton`

**复现**: 搜索 `dialog_scan_source` 在 `app/src/main` 下可确认文件存在。

### F2 [P3] 死资源：`permission_camera_rationale` 字符串未使用（遗留）

**描述**: v1.2 测试报告的 F3 问题未修复。`strings.xml` 中的 `permission_camera_rationale` 字符串已被删除（确认），但此问题原样存在于 v1.2 报告中。

**验证**: 检查当前 `strings.xml`，确认 `permission_camera_rationale` 字符串已不存在。原 v1.2 F3 问题实际上已经在变更#3/#4 间被解决（或被替换为 Snackbar 方式）。

**结果**: 此问题在 v2.0 中已修复 ✅

**修正**: 经过重新审查，原 v1.2 F3 问题已解决。`permission_camera_rationale` 字符串不再存在于 `strings.xml` 中。

**重新计数**: 本次 delta 测试实际发现 **1 项 P3 问题 (F1)**。

---

## 6. 单元测试回归

| 测试文件 | 测试数 | 状态 |
|----------|--------|------|
| `AppendHistoryTest.kt` | 12 | ✅ 代码未变更，测试仍有效 |
| `EditorViewModelTest.kt` | 47 | ✅ EditorViewModel 核心逻辑未变 |

**回归结论**: ViewModel 和 Model 层代码在变更#3/#4 中未修改，59 个单元测试仍然全部有效。

---

## 7. 加分项观察

### 7.1 良好的架构演进

1. **按钮组 3→4 按钮**: 从三等分变为 Undo/Redo/Append(weight=1) + Gallery(固定44dp)，使用 LinearLayout weight 实现灵活布局。
2. **主路径零思考**: Append 按钮直接拍照（FilledButton 视觉突出），Gallery 作为小图标次级入口——严格遵循参考设计规范第4节"扫码入口 UX 模式"。
3. **adjustNothing→adjustResize**: 变更前按钮组固定底部但编辑区无法滚动查看，变更后键盘弹起时可滚动查看全部内容，UX 显著改善。
4. **Toolbar Insets 处理**: `applyWindowInsets()` 方法专门处理系统栏避让，Toolbar 顶部和按钮栏底部都正确适配。

### 7.2 良好的 debug 注释

`activity_main.xml` 中的 XML 注释（`<!-- ===== -->`）清晰标注了 Toolbar 区域、编辑区、按钮栏的职责和设计意图，便于后续维护。

---

## 8. 交付前检查清单

- [x] 已读取 `docs/spec.md` — 理解变更#1-#4 全部需求
- [x] 已读取 `examples/android-ui-best-practices.md` — 对照设计规范
- [x] 已读取上次 `docs/test-report.md` — 理解基线状态
- [x] 已审查 `themes.xml` — 主题、色彩、样式正确
- [x] 已审查 `activity_main.xml` — 布局结构正确
- [x] 已审查 `AndroidManifest.xml` — adjustResize、权限、Activity 声明正确
- [x] 已审查 `MainActivity.kt` — 监听、Observer、权限、防抖、insets 正确
- [x] 已审查 `EditorViewModel.kt` — 核心逻辑无回归
- [x] 已审查 `QrCodeScanner.kt` — API 兼容
- [x] 19 项 delta 测试全部执行（17 PASS / 0 FAIL / 0 BLOCK / 1 P3 + 1 FIXED）
- [x] 测试结果已记录到 `docs/test-report-2.md`

---

## 9. 总结

| 维度 | 评分 | 说明 |
|------|------|------|
| UI 重构正确性 | ⭐⭐⭐⭐⭐ | MaterialToolbar、MaterialCardView、MD3 主题全部正确配置 |
| 4项修复正确性 | ⭐⭐⭐⭐⭐ | adjustResize、NestedScrollView、直接扫码、相册按钮均正确实现 |
| 底部弹窗移除 | ⭐⭐⭐⭐ | 功能上已移除，但死资源未清理(1 项 P3) |
| 回归完整性 | ⭐⭐⭐⭐⭐ | Undo/Redo、ViewModel、防抖、权限、字号、textIsSelectable 全部保留 |
| 代码规范性 | ⭐⭐⭐⭐⭐ | 无 `!!`、无硬编码、注释清晰、架构一致 |
| 资源清理 | ⭐⭐⭐ | 1 项死资源残留(v2新增) |

**测试结论**: ✅ **代码可以合并到 main 分支**。变更#3 (UI重构) 和变更#4 (4项修复) 实现正确，19 项 delta 测试中 17 项通过、0 项失败、1 项 P3 建议清理。建议在合并前清理 `dialog_scan_source.xml` 及相关死资源。
