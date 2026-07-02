# ScanPaste — 架构设计文档

> 设计者: coder | 初始日期: 2026-05-29 | 最后更新: 2026-05-29 (v4.3 同步)
> 对应 spec: v4.3 | style-guide: v4.3

---

## 1. 技术栈

| 类别 | 选择 | 理由 |
|------|------|------|
| 语言 | Kotlin 2.1.20 | 最新稳定版 |
| compileSdk | 35 | Android 15 |
| 最低SDK | API 24 (Android 7.0) | spec 要求 |
| 目标SDK | API 35 | 最新 |
| 构建系统 | Gradle 8.14 Kotlin DSL + AGP 8.7.3 | 项目 gradlew 自带 |
| 架构模式 | MVVM (单Activity + ViewModel + LiveData) | spec 强制 ViewModel |
| UI | Material Design 3 + ViewBinding + ConstraintLayout | 现代 Android 实践 |
| 扫码 | ZXing Embedded 4.3.0 | 纯离线 |
| 持久化 | MediaStore (Documents/scanpaste/) | Android 10+ SAF 兼容 |
| 小部件 | AppWidget + RemoteViewsService (ListView) | 桌面速记展示 |
| 彩蛋 | WebView + HTML5 Canvas + JavaScript | 内嵌游戏 |
| 测试 | JUnit4 + Truth | 原生兼容 |
| 混淆 | R8 (release minifyEnabled + proguard-rules.pro) | 包体积控制 |

## 2. 架构图

### 2.1 组件层次

```
┌──────────────────────────────────────────────────────────────────┐
│                        MainActivity                               │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ActivityMainBinding (ViewBinding)                          │  │
│  │  ┌──────────────────────────────────────────────────────┐  │  │
│  │  │  status_bar_spacer (蓝色填充, 动态高度=insets.top)    │  │  │
│  │  ├──────────────────────────────────────────────────────┤  │  │
│  │  │  toolbar (MaterialToolbar, 32dp, 蓝色背景)            │  │  │
│  │  │  ├── tv_title "扫码笔记" (bold 20sp, 可点击→游戏)    │  │  │
│  │  │  ├── version badge "v4.3" (7sp, 半透明白)            │  │  │
│  │  │  ├── btn_clear  ─ 清空                                │  │  │
│  │  │  ├── btn_readnote ─ 速读                              │  │  │
│  │  │  ├── btn_quicknote ─ 速记                             │  │  │
│  │  │  └── btn_save ─ 保存                                  │  │  │
│  │  ├──────────────────────────────────────────────────────┤  │  │
│  │  │  toolbar_divider (View, 1dp, 15%透明)                 │  │  │
│  │  ├──────────────────────────────────────────────────────┤  │  │
│  │  │  NestedScrollView                                     │  │  │
│  │  │  └── etEditor (EditText, 18sp, fillViewport)          │  │  │
│  │  │      - 填充 toolbar_divider 与 button_bar 之间          │  │  │
│  │  │      - textIsSelectable="true"                         │  │  │
│  │  │      - paddingHorizontal=16dp, paddingVertical=12dp   │  │  │
│  │  ├──────────────────────────────────────────────────────┤  │  │
│  │  │  button_bar (MaterialCardView, elevation=4dp)          │  │  │
│  │  │  ├── btnUndo   (TextButton + ic_undo, weight=1)       │  │  │
│  │  │  ├── btnRedo   (TextButton + ic_redo, weight=1)       │  │  │
│  │  │  ├── btnAppend (FilledButton + ic_scan, weight=1)     │  │  │
│  │  │  └── btnGallery (TextButton, 48dp, ic_gallery)        │  │  │
│  │  └──────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐  │
│  │ EditorViewModel  │  │ QrCodeScanner   │  │ AppendHistory    │  │
│  │ - undoStack      │  │ - createIntent  │  │ data class       │  │
│  │ - redoStack      │  │ - parseResult   │  │ position: Int    │  │
│  │ - text LiveData  │  │ - decodeFromImg │  │ text: String     │  │
│  │ - canUndo/Redo   │  └─────────────────┘  └──────────────────┘  │
│  │ - undoFailed     │                                              │
│  │ - clearAll()     │  ┌──────────────────────────────────────┐   │
│  └─────────────────┘  │ QuickNoteWidgetProvider/Service        │   │
│                        │ - RemoteViewsService (ListView)       │   │
│                        │ - 圆角+10%透明背景                    │   │
│                        └──────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                  RabbitJumpActivity                           │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  WebView → rabbitjump.html                              │  │
│  │  ├── Canvas 游戏 (兔跳狐狸)                              │  │
│  │  ├── Android.getGame()  → 读 game.txt 配置               │  │
│  │  ├── Android.getBest()  → 读 best.txt 纪录               │  │
│  │  ├── Android.saveBest() → 写 best.txt 纪录               │  │
│  │  └── Android.back()     → finish()                       │  │
│  └────────────────────────────────────────────────────────┘  │
│  Documents/scanpaste/                                          │
│  ├── quicknote.txt       (速记内容)                            │
│  ├── quicknote-1~5.txt   (速记覆盖备份)                        │
│  ├── game.txt            (🐰/🦊/文字, 3行)                      │
│  └── best.txt            (关卡数,日期时间)                     │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

```
用户操作 → MainActivity.clickListener
    │
    ├─ 追加 → 权限检查 → ZXing CaptureActivity → parseResult → viewModel.appendText()
    ├─ 相册 → galleryLauncher → decodeQrFromUri → viewModel.appendText()
    ├─ 撤销 → viewModel.undo()
    ├─ 重做 → viewModel.redo()
    ├─ 清空 → viewModel.clearAll()
    ├─ 速记 → saveQuickNote() → 存在? → 弹窗确认 → backupAndSaveQuickNote()
    │         ├── 读旧内容 → 删旧文件 → saveToBackupSlot (空位优先/满覆最旧)
    │         └── createQuickNoteUri → 写入新内容
    │       → 不存在? → doSaveQuickNote() 直接保存
    ├─ 速读 → loadQuickNote() → MediaStore read → viewModel.updateTextManually()
    ├─ 保存 → showSaveDialog() → SAF CREATE_DOCUMENT → writeContentToUri()
    └─ 标题点击 → startActivity(RabbitJumpActivity)
                       │
                       └─ WebView.loadUrl("rabbitjump.html")
                          ├─ loadGameConfig() → Android.getGame()
                          ├─ loadBest()       → Android.getBest()
                          └─ saveBest()       → Android.saveBest()

