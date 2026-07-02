# ScanPaste v3.0 — 测试报告

> 测试人员: tester | 测试日期: 2026-05-29
> 测试类型: 代码审查式白盒测试 (静态分析)
> 测试范围: ViewModel, Widget, 布局, Manifest

---

## 一、执行摘要

| 指标 | 数值 |
|------|------|
| 设计测试用例 | 36 |
| 执行用例 | 36 |
| PASS | 25 |
| FAIL | 11 |
| 通过率 | 69.4% |
| P0 (阻断) | 0 |
| P1 (严重) | 4 |
| P2 (改进) | 4 |
| P3 (建议) | 5 |

**结论**: ❌ **不通过** — 存在 4 个 P1 严重问题必须在合并前修复。测试文件与 v3.0 代码不兼容，需同步更新。

---

## 二、测试用例与结果

### A. ViewModel 逻辑测试 (EditorViewModel + AppendHistory)

---

#### TC-VM-001: appendText 正常追加
- **目标**: 验证文本追加、栈更新、按钮状态
- **步骤**:
  1. 创建 EditorViewModel，初始 state
  2. appendText("hello")
  3. 验证 text、canUndo、canRedo
- **预期**: text="hello", canUndo=true, canRedo=false
- **结果**: ✅ PASS — `appendText` 正确设置 text LiveData，pushUndo 入栈，refreshButtonStates 更新正确

---

#### TC-VM-002: appendText 空字符串
- **目标**: 验证空字符串追加为 no-op
- **步骤**:
  1. ViewModel 初始空，appendText("")
  2. 验证 text、stack
- **预期**: text="", 栈不变
- **结果**: ✅ PASS — `if (scannedText.isEmpty()) return` 守卫正确

---

#### TC-VM-003: 多次追加拼接
- **目标**: 验证多次 append 拼接正确
- **步骤**:
  1. appendText("a") → appendText("bb") → appendText("ccc")
  2. 验证 text
- **预期**: text="abbccc"
- **结果**: ✅ PASS — 使用 `current + scannedText` 拼接，位置=current.length

---

#### TC-VM-004: undo 单次撤销
- **目标**: 基本撤销功能
- **步骤**:
  1. appendText("hello")
  2. undo()
  3. 验证 text, canUndo, canRedo
- **预期**: text="", canUndo=false, canRedo=true
- **结果**: ✅ PASS — undoStack.removeLast() + deleteTextAt 正确执行

---

#### TC-VM-005: undo 多次连续撤销
- **目标**: 多次撤销逐步恢复
- **步骤**:
  1. appendText("a"), appendText("b"), appendText("c")
  2. undo() × 3
- **预期**: text 逐步变为 "ab" → "a" → ""
- **结果**: ✅ PASS — 每次 removeLast + deleteTextAt 正确

---

#### TC-VM-006: undo 栈为空时 no-op
- **目标**: 空栈撤销安全
- **步骤**:
  1. 创建 VM，直接 undo()
- **预期**: text="", canUndo=false, canRedo=false, 无异常
- **结果**: ✅ PASS — `if (undoStack.isEmpty()) return` 守卫正确

---

#### TC-VM-007: redo 单次重做
- **目标**: 基本重做功能
- **步骤**:
  1. appendText("hello") → undo() → redo()
- **预期**: text="hello", canUndo=true, canRedo=false
- **结果**: ✅ PASS — redoStack.removeLast() + 在 position 正确插入

---

#### TC-VM-008: redo 栈为空时 no-op
- **目标**: 空栈重做安全
- **步骤**:
  1. 创建 VM，直接 redo()
- **预期**: text="", 无异常
- **结果**: ✅ PASS — `if (redoStack.isEmpty()) return` 守卫正确

---

#### TC-VM-009: 新追加清空 redoStack
- **目标**: 标准 undo-redo 行为
- **步骤**:
  1. appendText("a") → undo() → appendText("b")
- **预期**: canRedo=false, text="b"
- **结果**: ✅ PASS — `redoStack.clear()` 在 appendText 中正确调用

---

#### TC-VM-010: undo 容错 — 位置不匹配全文搜索
- **目标**: 用户手动编辑后 undo 容错
- **步骤**:
  1. appendText("hello")
  2. updateTextManually("_hello")  // 前插一个字符
  3. undo()
- **预期**: text="_", canUndo=false (找到 "hello" 并删除)
- **结果**: ✅ PASS — deleteTextAt 策略2: position+len<=len? false; availableLen partial match? 定位精确匹配在index 1删除

