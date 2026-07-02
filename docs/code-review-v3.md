# ScanPaste v3.0 代码审查报告

> 审查者: coder (subagent) | 日期: 2026-05-29
> 审查范围: 全部 v3.0 源码 (10 个文件)
> 对照基准: `docs/design.md` v1.0

---

## 发现汇总

| 严重度 | 数量 | 说明 |
|--------|------|------|
| P0 阻塞 | 0 | — |
| P1 严重 | 4 | 功能缺失、架构不一致 |
| P2 改进 | 8 | 健壮性、可维护性问题 |
| P3 建议 | 4 | 代码风格、微优化 |

---

## P1 — 严重问题

### P1-1: 缺失字号自适应功能

**严重度**: P1 | **文件**: `EditorViewModel.kt`, `MainActivity.kt`
**位置**: `EditorViewModel` 类体 (全局缺失)

**问题**: 设计文档 §5 明确定义了字号自适应算法（基于行数的阶梯缩放到 24/20/16/12sp），ViewModel 应暴露 `textSize: LiveData<Int>`。但实际实现中 **完全没有** 此功能：
- EditorViewModel 无 `_textSize` / `textSize` 字段
- 无 `recalculateTextSize()` 方法
- 无 `TEXT_SIZE_LARGE/MEDIUM/SMALL/MIN` 常量
- MainActivity 无对应的 textSize observer
- 布局中 EditText 的 `textSize` 未绑定到 ViewModel

**影响**: 这是设计文档中的核心功能（§5 字号自适应算法），属于功能性缺失。随着文本增长，编辑器字体不会自动缩小，用户需要用肉眼看越来越小的字。

**建议修复**:
```kotlin
// EditorViewModel.kt 追加
private val _textSize = MutableLiveData(TEXT_SIZE_LARGE)
val textSize: LiveData<Int> = _textSize

companion object {
    const val TEXT_SIZE_LARGE = 24
    const val TEXT_SIZE_MEDIUM = 20
    const val TEXT_SIZE_SMALL = 16
    const val TEXT_SIZE_MIN = 12
}

private fun recalculateTextSize() {
    val text = _text.value ?: ""
    if (text.isEmpty()) { _textSize.value = TEXT_SIZE_LARGE; return }
    val lineCount = text.count { it == '\n' } + 1
    _textSize.value = when {
        lineCount <= 3  -> TEXT_SIZE_LARGE
        lineCount <= 8  -> TEXT_SIZE_MEDIUM
        lineCount <= 15 -> TEXT_SIZE_SMALL
        else            -> TEXT_SIZE_MIN
    }
}
// 在 appendText/undo/redo/updateTextManually/clearAll 末尾调用 recalculateTextSize()
```

```kotlin
// MainActivity.kt setupObservers() 追加
viewModel.textSize.observe(this) { size ->
    binding.etEditor.textSize = size.toFloat()
}
```

---

### P1-2: `adjustResize` 与设计 ADR9 `adjustNothing` 矛盾

**严重度**: P1 | **文件**: `AndroidManifest.xml` 第 18 行
**位置**: `android:windowSoftInputMode="adjustResize"`

**问题**: 设计文档 ADR9 明确决定使用 `adjustNothing`，理由是"保证按钮组不随键盘移位，满足 AC9"。但实际 Manifest 中使用 `adjustResize`。

**分析**: 实际代码使用 `NestedScrollView` + `fillViewport="true"` + `applyWindowInsets` 处理 `ime()` insets → 代码层面走的是 `adjustResize` 路径。设计文档中"变更#3"的布局描述也与 `adjustResize` 一致。ADR9 的结论与后续"变更#3"布局演进存在矛盾。

**建议修复**: 二选一：
- A) 更新 ADR9，确认使用 `adjustResize`（当前代码路径正确，只需更新文档）
- B) 改为 `adjustNothing` 并调整布局（较大改动，需要重新处理键盘遮挡问题）

**推荐 A**：当前代码路径在 `applyWindowInsets` 中已正确处理 `ime()` insets，`adjustResize` + `NestedScrollView` 是正确方案。

---

