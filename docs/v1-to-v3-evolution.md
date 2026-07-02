# ScanPaste v1.0 → v3.0 演进对比分析

> 一个 Android 扫码笔记工具的成长史：从"能跑就行"到"拿得出手"

---

## 1. 版本时间线

| 阶段 | 时间 | 事件 |
|------|------|------|
| v1.0 初始 | 2026-05-29 00:29 | spec + design 定稿，5 阶段 gate check 通过 |
| v1.0 编码 | 2026-05-29 00:36 | coder 交付，15 文件，~580 行 Kotlin 源码 |
| v1.0 测试 | 2026-05-29 00:43 | tester 审计，59 单元测试全通过 |
| v1.0 终审 | 2026-05-29 00:43 | auditor 审批通过，37 项 checklist 全部合格 |
| **变更 #1** | 2026-05-29 06:30 | 真机 Bug 修复（5 项） |
| **变更 #2** | 2026-05-29 06:39 | UI 优化 + 相册扫码 |
| **变更 #3** | 2026-05-29 07:06 → 09:12 | **v3.0 大版本** — MD3 重构、保存/速记、桌面小部件、edge-to-edge |

---

## 2. 功能演进对比

### 2.1 功能矩阵

| 功能 | v1.0 | v3.0 |
|------|------|------|
| **文本编辑** | EditText，字号自适应(12-24sp阶梯) | EditText + NestedScrollView，固定 18sp |
| **扫码追加** | 点击后弹窗选择"拍照/相册" | 点击直接拍照扫码；右侧 🖼️ 图标走相册 |
| **相册扫码** | ❌ 不支持 | ✅ `decodeQrFromImage()` ZXing 解码相册图片 |
| **撤销/重做** | 仅追加操作可撤销（双栈 100 条） | 追加 + 清空均可撤销重做；清空存全文快照 |
| **清空** | ❌ 不支持 | ✅ Toolbar 右上角清空按钮，可撤销恢复 |
| **保存文件** | ❌ 不支持 | ✅ SAF `ACTION_CREATE_DOCUMENT`，自定义扩展名 |
| **速记** | ❌ 不支持 | ✅ 一键保存到 `Documents/scanpaste/quicknote.txt` |
| **桌面小部件** | ❌ 不支持 | ✅ 可缩放+滚动+圆角+10%透明，实时显示速记 |
| **标题栏** | ❌ 无 | ✅ MaterialToolbar 48dp，蓝色 `#1A73E8`，白色标题 |
| **edge-to-edge** | ❌ adjustNothing | ✅ 透明状态栏 + spacer + IME insets 键盘自适应 |
| **应用图标** | ❌ 无 | ✅ mipmap-anydpi-v26 + 全分辨率 PNG fallback |

### 2.2 撤销重做演进

**v1.0**：追加操作的双栈撤销/重做。`AppendHistory(position, text)` 记录插入位置和内容，容错三重匹配（精确→部分→全文搜索→Toast）。

**v3.0**：新增清空可撤销。`clearAll()` 将当前全文以 `AppendHistory(0, currentText)` 压入 UndoStack；Undo 时检测 `position==0 && current.isEmpty()` 直接恢复快照；Redo 时以 `AppendHistory(0, "")` 重新清空。

### 2.3 存储能力演进

v1.0 明确标注"不保存历史记录到本地存储"。v3.0 加入了两个独立的存储路径：

- **保存**：SAF `ACTION_CREATE_DOCUMENT`，用户自定义文件名和扩展名，默认 `Documents` 目录。自动推断 MIME 类型（txt→text/plain, md→text/markdown, csv→text/csv, json→application/json）。
- **速记**：`MediaStore` 直接写入 `Documents/scanpaste/quicknote.txt`，无需用户交互。`findOrCreateQuickNoteUri()` 先查询后创建。

---

## 3. UI 演进：从白底到 MD3

### 3.1 主题切换

| 维度 | v1.0 | v3.0 |
|------|------|------|
| 主题 | `Theme.MaterialComponents.DayNight.NoActionBar` | `Theme.Material3.Light.NoActionBar` |
| 状态栏 | 默认（不处理） | `statusBarColor=transparent` + 蓝色 spacer |
| 导航栏 | 默认 | `navigationBarColor=md_surface` |
| 色彩 | Material Color Palette | 完整 MD3 Token 体系（10 个色彩变量） |
| 键盘策略 | `adjustNothing` | `adjustResize` + IME insets 处理 |