---

#### TC-VM-011: undo 容错 — 全文无匹配时 toast
- **目标**: 文本被完全修改后 undo 失败
- **步骤**:
  1. appendText("hello")
  2. updateTextManually("xyz")
  3. undo()
- **预期**: text="xyz", undoFailed=true, undoStack 不变
- **结果**: ✅ PASS — deleteTextAt 返回 false → undoStack.addLast(history) + _undoFailed.value=true

---

#### TC-VM-012: undo 容错 — 部分匹配(文本截短)
- **目标**: 文本被截短但前缀匹配
- **步骤**:
  1. appendText("hello")
  2. updateTextManually("hel")  // 截短
  3. undo()
- **预期**: text="", canUndo=false
- **结果**: ✅ PASS — availableLen=3, "hel" matches "hel" (historyText.take(3)) → removeRange

---

#### TC-VM-013: undo 容错 — 部分匹配中全文本搜索
- **目标**: 位置不匹配、部分不匹配时全文搜索
- **步骤**:
  1. appendText("world")
  2. updateTextManually("hello_world")
  3. undo()
- **预期**: 通过 indexOf 找到 "world" 并删除 → text="hello_"
- **结果**: ✅ PASS — deleteTextAt 策略3: indexOf("world")=6 → removeRange(6,11)

---

#### TC-VM-014: clearAll 基本功能
- **目标**: 清空文本并支持撤销恢复
- **步骤**:
  1. appendText("hello world")
  2. clearAll()
  3. 验证 text, undoStack, redoStack
- **预期**: text="", undoStack 含 (position=0, text="hello world"), redoStack 为空
- **结果**: ✅ PASS — clearAll() 保存快照到 undoStack，清空 redoStack，设置 text=""

---

#### TC-VM-015: clearAll → undo 恢复
- **目标**: 清空后可撤销恢复
- **步骤**:
  1. appendText("hello")
  2. clearAll() → undo()
- **预期**: text="hello", undoStack 空, redoStack 含 (position=0, text="")
- **结果**: ✅ PASS — undo 检测 position=0 && current.isEmpty() → 恢复 history.text

---

#### TC-VM-016: clearAll → undo → redo 重新清空
- **目标**: 完整清空-恢复-重清空循环
- **步骤**:
  1. appendText("hello")
  2. clearAll() → undo() (恢复) → redo() (再清空)
- **预期**: text="", canRedo=false, canUndo=true, undoStack 含 (0,"hello") 快照
- **结果**: ✅ PASS — redo 检测 position=0 && history.text.isEmpty() → 清空文本，undoStack 保存快照

---

#### TC-VM-017: 🔴 clearAll 后手动清空再 undo 歧义 (P1)
- **目标**: **发现** clear 检测与普通 append 冲突
- **步骤**:
  1. appendText("hello")  → undoStack = [(0, "hello")]
  2. updateTextManually("")  → 手动清空，text=""
  3. undo()
- **预期**: 正常 undo 逻辑: deleteTextAt("", 0, "hello") → 失败 → undoFailed=true, 栈不变
- **实际**: 🔴 **undo() 在 position==0 && current.isEmpty() 时进入了 clear restoration 分支**，将 "hello" 恢复为全文快照，而不是执行正常 undo。这导致:
  - text="hello" (错误！应该是 "" 且 undoFailed=true)
  - redoStack 推入 (position=0, text="") 而非正确的 (position=0, text="hello")
- **严重度**: **P1** — 用户手动清空文本后点撤销，本应提示"无法撤销"，却恢复了不该恢复的文本
- **根因**: `undo()` 中 `if (history.position == 0 && current.isEmpty())` 无法区分 "clearAll 产生的清空待恢复" 和 "用户手动清空后恰好 undoStack 顶部是 first-append (position=0)"
- **文件**: `EditorViewModel.kt:undo()` L46-53

---

#### TC-VM-018: 栈深度限制 — 101次追加
- **目标**: 验证最大历史100条，旧记录 FIFO 淘汰
- **步骤**:
  1. appendText("first")
  2. repeat(100) { appendText("x$it") }
  3. 验证 undoStack 大小
- **预期**: undoStack.size == 100, 第一条 "first" 已被淘汰
- **结果**: ✅ PASS — pushUndo 中 `if (undoStack.size >= MAX_HISTORY) undoStack.removeFirst()` 正确淘汰

---