### P1-3: 缺失 INTERNET 权限移除

**严重度**: P1 | **文件**: `AndroidManifest.xml`
**位置**: 全局（缺少 `tools:node="remove"`）

**问题**: 设计文档 §8.2 明确要求：
```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
```
但实际 Manifest 中**没有此声明**。ZXing Android Embedded 库的传递依赖可能会引入 INTERNET 权限声明。如果不移除，应用会在 Play Store 上显示"完全的网络访问权限"，与离线应用定位矛盾。

**影响**: 隐私合规风险 — 离线应用不应声明网络权限。

**建议修复**: 在 `</manifest>` 前追加：
```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
```

---

### P1-4: Widget `ACTION_APPWIDGET_UPDATE` 双重更新

**严重度**: P1 | **文件**: `QuickNoteWidgetProvider.kt` 第 17-26 行
**位置**: `onReceive()` 方法

**问题**: `onReceive` 既调用 `super.onReceive()`（其内部会触发 `onUpdate`），又在条件分支中再次调用 `updateWidget`，导致每次 `ACTION_APPWIDGET_UPDATE` 广播到来时，**每个 widget 实例被 `updateWidget` 调用两次**。

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)  // → onUpdate → updateWidget (第1次)
    if (intent.action == UPDATE_ACTION || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
        // ...
        updateWidget(context, mgr, id)  // (第2次)
    }
}
```

**影响**: 性能浪费 — 双重 `RemoteViews` 序列化；功能上无错误但不优雅。

**建议修复**:
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == UPDATE_ACTION) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, QuickNoteWidgetProvider::class.java))
        for (id in ids) {
            mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            updateWidget(context, mgr, id)
        }
    }
    // ACTION_APPWIDGET_UPDATE 已在 super.onReceive → onUpdate 中处理，不重复
}
```

---

## P2 — 改进问题

### P2-1: QR 解码无图片下采样，大图可能 OOM

**严重度**: P2 | **文件**: `QrCodeScanner.kt` 第 43 行
**位置**: `decodeQrFromImage()` → `BitmapFactory.decodeStream(inputStream)`

**问题**: 直接 `BitmapFactory.decodeStream(inputStream)` 无任何 `BitmapFactory.Options` 下采样。如果用户从相册选择一张 12MP 照片（约 36MB ARGB_8888），`decodeQrFromBitmap` 还会再创建 `IntArray(bitmap.width * bitmap.height)`，即 12M × 4 字节 = 48MB 的 int 数组。两者相加 ≈ 84MB，极易触发 OOM。

**建议修复**:
```kotlin
fun decodeQrFromImage(inputStream: InputStream): String? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        // 计算合适采样率，目标最大边 2048px
        options.inSampleSize = calculateInSampleSize(options, 2048)
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null
        decodeQrFromBitmap(bitmap)
    } catch (e: Exception) { null }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqMaxDim: Int): Int {
    val (w, h) = options.outWidth to options.outHeight
    var sampleSize = 1
    while (w / sampleSize > reqMaxDim || h / sampleSize > reqMaxDim) sampleSize *= 2
    return sampleSize
}
```

注: 需要重新打开 inputStream（或使用 `mark/reset`），因为 `inJustDecodeBounds` 不读取像素数据但会消耗流。

---

### P2-2: 硬编码字符串和值

**严重度**: P2 | **文件**: 多个文件

| 位置 | 硬编码值 | 建议 |
|------|---------|------|
| `MainActivity.kt:43` | `"image/*"` | 常量或 string resource |
| `MainActivity.kt:121` | `"wt"` | 常量 `SAVE_MODE` |
| `MainActivity.kt:191` | `"com.android.externalstorage.documents"` | 常量 |
| `MainActivity.kt:191` | `"primary:Documents"` | 常量或 `Environment.DIRECTORY_DOCUMENTS` |
| `ScanPasteCaptureActivity.kt:33` | `bottomMargin = 160` | dimens 资源 |
| `ScanPasteCaptureActivity.kt:30` | `textSize = 16f` | dimens 资源 |
| `ScanPasteCaptureActivity.kt:33` | `Color.argb(120,0,0,0)` | color 资源或常量 |
| `ScanPasteCaptureActivity.kt:32-33` | `setPadding(32,12,32,12)` | dimens 资源 |
| `widget_quicknote.xml:20` | `android:text="速记内容为空"` | `@string/widget_empty_text` |