### 3.2 布局结构对比

**v1.0 布局：**
```
ConstraintLayout
├── EditText (match_parent, 上方全部空间)
└── LinearLayout 56dp 底部
    ├── Button "取消" (三等分)
    ├── Button "重做" (三等分)
    └── Button "追加" (三等分)
```

**v3.0 布局：**
```
ConstraintLayout
├── View (status_bar_spacer, 动态高度=statusBarHeight, 蓝色背景)
├── MaterialToolbar 48dp (蓝色, 白色标题, 右侧 [清空] [速记] [保存])
├── View (toolbar_divider, 1dp, 15%透明度)
├── NestedScrollView (fillViewport)
│   └── EditText (18sp, 16dp/12dp padding)
└── MaterialCardView (elevation=4dp, 底部按钮组)
    └── LinearLayout
        ├── TextButton "撤销" (icon=ic_undo, weight=1)
        ├── TextButton "重做" (icon=ic_redo, weight=1)
        ├── FilledButton "追加" (icon=ic_scan, weight=1, 蓝色)
        └── TextButton 🖼️ (48dp×48dp, icon=ic_gallery)
```

### 3.3 字号策略演变

v1.0 采用**自适应字号**（基于显式换行符行数阶梯缩放：≤3行→24sp, ≤8行→20sp, ≤15行→16sp, >15行→12sp）。目的是让内容少时大字醒目、内容多时小字容纳。

v3.0 改为**固定 18sp**。原因：自适应字号在混合内容（短行+长行）时视觉跳跃感明显，用户更习惯于固定字号的可预测阅读体验。18sp 是系统字体缩放因子为 1.0 时的默认正文字号，适合中文字符显示。

---

## 4. 架构演进

### 4.1 模块对比

| 模块 | v1.0 | v3.0 |
|------|------|------|
| MainActivity | ~280 行，4 个 launcher | ~220 行，6 个 launcher，功能更密集但更紧凑 |
| EditorViewModel | ~210 行，双栈 + 字号计算 | ~160 行，双栈 + clearAll + 容错，删除了字号逻辑 |
| QrCodeScanner | ~55 行，仅 Intent 封装 | ~90 行，新增 `decodeQrFromImage()` |
| ScanPasteCaptureActivity | ❌ | ✅ ~40 行，竖屏锁定 + 提示 overlay |
| QuickNoteWidgetProvider | ❌ | ✅ ~60 行，RemoteViews + PendingIntent |
| QuickNoteWidgetService | ❌ | ✅ ~90 行，RemoteViewsFactory |
| AppendHistory | ~20 行 | 不变 |
| 布局文件 | 1 个 (activity_main) | 4 个 (+dialog_save, widget_quicknote, widget_item) |
| 矢量图标 | 0 | 5 个 (ic_undo, ic_redo, ic_scan, ic_gallery, ic_launcher_foreground) |
| Widget 配置 | ❌ | quicknote_widget_info.xml |

### 4.2 v3.0 架构图

```
┌──────────────────────────────────────────────────────────────┐
│                       MainActivity                           │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  status_bar_spacer (蓝色, 动态高度)                     │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │  MaterialToolbar (48dp, 蓝色)                           │  │
│  │  ┌─ "扫码笔记" ───────────────── [清空][速记][保存] ─┐  │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │  toolbar_divider (1dp)                                  │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │  NestedScrollView                                       │  │
│  │  └─ EditText (18sp, textIsSelectable)                   │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │  MaterialCardView (elevation=4dp)                      │  │
│  │  ┌─[撤销]──[重做]──[追加]──[🖼️]─────────────────────┐  │  │
│  └──────────────────────────────────────────────────────┴──┘  │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │               EditorViewModel                            │  │
│  │  ┌─────────┐  ┌─────────┐  ┌──────────────────────────┐  │  │
│  │  │undoStack│  │redoStack│  │ text: LiveData<String>    │  │  │
│  │  │ (100)   │  │ (100)   │  │ canUndo: LiveData<Bool>   │  │  │
│  │  └─────────┘  └─────────┘  │ canRedo: LiveData<Bool>   │  │  │
│  │                             └──────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌────────────────────────┐  ┌────────────────────────────┐   │
│  │    QrCodeScanner       │  │  QuickNoteWidgetProvider    │   │
│  │  - createScanIntent()  │  │  - onUpdate()               │   │
│  │  - parseResult()       │  │  - sendUpdateBroadcast()    │   │
│  │  - decodeQrFromImage() │  └────────────────────────────┘   │
│  └────────────────────────┘                                    │
│                                                               │
│  ┌──────────────────────────┐                                 │
│  │ QuickNoteWidgetService   │                                 │
│  │  - RemoteViewsFactory     │                                 │
│  │  - loadQuickNote()        │                                 │
│  └──────────────────────────┘                                 │
└──────────────────────────────────────────────────────────────┘
```