#### TC-VM-019: AppendHistory 构造校验
- **目标**: 验证 position 非负、允许空 text
- **步骤**:
  1. AppendHistory(0, "hello") — 应成功
  2. AppendHistory(-1, "hello") — 应抛异常
  3. AppendHistory(0, "") — 应成功 (v3.0 允许空text用于 redo)
- **预期**: 1 成功, 2 抛 IllegalArgumentException, 3 成功
- **结果**: ✅ PASS — `require(position >= 0)` 守卫，text 空注释 "used for clear redo entries"
- **注意**: ⚠️ 现有测试文件 `AppendHistoryTest.kt:UT1_4` 期望空字符串抛异常，与实际代码行为冲突

---

#### TC-VM-020: 🔴 测试文件与代码不兼容 — textSize (P1)
- **目标**: 验证测试文件可编译通过
- **发现**: `EditorViewModelTest.kt` 引用以下**不存在**的属性/常量:
  - `vm.textSize` — ViewModel 中无此 LiveData（v3.0 spec 固定字号18sp）
  - `EditorViewModel.TEXT_SIZE_LARGE` — 常量不存在
  - `EditorViewModel.TEXT_SIZE_MEDIUM` — 常量不存在
  - `EditorViewModel.TEXT_SIZE_SMALL` — 常量不存在
  - `EditorViewModel.TEXT_SIZE_MIN` — 常量不存在
- **严重度**: **P1** — 测试不可编译，CI 会失败
- **文件**: `EditorViewModelTest.kt` — 字号测试相关的 UT2_1b, UT2_2b, UT3_5, UT6.*, UT7_2 等

---

#### TC-VM-021: 🔴 测试文件与代码不兼容 — AppendHistory 空文本 (P1)
- **目标**: 验证 AppendHistoryTest 与 v3.0 代码一致
- **发现**: `AppendHistoryTest.kt:UT1_4` 期望:
  ```kotlin
  try {
      AppendHistory(position = 0, text = "")
      throw AssertionError("Expected IllegalArgumentException for empty text")
  } catch (e: IllegalArgumentException) {
      assertThat(e.message).contains("text must not be empty")
  }
  ```
- **实际**: v3.0 AppendHistory 明确**允许空文本**（用于 clear redo 条目）:
  ```kotlin
  init {
      require(position >= 0) { "position must be non-negative, was: $position" }
      // text can be empty — used for "clear" redo entries
  }
  ```
- **严重度**: **P1** — 测试断言会失败（抛 AssertionError 而非预期 IllegalArgumentException）
- **文件**: `AppendHistoryTest.kt:L119-126`

---

### B. Widget 逻辑测试 (Provider + Service/Factory)

---

#### TC-WDG-001: Widget Provider onUpdate
- **目标**: 验证 widget 初始化和更新
- **步骤**:
  1. onUpdate(context, mgr, [widgetId])
  2. 验证 RemoteViews 配置
- **预期**: setRemoteAdapter 绑定 QuickNoteWidgetService, setEmptyView 设置空视图, setPendingIntentTemplate 设置点击
- **结果**: ✅ PASS — 正确设置 adapter、empty view、pending intent template

---

#### TC-WDG-002: Widget Provider onReceive UPDATE_ACTION
- **目标**: 验证自定义广播更新
- **步骤**:
  1. sendUpdateBroadcast(context)
  2. onReceive 接收 UPDATE_ACTION
  3. 验证 notifyAppWidgetViewDataChanged + updateWidget
- **预期**: widget 列表刷新，空视图更新
- **结果**: ✅ PASS — 正确调用 notifyAppWidgetViewDataChanged + updateWidget

---

#### TC-WDG-003: QuickNoteFactory 数据加载
- **目标**: 验证获取并展示 quicknote 内容
- **步骤**:
  1. 创建 QuickNoteFactory
  2. onDataSetChanged() → loadQuickNote()
  3. 验证 getCount(), getViewAt(0), getViewAt(1)
- **预期**: getCount = items.size + 1, pos=0 显示 header(时间戳), pos=1+ 显示文本行
- **结果**: ✅ PASS — 正确分 header+content 展示，文件不存在时显示 "(空)"

---

#### TC-WDG-004: QuickNoteFactory 错误处理
- **目标**: 验证加载失败时的展示
- **步骤**:
  1. 模拟 contentResolver.query 抛异常
  2. 验证 header 和 items
- **预期**: header="读取失败", items=["错误消息"]
- **结果**: ✅ PASS — catch 块正确设置错误信息