ViewModel 状态流:
    text LiveData ──→ etEditor.text (双向绑定 via TextWatcher)
    canUndo LiveData ──→ btnUndo.isEnabled
    canRedo LiveData ──→ btnRedo.isEnabled
    undoFailed LiveData ──→ Toast (单次事件)
```

## 3. 模块设计

### 3.1 MainActivity

**完整组件清单（v4.2）**：

```kotlin
class MainActivity : AppCompatActivity() {
    // View
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: EditorViewModel
    private lateinit var qrCodeScanner: QrCodeScanner

    // 相机权限
    private val cameraPermissionLauncher: ActivityResultLauncher<String>
    private val scanLauncher: ActivityResultLauncher<Intent>

    // 相册扫码
    private val galleryLauncher: ActivityResultLauncher<String>

    // 保存文件
    private val saveFileLauncher: ActivityResultLauncher<Intent>
    private var saveFileName = "扫码笔记"
    private var saveExtension = "txt"

    // 防抖
    private var lastAppendTime = 0L     // 追加/相册共用
    private var lastUndoTime = 0L
    private var lastRedoTime = 0L

    // 核心方法
    fun onCreate()                        // inflate + insets + observers + listeners
    fun onNewIntent()                     // widget intent 处理
    private fun setupObservers()          // LiveData 绑定
    private fun setupClickListeners()     // 7按钮 + 标题点击 → easter egg
    private fun checkAndRequestCameraPermission()
    private fun launchScanner()
    private fun decodeQrFromUri(uri)      // 相册扫码
    private fun saveQuickNote()           // 速记
    private fun loadQuickNote()           // 速读
    private fun findOrCreateQuickNoteUri()
    private fun showSaveDialog()          // 保存弹窗
    private fun launchSaveFile()
    private fun writeContentToUri(uri)
    private fun applyWindowInsets()       // edge-to-edge
}
```

### 3.2 EditorViewModel

**关键方法（v4.2）**：

```kotlin
class EditorViewModel : ViewModel() {
    val text: LiveData<String>
    val canUndo: LiveData<Boolean>
    val canRedo: LiveData<Boolean>
    val undoFailed: LiveData<Boolean>    // 单次事件：撤销失败时 toast