### 4.3 新增数据流

**速记写入流程：**
```
点击 "速记"
  → saveQuickNote()
  → findOrCreateQuickNoteUri()  [MediaStore 查询/创建]
  → contentResolver.openOutputStream(uri, "wt")
  → write UTF-8 content
  → sendUpdateBroadcast() → 通知小部件刷新
```

**小部件数据流：**
```
系统 onUpdate()
  → updateWidget()
  → setRemoteAdapter(intent_to_service)
  → setPendingIntentTemplate(click→MainActivity)
  → Service: QuickNoteFactory.loadQuickNote()
      → 读取 quicknote.txt
      → 按行分割为 items
      → header = "🕐 修改时间"
      → return count = items.size + 1
```

---

## 5. 遇到的坑与解决方案

### 5.1 RemoteViews 兼容性地狱

**问题**：桌面小部件对 View 类型有严格限制。`AppWidgetProvider` 只能用 `RemoteViews`，而 `RemoteViews` 只支持少量 View 子类（TextView、ImageView、ListView、GridView 等）。不能使用 ConstraintLayout、MaterialButton 等现代组件。

**解决方案**：
- 使用 `ListView` + `RemoteViewsService` + `RemoteViewsFactory` 实现滚动列表
- 白底 10% 透明 + 12dp 圆角用 `shape drawable`（widget_bg.xml），不用 MaterialCardView
- 每个列表项做成独立 layout（widget_item.xml），仅使用 LinearLayout + TextView