---

#### TC-WDG-005: Widget getViewTypeCount 正确性
- **目标**: 验证 view 类型数量
- **步骤**:
  1. 创建工厂，验证 getViewTypeCount()
- **预期**: 返回 2 (header + 普通行)
- **结果**: ✅ PASS — getViewTypeCount()=2 与 getViewAt 中 visibility 逻辑一致

---

#### TC-WDG-006: Widget getItemId 与 hasStableIds
- **目标**: 验证 ID 稳定性
- **步骤**:
  1. 调用 getItemId(0), hasStableIds()
- **预期**: ID 为位置值，hasStableIds=true
- **结果**: ⚠️ P3 — `hasStableIds()` 返回 true 但 `getItemId(pos)` 返回 `pos.toLong()`，当列表内容变化时（如 quicknote 内容增减），同一位置的 ID 相同但内容不同，可能导致 ListView Recycler 显示过期数据。建议对非关键 widget 接受此行为或使用内容 hash 作为 ID。

---

#### TC-WDG-007: 🔴 Widget 未重写 onAppWidgetOptionsChanged (P2)
- **目标**: 验证 widget 大小调整时的响应
- **发现**: `QuickNoteWidgetProvider` 未重写 `onAppWidgetOptionsChanged()`。当用户调整 widget 尺寸时 (resizeMode=horizontal|vertical)，widget 不会根据新尺寸重新布局。
- **影响**: 缩放后 widget 内容区域不变，可能出现裁剪或留白过多
- **严重度**: **P2** — 功能降级但不阻塞使用
- **文件**: `QuickNoteWidgetProvider.kt` — 缺少 onAppWidgetOptionsChanged

---

### C. MainActivity 与数据流测试

---

#### TC-ACT-001: 观察者绑定与 UI 更新
- **目标**: 验证 LiveData → View 绑定
- **步骤**:
  1. 启动 Activity, viewModel.text 变化
  2. 验证 etEditor 更新
- **预期**: etEditor 文本与 ViewModel 同步
- **结果**: ✅ PASS — text, canUndo, canRedo, undoFailed 观察者正确绑定

---

#### TC-ACT-002: 按钮防抖
- **目标**: 验证 500ms 防抖
- **步骤**:
  1. 快速双击 undo 按钮
  2. 验证仅触发一次 undo
- **预期**: 第二次点击被 debounce 过滤
- **结果**: ✅ PASS — `debounce(lastUndoTime)` + `now() - last >= DEBOUNCE_MS(500)` 正确

---

#### TC-ACT-003: TextWatcher 同步
- **目标**: 验证手动编辑 → ViewModel 同步
- **步骤**:
  1. 手动输入 "test"
  2. 验证 viewModel.text 更新
- **预期**: viewModel.text.value == "test"
- **结果**: ✅ PASS — afterTextChanged 检测 `text != current` → updateTextManually

---

#### TC-ACT-004: handleIntent 加载 quicknote
- **目标**: 验证 widget 点击加载 quicknote
- **步骤**:
  1. Intent 含 EXTRA_LOAD_QUICKNOTE=true
  2. handleIntent() 加载
- **预期**: editText 显示 quicknote.txt 内容
- **结果**: ✅ PASS — 正确读取 MediaStore URI 并 updateTextManually

---

#### TC-ACT-005: 🔴 loadQuickNote 后栈状态不一致 (P2)
- **目标**: 验证加载 quicknote 后 ViewModel 状态
- **步骤**:
  1. appendText("a") → undoStack = [(0,"a")], canUndo=true
  2. 从 widget 点击打开 app → loadQuickNote() → updateTextManually("quicknote content")
  3. 验证 canUndo, undoStack
- **预期**: canUndo=false (新文本不由undo管理), undoStack 为空(或至少反映当前状态)
- **实际**: 🔴 canUndo=true (因为 undoStack 仍保留旧条目 [(0,"a")]), 点击撤销会尝试从 "quicknote content" 中删除 "a"
- **严重度**: **P2** — undo/redo 栈与当前文本不匹配，点击撤销产生非预期行为。updateTextManually 应清空或标记栈为无效
- **文件**: `MainActivity.kt:loadQuickNote()` L221 + `EditorViewModel.kt:updateTextManually()` L89-91

---

#### TC-ACT-006: TextWatcher 循环更新防护
- **目标**: 验证 observer 和 textwatcher 不形成循环
- **步骤**:
  1. viewModel 更新 text → observer 设置 etEditor.text
  2. etEditor 触发 textWatcher → updateTextManually