    fun appendText(scannedText: String)  // 追加 + 清空 redoStack
    fun undo()                           // 含 clear 恢复的特殊处理
    fun redo()                           // 含 clear 重做的特殊处理
    fun clearAll()                       // 全文快照保存到 undoStack
    fun updateTextManually(newText: String)
    fun onUndoFailedHandled()            // 消费 undoFailed 事件

    private fun pushUndo(history)
    private fun trimStack(stack)         // FIFO 淘汰 >100 条
    private fun deleteTextAt(text, pos, historyText): Boolean  // 三重容错
    private fun refreshButtonStates()
}
```

**clearAll 特殊机制**: 清空前将全文以 `AppendHistory(position=0, text=全文)` 保存到 undoStack。撤销时检测 `position==0 && current.isEmpty()` → 恢复全文快照。

### 3.3 AppendHistory

```kotlin
data class AppendHistory(val position: Int, val text: String)
```
- position >= 0（init 校验）
- text 可为空（用于 "clear" redo 条目）

### 3.4 QrCodeScanner

封装 ZXing 扫码启动、结果解析、图片解码。

### 3.5 RabbitJumpActivity

WebView 加载 `rabbitjump.html`，通过 `AndroidBridge` 提供 4 个 JS 接口：
- `back()` → finish
- `getBest()` / `saveBest(data)` → best.txt 读写
- `getGame()` → game.txt 读取（不存在则创建默认值：🐰/🦊/文字）

### 3.6 QuickNoteWidgetProvider + QuickNoteWidgetService

AppWidget 组件：ListView 展示 quicknote.txt 内容，圆角 + 10% 透明背景，点击打开文件。

## 4. Undo/Redo 算法

双栈分离模型，额外支持清空操作的特殊处理：

```
clearAll():
  undoStack.push(AppendHistory(0, 全文快照))
  redoStack.clear()
  text = ""

undo (clear恢复):
  if (position==0 && current.isEmpty()):
    text = history.text           // 恢复全文
    redoStack.push(AppendHistory(0, ""))  // redo=再清空

redo (clear重做):
  if (position==0 && text.isEmpty()):
    undoStack.push(AppendHistory(0, current))  // undo=恢复
    text = ""                                  // 再清空
```

容错策略：deleteTextAt() 三重匹配 → 精确位置 → 部分匹配 → 全文搜索 → Toast 失败。

## 5. 布局要点（v4.2）

- **Edge-to-edge**: 透明状态栏 + `status_bar_spacer`(蓝色, 动态高度) + IME insets 处理
- **NestedScrollView**: fillViewport, 键盘自适应 via adjustResize
- **Toolbar**: 32dp 紧凑高度, 蓝色背景, 右侧4个操作按钮(清空/速读/速记/保存)
- **编辑区**: 固定 18sp, 16dp横/12dp纵 padding, textIsSelectable
- **底部栏**: MaterialCardView elevation=4dp, 5等份(Undo/Redo/Append权重各1 + Gallery固定48dp)
- **底部安全区**: `updatePadding(bottom=systemBars.bottom)`

## 6. 关键设计决策 (ADR)

| # | 决策 | 理由 |
|---|------|------|
| ADR1 | ZXing 非 ML Kit | 纯离线，无模型下载 |
| ADR2 | 独立 CaptureActivity 扫码 | 无需管理相机生命周期 |
| ADR3 | Undo/Redo 双栈 + clear 快照 | 标准模式 + 清空可恢复 |
| ADR4 | 固定 18sp 字号 | 简洁可靠，避免自适应复杂度 |
| ADR5 | MediaStore 持久化 | Android 10+ 标准，Documents/scanpaste/ 目录 |
| ADR6 | game.txt 配置化 | 用户可自定义游戏角色，重启生效 |
| ADR7 | LiveData 非 StateFlow | Android View 层天然支持 |
| ADR8 | adjustResize | 键盘弹起时编辑区自适应收缩 |
| ADR9 | 备份空位优先+满覆最旧 | 简单直观，5槽位足够；删旧→备份→新建避免Uri失效 |
| ADR10 | openOutputStream 不用 "wt" | MediaStore 上 "wt" 模式行为异常导致 0B 文件 |

## 9. 依赖配置

见 `gradle/libs.versions.toml`:

- AGP 8.7.3, Kotlin 2.1.20
- AndroidX: core-ktx 1.12.0, appcompat 1.6.1, material 1.11.0
- Lifecycle 2.7.0, Activity 1.8.2
- ZXing Embedded 4.3.0
- Test: JUnit 4.13.2, Truth 1.1.5