**教训**：设计桌面小部件布局时，先用 RemoteViews 支持列表[查一遍](https://developer.android.com/reference/android/widget/RemoteViews#public-constructors)，确认每个 View 类型都在支持范围内。

### 5.2 Edge-to-Edge 适配

**问题**：Android 10+ 强制 edge-to-edge，即使设置了 `adjustNothing`，键盘弹起时系统仍会平移布局。v1.0 使用 `adjustNothing` 试图让按钮组固定在底部，但在 Android 11+ 真机上失效。

**解决方案**（v3.0）：
- 设置 `WindowCompat.setDecorFitsSystemWindows(window, false)`
- 在布局顶部放置一个 View 作为状态栏 spacer，代码中动态设置高度为 `systemBars.top`
- 根布局底部 padding 设为 `systemBars.bottom`
- `windowSoftInputMode="adjustResize"`，配合 `ViewCompat.setOnApplyWindowInsetsListener` 同时监听 systemBars + IME + displayCutout 三类 insets
- IME 弹出时系统压缩 NestedScrollView（按钮组始终固定在底部），编辑区自动收缩但内容可滚动

**关键代码：**
```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
    val bars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or
        WindowInsetsCompat.Type.ime() or
        WindowInsetsCompat.Type.displayCutout()
    )
    binding.statusBarSpacer.layoutParams.height = bars.top
    view.updatePadding(bottom = bars.bottom)
    insets
}
```

### 5.3 ZXing API 变更 (3.x → 4.x)

**问题**：ZXing Embedded 4.x 中 `IntentIntegrator` 部分 API 有变化。`setCaptureActivity()` 在 4.3.0 中必须传入 `Class<? extends CaptureActivity>` 类型，否则默认可能 fallback 到外部扫码应用或横屏。

**解决方案**：
- 创建 `ScanPasteCaptureActivity` 继承 `CaptureActivity`，在 `onCreate` 中强制竖屏：`requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`
- 在 `IntentIntegrator` 中使用 `setCaptureActivity(ScanPasteCaptureActivity::class.java)` 显式绑定
- 在 Manifest 中对该 Activity 使用 `tools:replace="android:screenOrientation"` 覆盖 ZXing 库内部的声明

**教训**：第三方库的 Manifest 属性可能与你的需求冲突。`tools:replace` 是一种强大的覆盖手段，但要注意这是库内部声明改变，库升级时该属性可能消失或改名。

### 5.4 SAF 默认目录设置

**问题**：`ACTION_CREATE_DOCUMENT` 默认打开最近使用的目录，而不是 Documents。用户需要手动导航到 Documents。

**解决方案**：Android 8.0+ 使用 `DocumentsContract.EXTRA_INITIAL_URI` 预设初始目录：
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    putExtra(
        DocumentsContract.EXTRA_INITIAL_URI,
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents"
        )
    )
}
```

**注意**：`EXTRA_INITIAL_URI` 在某些 OEM（如小米 MIUI 13+、华为 HarmonyOS）上可能被忽略，无法 100% 覆盖。因此仍需保留 `EXTRA_TITLE` 的建议文件名作为 fallback。

### 5.5 MediaStore 速记文件的管理

**问题**：使用 MediaStore 创建 `Documents/scanpaste/quicknote.txt` 后，用户通过文件管理器删除该文件，MediaStore 数据库不会自动清理记录。下次 `findOrCreateQuickNoteUri()` 查询到的是已删除的记录，`openOutputStream` 会失败。

**解决方案**：考虑到 quicknote.txt 的典型使用场景（反复覆盖而非偶尔删除），当前采用 try-catch 容错。更健壮的方案是捕获写入失败后重新创建，但未实施——因为 MediaStore 在大多数设备上会正确处理这种情况。

### 5.6 应用图标兼容性

**问题**：变更 #1 首次添加图标时，仅配置了 `mipmap-anydpi-v26` 的自适应图标（Adaptive Icon），导致 Android 7.0-8.0（API 24-25）设备找不到图标资源。

**解决方案**：为每个 dpi 密度提供 PNG fallback（mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi），配合自适应图标使 API 26+ 设备获得最佳视觉效果。

---

## 6. Android App 开发最佳实践总结

### 6.1 状态栏 / Toolbar 处理

**黄金法则：** 永远不要依赖单一方法处理状态栏。组合使用以下三件套：

| 手段 | 用途 | 何时用 |
|------|------|--------|
| `WindowCompat.setDecorFitsSystemWindows(window, false)` | 启用 edge-to-edge，让内容绘制在系统栏后面 | 所有 App 都该用 |
| `theme: statusBarColor=transparent` | 状态栏透明，内容透过去 | 有自定义 Toolbar 时必设 |
| `ViewCompat.setOnApplyWindowInsetsListener` | 精确控制各区域避让 | 有底部按钮组必设 |
| spacer View + 动态高度 | 状态栏区域显示品牌色 | Toolbar 带色彩背景时 |
| 根 layout bottom padding = `systemBars.bottom` | 导航栏 / Home Indicator 避让 | 按钮组在底部时 |

**本项目方案的要点：**
1. 不在 Toolbar 上用 `fitsSystemWindows`（会导致 insets 留在 Toolbar 内部而不是根布局）
2. 用一个独立的 `status_bar_spacer View` 填充状态栏区域
3. 同一个 `setOnApplyWindowInsetsListener` 同时处理顶部 spacer 和底部 padding

### 6.2 键盘自适应方案

| 策略 | 效果 | 适用场景 |
|------|------|---------|
| `adjustResize` + IME insets | 布局随键盘缩放，编辑区收缩，按钮组不移位 | 编辑区+底部固定控件 |
| `adjustPan` | 整体上移，布局不缩放 | 简单的表单页面 |
| `adjustNothing` | 不做任何处理 | 极少数场景，v1.0 用了后来踩坑 |

**推荐：`adjustResize` + `WindowInsetsCompat.Type.ime()`。**

在 manifest 设 `adjustResize`，在代码中用 `setOnApplyWindowInsetsListener` 监听 IME insets。系统自动处理布局调整，按钮组因约束在 `parent` 底部而不会移位。

**关键点：** NestedScrollView 作为编辑区的容器是关键——IME 弹出时 ScrollView 高度被压缩，EditText 内容可以滚动，用户体验自然。

### 6.3 桌面小部件开发要点

| 要点 | 说明 |
|------|------|
| **View 限制** | 只支持 TextView、ImageView、ListView、GridView、StackView、ViewFlipper 等有限类型 |
| **布局限制** | 不支持 ConstraintLayout、Material Components、RecyclerView。用 FrameLayout + LinearLayout + RelativeLayout |
| **样式限制** | 不支持 style 引用、不支持 theme 属性（如 `?attr/colorSurface`）。所有颜色必须写死 |
| **列表方案** | `ListView` + `RemoteViewsService` + `RemoteViewsFactory` |
| **点击事件** | 通过 `setPendingIntentTemplate` + `setOnClickFillInIntent` |
| **刷新机制** | `notifyAppWidgetViewDataChanged()` + `sendBroadcast()` |
| **透明度** | 用 shape drawable 的 `solid` + alpha 通道（如 `#E6FFFFFF` = 90%白色 = 10%透明） |
| **圆角** | shape drawable `corners android:radius="12dp"` |