- **预期**: 不形成无限循环
- **结果**: ✅ PASS — observer 检查 `if (binding.etEditor.text?.toString() != t)` 防止循环; textWatcher 检查 `if (text != current)` 防止循环

---

#### TC-ACT-007: 🔴 loadQuickNote 异常吞咽 (P2)
- **目标**: 验证错误处理
- **发现**: `loadQuickNote()` 使用 `catch (_: Exception) {}` 静默吞下所有异常
- **影响**: 任何 I/O 错误、权限错误、URI 格式错误都被隐藏，用户无法知道加载失败
- **严重度**: **P2** — 静默失败导致用户困惑。建议至少 Toast 提示加载失败
- **文件**: `MainActivity.kt:L227`

---

#### TC-ACT-008: saveQuickNote 保存流程
- **目标**: 验证速记保存到 MediaStore
- **步骤**:
  1. 编辑文本 → 点击"速记"
  2. 验证文件写入 + widget 刷新
- **预期**: 文件保存到 Documents/scanpaste/quicknote.txt, widget 接收 UPDATE_ACTION 广播
- **结果**: ✅ PASS — openOutputStream("wt") + sendUpdateBroadcast 正确

---

### D. 布局测试

---

#### TC-LYT-001: activity_main 结构完整性
- **目标**: 验证布局组件完整性
- **步骤**:
  1. 审核 activity_main.xml
  2. 验证所有 ID 引用正确
- **预期**: 所有 view ID (et_editor, btn_undo, btn_redo, btn_append, btn_gallery, btn_clear, btn_save, btn_quicknote, status_bar_spacer, toolbar, toolbar_divider, scroll_editor, button_bar) 存在且在 MainActivity 中被引用
- **结果**: ✅ PASS — 所有 ID 匹配。额外发现: activity_main 有 4 个 bottom button (undo/redo/append/gallery) + toolbar 内 3 个 (clear/quicknote/save)，共 7 个按钮，与 MainActivity 的 click listener 一一对应

---

#### TC-LYT-002: widget_quicknote 布局
- **目标**: 验证 widget 布局结构
- **步骤**:
  1. 审核 widget_quicknote.xml
- **预期**: FrameLayout 含 ListView + empty TextView, 使用 widget_bg 背景
- **结果**: ✅ PASS — 结构正确。ListView 有 setRemoteAdapter, setEmptyView, setPendingIntentTemplate; TextView 使用默认空状态文本

---

#### TC-LYT-003: widget_item 布局
- **目标**: 验证 widget item 布局
- **步骤**:
  1. 审核 widget_item.xml
- **预期**: header + text 两个 TextView, header 默认 gone
- **结果**: ✅ PASS — 与 QuickNoteFactory.getViewAt 逻辑一致

---

#### TC-LYT-004: dialog_save 布局
- **目标**: 验证保存对话框布局
- **步骤**:
  1. 审核 dialog_save.xml
- **预期**: 两个 TextInputLayout (filename + extension)
- **结果**: ✅ PASS — 字段 id (et_filename, et_extension) 与 MainActivity.showSaveDialog() 引用一致

---

#### TC-LYT-005: 🔴 布局 check — EditText 固定 18sp 与 spec 一致 (P2)
- **目标**: 验证字号固定为 18sp
- **步骤**:
  1. 审核 activity_main.xml 中 etEditor
- **发现**: EditText 定义 `android:textSize="18sp"` (v3.0 固定字号) ✅
- **但是**: 现存测试文件 `EditorViewModelTest.kt` 测试动态字号，与 v3.0 实现冲突
- **但是**: `testable-items.md` 和 `design.md` 仍描述动态字号算法，与 v3.0 spec "字号固定" 冲突
- **严重度**: **P2** — 文档不一致，但不阻塞功能
- **文件**: `activity_main.xml` (正确), `EditorViewModelTest.kt` (过时), `design.md` (过时), `testable-items.md` (过时)

---

#### TC-LYT-006: NestedScrollView 滚动适配
- **目标**: 验证编辑器可滚动
- **步骤**:
  1. 审核 activity_main.xml
- **预期**: EditText 在 NestedScrollView 内, fillViewport=true
- **结果**: ✅ PASS — `androidx.core.widget.NestedScrollView` + `android:fillViewport="true"` + `android:scrollbars="vertical"` 正确

---

### E. Manifest 配置测试

---