**建议修复**: 将硬编码值提取至 `strings.xml`、`dimens.xml`、`colors.xml` 或 companion object 常量。

---

### P2-3: Widget 服务在主线程执行 IO 操作

**严重度**: P2 | **文件**: `QuickNoteWidgetService.kt` 第 52-54 行
**位置**: `QuickNoteFactory.loadQuickNote()` → MediaStore 查询 + 文件读取

**问题**: `RemoteViewsFactory.onDataSetChanged()` 在主线程调用，内部执行：
1. `contentResolver.query()` — 同步 MediaStore 查询
2. `contentResolver.openInputStream()` + `readBytes()` — 文件读取

对于快速笔记文件（通常很小），实际 ANR 风险低，但不符合 Android 最佳实践。如果文件损坏或 MediaStore 延迟，可能阻塞主线程数秒。

**建议修复**:
```kotlin
override fun onDataSetChanged() {
    // RemoteViewsFactory 在主线程上调用；在协程中加载
    // 方案1: 使用简单线程池
    Thread {
        val (h, i) = loadQuickNoteSync()
        handler.post {
            header = h; items = i
            // notify AppWidgetManager to refresh
        }
    }.start()
}
// 注: RemoteViewsFactory 没有内置异步机制，这是标准做法
```

---

### P2-4: EditText TextWatcher 未在 onDestroy 移除

**严重度**: P2 | **文件**: `MainActivity.kt` 第 93-100 行
**位置**: `setupTextWatcher()` → 匿名 `TextWatcher`

**问题**: `addTextChangedListener` 添加了匿名 TextWatcher，但从未调用 `removeTextChangedListener`。虽然 Activity 销毁时 EditText 也会被回收，但在某些边缘场景（如 Activity 被 retain 但 View 需要重建），TextWatcher 可能导致旧的 Activity 引用无法被 GC。

**建议修复**:
```kotlin
private val editorTextWatcher = object : TextWatcher {
    override fun beforeTextChanged(...) {}
    override fun onTextChanged(...) {}
    override fun afterTextChanged(s: Editable?) {
        val text = s?.toString() ?: ""
        val current = viewModel.text.value ?: ""
        if (text != current) viewModel.updateTextManually(text)
    }
}

override fun onDestroy() {
    binding.etEditor.removeTextChangedListener(editorTextWatcher)
    super.onDestroy()
}
```

---

### P2-5: `saveExtension.lowercase()` 非 Locale-safe

**严重度**: P2 | **文件**: `MainActivity.kt` 第 179 行
**位置**: `launchSaveFile()` → `saveExtension.lowercase()`

**问题**: `String.lowercase()` 使用默认 Locale。在土耳其语等 Locale 中，`"TXT".lowercase()` 返回 `"txt"` 会正确（T→t)，但 `"XML".lowercase()` 变为 `"xml"`（X→x, M→m, L→l）。幸好文件扩展名通常不涉及特殊字符映射，但最佳实践应使用 `Locale.ROOT`:

```kotlin
saveExtension.lowercase(Locale.ROOT)
```

---

### P2-6: 无障碍属性缺失

**严重度**: P2 | **文件**: `activity_main.xml`, `ScanPasteCaptureActivity.kt`

| 控件 | 缺失属性 |
|------|---------|
| `btn_clear` | `android:contentDescription` |
| `btn_quicknote` | `android:contentDescription` |
| `btn_save` | `android:contentDescription` |
| ScanPasteCaptureActivity 的 prompt TextView | `android:contentDescription` |

**建议修复**: 为每个交互控件添加 contentDescription。

---

### P2-7: `findOrCreateQuickNoteUri()` 逻辑重复

**严重度**: P2 | **文件**: `MainActivity.kt:148-156`, `QuickNoteWidgetService.kt:79-100`
**位置**: `MainActivity.findOrCreateQuickNoteUri()` ≈ `QuickNoteFactory.findOrCreateUri()`