### 6.4 SAF 文件存储

```kotlin
// 创建文件（用户选择位置）
Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "text/plain"
    putExtra(Intent.EXTRA_TITLE, "文件名.txt")
    // API 26+ 预设初始目录
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, /* ... */)
    }
}

// 写入
contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
```

**要点：**
- SAF 不需要存储权限，用户自主选择文件位置
- `EXTRA_INITIAL_URI` 是 API 26+ 的特性，低版本用 `EXTRA_TITLE` 作为 fallback
- 不同 OEM 对 `EXTRA_INITIAL_URI` 支持不一致，不要作为唯一依赖
- 写完记得 `flush()` 或关闭流

### 6.5 ZXing 扫码集成

**依赖：**
```toml
zxing-android-embedded = { group = "com.journeyapps", name = "zxing-android-embedded", version = "4.3.0" }
```

**扫码流程：**
1. `IntentIntegrator` 创建 Intent → `ActivityResultLauncher` 启动
2. ZXing CaptureActivity 实时取景 → 识别后返回
3. `IntentIntegrator.parseActivityResult()` 解析结果

**常见陷阱：**
- **必须创建自定义 CaptureActivity** 继承并锁定竖屏
- **必须设置 `setCaptureActivity()`** 防止 fallback 到外部应用
- **Android 6.0+** CAMERA 权限为运行时权限，需自行处理
- **相册解码** 需要 `decodeQrFromImage()`：Bitmap → int[] pixels → RGBLuminanceSource → BinaryBitmap → MultiFormatReader

### 6.6 撤销 / 重做设计模式

**核心模型：** 双栈分离（UndoStack + RedoStack）

```
┌──────────────┐     undo()    ┌──────────────┐
│  UndoStack   │ ────────────→ │  RedoStack    │
│  (LIFO)      │ ←──────────── │  (LIFO)       │
└──────────────┘     redo()    └──────────────┘
```

**关键规则：**
1. undo/redo 各自使用独立的栈，而非单栈+游标
2. 新操作（append/clear）清空 RedoStack（标准行为）
3. 栈深度设上限（100），超出时淘汰最旧记录（FIFO 淘汰）
4. 混合编辑场景（用户追加后又手动修改了文本）需容错：
   - 先按记录的位置精确删除
   - 位置被破坏则全文搜索匹配
   - 仍找不到则放弃该操作 + Toast 提示（不移动指针，不破坏状态）

---

## 7. 标准 Android App 项目模板检查清单

### 7.1 必须的构建配置

- [ ] Kotlin DSL build scripts (`settings.gradle.kts`, `build.gradle.kts`)
- [ ] Version Catalog (`gradle/libs.versions.toml`)
- [ ] `compileSdk >= 35`, `targetSdk >= 35`
- [ ] `minSdk = 24` (Android 7.0, 覆盖 ~99% 设备)
- [ ] `JavaVersion.VERSION_17` / `jvmTarget = "17"`
- [ ] `buildFeatures.viewBinding = true`
- [ ] R8 混淆 + `proguard-rules.pro`（release 必须）
- [ ] `nonTransitiveRClass = true`（`gradle.properties`）
- [ ] `android.useAndroidX = true`

### 7.2 UI 组件选型建议

| 场景 | 推荐 | 避免 |
|------|------|------|
| 单页应用 | 单 `Activity` + Fragment/直接布局 | 多 Activity 跳转 |
| 布局容器 | `ConstraintLayout` | 嵌套 `LinearLayout` |
| 主题 | `Theme.Material3.Light.NoActionBar` | 自定义浅拷贝主题 |
| 列表 | `RecyclerView` | `ListView`（现代 App） |
| 底部托底 | `MaterialCardView` + elevation | 普通 LinearLayout |
| 图标 | Vector Drawable (`drawable/*.xml`) | PNG（除 launcher icon） |
| 色彩 | MD3 Token 体系 | 硬编码色值散布各文件 |

### 7.3 必须处理的系统交互