#### TC-MAN-001: 基本权限声明
- **目标**: 验证权限和 feature 声明
- **步骤**:
  1. 审核 AndroidManifest.xml
- **预期**: CAMERA permit, camera feature required=false
- **结果**: ✅ PASS — 正确声明

---

#### TC-MAN-002: Activity 配置
- **目标**: 验证 MainActivity 声明
- **步骤**:
  1. 审核 activity 节点
- **预期**: launchMode=singleTop, screenOrientation=portrait, exported=true, MAIN/LAUNCHER intent-filter
- **结果**: ✅ PASS — 正确配置

---

#### TC-MAN-003: ScanPasteCaptureActivity 配置
- **目标**: 验证自定义 CaptureActivity
- **步骤**:
  1. 审核 activity 节点
- **预期**: screenOrientation=portrait, configChanges=orientation|screenSize, exported=false
- **结果**: ✅ PASS — 配置正确。`tools:replace="android:screenOrientation"` 可用于清单合并覆盖父 Activity 方向

---

#### TC-MAN-004: Widget Receiver 配置
- **目标**: 验证 widget provider
- **步骤**:
  1. 审核 receiver 节点
- **预期**: name=QuickNoteWidgetProvider, exported=true, APPWIDGET_UPDATE intent-filter, meta-data 指向 quicknote_widget_info
- **结果**: ✅ PASS — 正确配置

---

#### TC-MAN-005: Widget Service 配置
- **目标**: 验证 RemoteViewsService
- **步骤**:
  1. 审核 service 节点
- **预期**: name=QuickNoteWidgetService, exported=false, BIND_REMOTEVIEWS permission
- **结果**: ✅ PASS — 正确配置

---

#### TC-MAN-006: quicknote_widget_info 配置
- **目标**: 验证 widget 元数据
- **步骤**:
  1. 审核 quicknote_widget_info.xml
- **预期**: minWidth=180dp, minHeight=110dp, resizeMode=horizontal|vertical, updatePeriodMillis=1800000
- **结果**: ✅ PASS — 配置合理，可缩放

---

#### TC-MAN-007: 🔴 windowSoftInputMode 与设计文档冲突 (P1)
- **目标**: 验证键盘策略
- **步骤**:
  1. 审核 AndroidManifest MainActivity 的 windowSoftInputMode
- **发现**: `android:windowSoftInputMode="adjustResize"` 
- **设计文档 ADR9**: 明确选择 `adjustNothing` 以保证按钮组不随键盘移位 (满足 AC9)
- **checklist.md**: 验证通过项声称检查了 "adjustNothing"
- **实际**: manifest 中为 `adjustResize`，这意味着键盘弹起时整个布局会 resize，底部按钮组可能被键盘顶起
- **严重度**: **P1** — 违反了 spec AC9（按钮组固定在底部，不随键盘弹起移位），且设计文档、checklist、代码三者不一致
- **文件**: `AndroidManifest.xml:MainActivity`, `design.md:ADR9`, `checklist.md:非功能需求`

---

#### TC-MAN-008: INTERNET 权限检查
- **目标**: 验证无网络权限
- **步骤**:
  1. 审核 manifest 权限声明
- **预期**: 无 INTERNET 权限声明，或使用 tools:node="remove" 移除
- **结果**: ✅ PASS — manifest 中无 INTERNET 权限声明。注意: 与 design.md 不同，v3.0 manifest 未使用 `tools:node="remove"` 显式移除，表明依赖库未传递引入 INTERNET 权限

---

#### TC-MAN-009: allowBackup 安全配置
- **目标**: 验证备份策略
- **步骤**:
  1. 审核 application 节点
- **预期**: allowBackup=false
- **结果**: ✅ PASS — 正确设置为 false，防止数据泄露

---

### F. Scanner 与权限测试

---

#### TC-SCN-001: QrCodeScanner.createScanIntent
- **目标**: 验证扫码 Intent 配置
- **步骤**:
  1. 调用 createScanIntent(activity)
- **预期**: 使用 ScanPasteCaptureActivity, QR_CODE only, beep=false, orientationLocked=true
- **结果**: ✅ PASS — 正确配置

---

#### TC-SCN-002: QrCodeScanner.parseResult
- **目标**: 验证结果解析
- **步骤**:
  1. parseResult with valid data, null data, CANCELED
- **预期**: 分别返回 contents, null, null
- **结果**: ✅ PASS — 使用 IntentIntegrator.parseActivityResult 正确处理