**问题**: 两个类中有几乎完全相同的 URI 查找/创建逻辑，违反 DRY 原则。如果 MediaStore 路径或文件名需要修改，需要同时改两处。

**建议修复**: 提取到共享工具类，如 `QuickNoteStorage.kt`：
```kotlin
object QuickNoteStorage {
    private const val FILE_NAME = "quicknote.txt"
    private const val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/scanpaste/"

    fun findOrCreateUri(context: Context): Uri? { /* ... */ }
}
```

---

### P2-8: Layout XML 元素 ID — MaterialToolbar 放在 ConstraintLayout 中使用 `app:title` 属性可能有渲染问题

**严重度**: P2 | **文件**: `activity_main.xml` 第 16-51 行
**位置**: MaterialToolbar 内嵌 LinearLayout 作为 toolbar 子元素

**问题**: `MaterialToolbar` 内使用 `LinearLayout`（`layout_gravity="end"`）作为 action views 的容器，但未使用标准的 `app:menu` 或 Toolbar 的标准方式添加 action。虽然这样可以工作，但如果 MaterialToolbar 内部对子 View 做特殊处理（如 title 与 action 之间的间距），可能导致布局偏移。

**建议**: 使用标准的 `app:menu` + `onCreateOptionsMenu` 方式，或改用 `MaterialToolbar` 的 `addView` 方式创建 action views。如果当前方案工作正常，可降为 P3。

---

## P3 — 建议问题

### P3-1: AppendHistory KDoc 不变式与 init 注释不一致

**严重度**: P3 | **文件**: `AppendHistory.kt` 第 5-14 行

**问题**: KDoc 声明不变式 `text.isNotEmpty()`，但 init block 注释说 `// text can be empty — used for "clear" redo entries`。二者矛盾。

**建议修复**: 更新 KDoc：
```kotlin
/**
 * Records a single append operation for undo/redo tracking.
 *
 * Invariants:
 * - position >= 0
 * - text can be empty (used for "clear" redo entries and clear-all undo snapshots)
 */
```

---

### P3-2: `MultiFormatReader` 每次解码新建实例

**严重度**: P3 | **文件**: `QrCodeScanner.kt` 第 62 行
**位置**: `decodeQrFromBitmap()` → `MultiFormatReader()`

**问题**: 每次图片解码都新建 `MultiFormatReader` 实例。虽然 ZXing 文档说它非线程安全 → 不共享是正确的。但如果只需要主线程解码，可以复用实例以减少对象分配。

**建议**: 保持现状即可（主线程单次调用，新建实例开销可忽略），或改为 lazy 字段：
```kotlin
private val reader by lazy {
    MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
}
```

---

### P3-3: `SimpleDateFormat` 重复创建

**严重度**: P3 | **文件**: `QuickNoteWidgetService.kt` 第 64 行
**位置**: `loadQuickNote()` → `SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())`

**问题**: 每次 `onDataSetChanged()` 都创建新的 `SimpleDateFormat` 实例。可提至类级别复用。

**建议修复**:
```kotlin
private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
```

---

### P3-4: `now()` 和 `debounce()` 可改为扩展函数或内联

**严重度**: P3 | **文件**: `MainActivity.kt` 第 202-203 行
**位置**: `debounce()`, `now()`

**建议**: `now()` 可内联或直接用 `System.currentTimeMillis()`；`debounce()` 可改为 `Long.isDebounced()` 扩展函数提升可读性。当前实现功能正确，建议仅供参考。

---

## 架构一致性检查 (对照 design.md)