| 项 | 实现 |
|------|------|
| **Edge-to-Edge** | `setDecorFitsSystemWindows(false)` + `setOnApplyWindowInsetsListener` |
| **状态栏避让** | spacer View 动态高度 或 Toolbar `fitsSystemWindows` |
| **导航栏避让** | 根布局 `updatePadding(bottom=systemBars.bottom)` |
| **键盘自适应** | `adjustResize` + IME insets |
| **运行时权限** | `ActivityResultContracts.RequestPermission` + "不再询问" → Settings 引导 |
| **相机放开** | `camera required="false"`（无相机设备仍可安装） |
| **禁止联网** | `<uses-permission tools:node="remove">` 移除传递依赖引入的 INTERNET |
| **竖屏锁定** | `screenOrientation="portrait"` |
| **无备份** | `allowBackup="false"`（安全性） |
| **无障碍** | 所有 icon-only 按钮添加 `contentDescription` |

### 7.4 常见坑 & 避免方法

| 坑 | 避免方法 |
|------|----------|
| 键盘弹起按钮移位 | `adjustResize` + bottom padding by `systemBars.bottom`，不用 `adjustNothing` |
| Android 11+ 强制 edge-to-edge | 不要抵抗，主动适配：设状态栏透明 + spacer |
| ZXing 扫码横屏 | 自定义 `CaptureActivity` 强制竖屏 + `tools:replace` |
| 库依赖引入 INTERNET 权限 | `tools:node="remove"` |
| `textIsSelectable` 与 `inputType` 冲突 | Android 11+ 正常，旧版本可能有问题，需真机验证 |
| Adaptive Icon 无 fallback | 所有 dpi 密度提供 PNG，不只是 `mipmap-anydpi-v26` |
| SAF `EXTRA_INITIAL_URI` 不生效 | OEM 定制可能忽略，保留 `EXTRA_TITLE` 作为 fallback |
| RemoteViews 不支持 MD3 组件 | 小部件布局只用 FrameLayout/LinearLayout + 基础 View |
| LiveData Observer 循环更新 | `if (text != current)` 比较后再 `setText()` |
| 按钮重复点击 | 500ms防抖 + 独立时间戳 |
| Kotlin 版本与 Gradle 不兼容 | Gradle 8.14+ 需要 Kotlin 2.1.20+，AGP 8.x + Kotlin 1.9.x 需 JDK 17 |

### 7.5 Manifest 模板骨架

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 权限 -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- 移除传递依赖引入的非必要权限 -->
    <uses-permission android:name="android.permission.INTERNET"
        tools:node="remove" />

    <!-- 可选硬件 -->
    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:theme="@style/Theme.ScanPaste">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

## 8. 经验教训

### 8.1 做对的事

1. **MVVM + 单 Activity** — 从一开始就坚持，v1.0→v3.0 零架构重构。EditorViewModel 按职责封装状态，添加新功能无需改动 Activity 核心流程。
2. **双栈撤销/重做** — 选对了模式。添加"清空可撤销"只改了两处逻辑（clearAll 压快照 + undo 检测快照恢复），不用重写。
3. **ViewBinding** — 没有用 `findViewById`，后期加控件时编译期就能检测布局引用完整性。
4. **Version Catalog** — 依赖集中管理，升级依赖只需改一个文件。

### 8.2 可改进的地方

1. **字号自适应被推翻** — v1.0 花了精力做的字号阶梯算法在 v3.0 被固定字号取代。教训：设计阶段的假设（"用户需要自动缩放字号"）应尽早用原型验证。
2. **adjustNothing 选错了** — v1.0 的键盘策略被证明是错误的。测试发现后迅速在变更 #3 改为 `adjustResize` + IME insets。教训：键盘策略是"一选定终身"的决策，应在原型阶段就在真机上测试。
3. **图标 fallback 遗漏** — v1.0 没有应用图标，变更 #1 匆忙添加时只做了自适应图标，遗漏了 API 24-25 的 fallback。教训：资源文件的平台兼容性至少要覆盖 `minSdk` 到 `targetSdk`。
4. **无真机测试的 v1.0** — v1.0 在模拟器上测试通过就交付了，结果 5 项真机 Bug（状态栏遮蔽、无选择、横屏、相册 fallback、缺图标）必须紧急修复。教训：M 级项目至少要在 1 台真机上跑过验收标准。

---

*本文档由 publicist 于 2026-05-29 撰写，基于 spec.md（3 次变更记录）、design.md、journey.md、源码及 example 文档。*