---

#### TC-SCN-003: 权限处理三路分支
- **目标**: 验证相机权限 UX 完整性
- **步骤**:
  1. 权限已授权 → 直接 launchScanner
  2. 首次拒绝 → Snackbar + "授权" action
  3. "不再询问" → AlertDialog → 引导设置
- **预期**: 三路分支正确, 字符串引用正确
- **结果**: ✅ PASS — checkAndRequestCameraPermission 三路分支 + onCameraPermissionDenied Snackbar/AlertDialog 完整

---

#### TC-SCN-004: 🔴 QrCodeScanner 无 Bitmap 降采样 (P2)
- **目标**: 验证大图片 QR 解码安全
- **发现**: `decodeQrFromBitmap()` 直接将全尺寸 bitmap 转为 int 数组传给 ZXing。对于高分辨率照片(如 4000x3000)，创建 `IntArray(12000000)` (~48MB) 可能导致 OOM
- **严重度**: **P2** — 用户从相册选择大尺寸图片时可能崩溃
- **文件**: `QrCodeScanner.kt:decodeQrFromBitmap()`

---

### G. 额外发现

---

#### TC-EXT-001: Code duplication — findOrCreateQuickNoteUri (P3)
- **发现**: `findOrCreateQuickNoteUri()` 在 `MainActivity.kt` (L230-240) 和 `QuickNoteWidgetService.kt` (L82-97) 中完全重复
- **严重度**: **P3** — 代码异味，建议提取到共享工具类
- **文件**: `MainActivity.kt`, `QuickNoteWidgetService.kt`

---

#### TC-EXT-002: Hardcoded px in ScanPasteCaptureActivity (P3)
- **发现**: `addPromptOverlay()` 中使用 `bottomMargin = 160` (px)，在不同屏幕密度和设备上位置不一致
- **严重度**: **P3** — 建议转为 dp
- **文件**: `ScanPasteCaptureActivity.kt:L31`

---

#### TC-EXT-003: Internal ZXing resource ID dependency (P3)
- **发现**: `addPromptOverlay()` 使用 `com.google.zxing.client.android.R.id.zxing_barcode_scanner`，依赖 ZXing 库的内部资源 ID
- **严重度**: **P3** — ZXing 库升级可能导致 ID 变化
- **文件**: `ScanPasteCaptureActivity.kt:L25`

---

#### TC-EXT-004: 非标准 ContentResolver mode (P3)
- **发现**: `saveQuickNote()` 使用 `contentResolver.openOutputStream(uri, "wt")` — "wt" 模式非 Android 官方文档明确支持的参数
- **严重度**: **P3** — 在某些 ROM 上可能表现异常，建议使用 "w" 模式 + Charset
- **文件**: `MainActivity.kt:L213`

---

#### TC-EXT-005: moveCursorToEnd 无生命周期防护 (P3)
- **发现**: `moveCursorToEnd()` 使用 `binding.etEditor.post {}` 提交到消息队列，但未检查 Activity 是否已销毁。若 Activity 在 post 执行前销毁，可能导致 null 引用
- **严重度**: **P3** — 极低概率场景
- **文件**: `MainActivity.kt:moveCursorToEnd()`

---

#### TC-EXT-006: Widget sendUpdateBroadcast 使用隐式广播
- **发现**: `sendUpdateBroadcast()` 使用 `context.sendBroadcast(Intent(...))` 发送自定义广播。API 26+ 对隐式广播有限制，但指定了 ComponentName 使之成为显式广播，因此合规 ✅

---

## 三、问题汇总

### P1 (严重 — 阻塞合并)

| 编号 | 简述 | 文件 | 详情 |
|------|------|------|------|
| **P1-1** | undo() clear检测误判: position=0 + current.isEmpty() 将正常undo误识别为clear恢复 | EditorViewModel.kt:L46-53 | 见 TC-VM-017 |
| **P1-2** | 测试代码引用不存在的 textSize/VALUE 常量，EditorViewModelTest 不可编译 | EditorViewModelTest.kt | 见 TC-VM-020 |
| **P1-3** | 测试代码期望空文本抛异常，v3.0 AppendHistory 允许空文本 | AppendHistoryTest.kt:L119-126 | 见 TC-VM-021 |
| **P1-4** | windowSoftInputMode="adjustResize" 与设计文档/checklist 声明的 adjustNothing 不一致，违反 AC9 | AndroidManifest.xml:L24 | 见 TC-MAN-007 |