| 设计项 | 设计文档 | 实际实现 | 一致性 | 引用 |
|--------|---------|---------|--------|------|
| MVVM 架构 | 单 Activity + ViewModel | ✅ 一致 | ✅ | §3.1-3.2 |
| EditorViewModel | text/canUndo/canRedo/undoFailed + 字号 | ❌ 缺字号 (`textSize`) | ❌ | P1-1, §5 |
| AppendHistory 不变式 | position>=0, text 可空 | ✅ 一致（但 KDoc 矛盾） | ⚠️ | P3-1, §3.3 |
| Undo/Redo 双栈模型 | ArrayDeque + 全文搜索容错 | ✅ 完全一致 | ✅ | §4.1-4.2 |
| ZXing 独立 Activity 扫码 | IntentIntegrator + CaptureActivity | ✅ 一致 | ✅ | §6, ADR2 |
| 权限处理 | 运行时权限 + 拒绝引导至设置 | ✅ 一致 | ✅ | §6.2 |
| 栈深度限制 | MAX_HISTORY=100, FIFO 淘汰 | ✅ 一致 | ✅ | ADR4 |
| Layout 结构 | ConstraintLayout + MaterialToolbar + CardView | ✅ 一致 | ✅ | §8.1 |
| Manifest 配置 | singleTop, portrait, adjustNothing | ❌ adjustResize 非 adjustNothing | ❌ | P1-2, ADR9 |
| INTERNET 权限移除 | tools:node="remove" | ❌ 缺失 | ❌ | P1-3, §8.2 |
| 依赖注入 | 手动注入，不用 DI 框架 | ✅ 一致 | ✅ | ADR7 |
| LiveData vs StateFlow | LiveData | ✅ 一致 | ✅ | ADR8 |
| Widget 支持 | 设计未涉及 | ✅ 额外实现 | ➕ | §3.6(v3) |

---

## 文件级检查清单

| 文件 | `!!` 使用 | 硬编码 | 未用 import | 线程安全 | 命名规范 |
|------|----------|--------|------------|---------|---------|
| `MainActivity.kt` | 无 ✅ | 有 (P2-2) | 无 ✅ | main thread ✅ | 良好 ✅ |
| `EditorViewModel.kt` | 无 ✅ | 无 ✅ | 无 ✅ | 缺字号(P1-1) ⚠️ | 良好 ✅ |
| `AppendHistory.kt` | 无 ✅ | 无 ✅ | 无 ✅ | 不可变 data class ✅ | KDoc矛盾(P3-1) ⚠️ |
| `QrCodeScanner.kt` | 无 ✅ | 无 ✅ | 无 ✅ | OOM风险(P2-1) ⚠️ | 良好 ✅ |
| `ScanPasteCaptureActivity.kt` | 无 ✅ | 有 (P2-2) | 无 ✅ | main thread ✅ | 良好 ✅ |
| `QuickNoteWidgetProvider.kt` | 无 ✅ | 无 ✅ | 无 ✅ | 双重更新(P1-4) ⚠️ | 良好 ✅ |
| `QuickNoteWidgetService.kt` | 无 ✅ | 无 ✅ | 无 ✅ | 主线程IO(P2-3) ⚠️ | 良好 ✅ |
| `activity_main.xml` | N/A | 无 ✅ | N/A | N/A | a11y缺失(P2-6) ⚠️ |
| `widget_quicknote.xml` | N/A | 中文(P2-2) | N/A | N/A | 良好 ✅ |
| `AndroidManifest.xml` | N/A | 无 ✅ | N/A | N/A | adjustResize(P1-2) ⚠️ |

---

## 总结

### 代码质量总评

整体代码质量**良好**。Kotlin 语法使用规范、无 `!!` 滥用、MVVM 架构清晰、Undo/Redo 双栈模型实现正确、权限处理完整。主要问题集中在：

1. **功能缺失** — 字号自适应算法（P1-1）是设计文档中定义但未实现的核心功能
2. **文档与实现不一致** — `adjustResize` vs `adjustNothing`（P1-2）、INTERNET 权限移除（P1-3）
3. **健壮性不足** — OOM 风险（P2-1）、Widget 主线程 IO（P2-3）、无图片下采样（P2-1）

### 修复优先级

1. **先修 P1-1**（字号自适应）— 核心功能缺失
2. **再修 P1-2 文档或代码** — 二选一统一
3. **再修 P1-3**（INTERNET 权限移除）— 隐私合规
4. **再修 P2-1**（图片下采样）— 防止 OOM 崩溃
5. 其余 P2/P3 可按需逐步修复