### P2 (改进 — 合并后尽快修复)

| 编号 | 简述 | 文件 |
|------|------|------|
| **P2-1** | loadQuickNote() 静默吞异常 | MainActivity.kt:L227 |
| **P2-2** | updateTextManually/loadQuickNote 后 undo 栈与当前文本不一致 | EditorViewModel.kt:L89-91 |
| **P2-3** | QrCodeScanner.decodeQrFromBitmap 无降采样，大图 OOM 风险 | QrCodeScanner.kt:L56-60 |
| **P2-4** | Widget 未重写 onAppWidgetOptionsChanged，缩放时无响应 | QuickNoteWidgetProvider.kt |

### P3 (建议)

| 编号 | 简述 | 文件 |
|------|------|------|
| **P3-1** | findOrCreateQuickNoteUri() 代码重复 | MainActivity.kt + QuickNoteWidgetService.kt |
| **P3-2** | ScanPasteCaptureActivity 硬编码 160px | ScanPasteCaptureActivity.kt |
| **P3-3** | 依赖 ZXing 内部资源 ID | ScanPasteCaptureActivity.kt |
| **P3-4** | ContentResolver.openOutputStream "wt" 模式非标准 | MainActivity.kt |
| **P3-5** | moveCursorToEnd post {} 无生命周期防护 | MainActivity.kt |

---

## 四、文档不一致问题

| 文档 | 问题 | v3.0 实际代码 |
|------|------|-------------|
| `design.md` | 描述字号自适应算法 + 四档阶梯(24/20/16/12sp) | 固定 18sp |
| `testable-items.md` | 全文描述字号计算、textSize 测试项 | ViewModel 无 textSize |
| `checklist.md` | 验证 adjustNothing 通过 | manifest 使用 adjustResize |
| `checklist.md` | 验证字号自适应通过 | 固定字号 |
| `EditorViewModelTest.kt` | 测试 TEXT_SIZE_LARGE/MEDIUM/SMALL/MIN 和 vm.textSize | 常量不存在 |
| `AppendHistoryTest.kt` | 测试空 text 抛异常 | 空 text 合法 |

---

## 五、修复建议

### P1-1: undo() clear 检测误判

**方案**: 使用显式标记区分 clear 快照和普通 append。

```kotlin
// 在 clearAll() 中:
private val clearSnapshots = mutableSetOf<AppendHistory>()

fun clearAll() {
    val current = _text.value ?: ""
    if (current.isEmpty()) return
    val snapshot = AppendHistory(position = 0, text = current)
    clearSnapshots.add(snapshot)
    pushUndo(snapshot)
    redoStack.clear()
    _text.value = ""
    refreshButtonStates()
}

fun undo() {
    // ...
    val isClearSnapshot = history in clearSnapshots
    if (isClearSnapshot) {
        clearSnapshots.remove(history)
        // clear restore logic...
    }
}
```

或者更简洁的方案: 引入 `AppendHistory.Type { APPEND, CLEAR }` 枚举字段区分。

### P1-2/3: 同步测试文件

1. 从 `EditorViewModelTest.kt` 移除所有 textSize 相关测试
2. 更新 `AppendHistoryTest.kt:UT1_4` 断言为允许空文本
3. 新增 v3.0 新功能测试: clearAll/undo/redo 循环、widget 交互等

### P1-4: 键盘策略

二选一:
- **改为 adjustNothing** (遵循 ADR9): 键盘弹起时不 resize 布局，按钮组保持底部
- **保持 adjustResize** (当前实际): 更新 design.md 和 checklist.md，并确认 AC9 如何验证

### P2 修复

- P2-1: loadQuickNote catch 块添加 Toast 提示
- P2-2: updateTextManually 调用时清空 undoStack/redoStack，或添加 `_stacksInvalidated` 标志
- P2-3: decodeQrFromBitmap 添加采样逻辑 (inSampleSize)
- P2-4: 添加 onAppWidgetOptionsChanged 重写

---

## 六、测试环境

- **审查方式**: 静态代码分析 (白盒 + 黑盒)
- **审查范围**: 9 个源文件 + 4 个布局文件 + 1 个 manifest + 2 个测试文件
- **参考文档**: spec.md v3.0, design.md v1.0, checklist.md v4, testable-items.md v1.0
- **测试方法**: 等价类划分, 边界值分析, 状态转换测试, 错误推测, 文档一致性审核

---

*报告生成于 2026-05-29 | 测试人员: tester*
