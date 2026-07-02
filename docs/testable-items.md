# 可测试项清单 — ScanPaste

> 对应 design.md v1.0 | 生成日期: 2026-05-29

---

## 1. 单元测试 (JUnit4 + Truth)

### 1.1 AppendHistory 数据类

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT1.1 | 正常创建 AppendHistory | `AppendHistory(position=0, text="hello")` | `position==0`, `text=="hello"` |
| UT1.2 | data class equals 正确 | 两个相同参数的对象 | `assertThat(a).isEqualTo(b)` |
| UT1.3 | data class copy 正确 | `original.copy(position=5)` | position=5, text 不变 |
| UT1.4 | text 为空字符串 | `AppendHistory(0, "")` | 构造成功（由调用方保证非空，类本身不校验） |

### 1.2 EditorViewModel — appendText()

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT2.1 | 初始状态为空 | 创建 ViewModel | `text.value == ""`, `textSize.value == 24`, `canUndo.value == false`, `canRedo.value == false` |
| UT2.2 | 追加一段文本 | appendText("hello") | `text.value == "hello"`, `textSize.value == 24` (≤3行), `canUndo == true`, `canRedo == false` |
| UT2.3 | 追加多段文本 | append("a") × 3 + append("b\nc\nd\n") × 1 | `text.value` 包含所有追加, `canUndo == true` |
| UT2.4 | 追加后 RedoStack 清空 | append("a") → undo() → append("b") | `canRedo == false`, RedoStack 为空 |
| UT2.5 | 追加空字符串 | appendText("") | text 不变，栈不变（入口处防御） |

### 1.3 EditorViewModel — undo()

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT3.1 | Undo 栈为空时调用 undo() | 新建 VM, 直接 undo() | text 不变, `canUndo == false`, 无异常 |
| UT3.2 | 单次追加后撤销 | append("hello") → undo() | `text.value == ""`, `canUndo == false`, `canRedo == true` |
| UT3.3 | 多次追加后连续撤销 | append("a") → append("bb") → append("ccc") → undo() → undo() | 文本逐步减少, `canUndo` 逐步变为 false（最后一次 undo 后） |
| UT3.4 | 连续撤销至栈空 | append("a") → append("b") → undo() → undo() | `text.value == ""`, `canUndo == false`, `canRedo == true`, RedoStack.size == 2 |
| UT3.5 | 撤销后字号变化 | append("a\nb\nc\nd\ne\nf\ng\nh\ni\n") → append("a") → undo() | 第一次追加后字号变小（>8行），撤销恢复后字号变大 |
| UT3.6 | 追加内容位置不匹配，全文搜索命中 | append("hello") → 手动改 text 为 "_hello" → undo() | "hello" 被从全文搜索位置删除，text 变为 "_" |
| UT3.7 | 追加内容全文搜索无匹配 | append("hello") → 手动改 text 为 "xyz" → undo() | text 不变（"xyz"），返回 false，UndoStack 顶元素不变 |

### 1.4 EditorViewModel — redo()

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT4.1 | Redo 栈为空时调用 redo() | 新建 VM, 直接 redo() | text 不变, `canRedo == false`, 无异常 |
| UT4.2 | 撤销后再恢复 | append("hello") → undo() → redo() | `text.value == "hello"` (恢复原状), `canUndo == true`, `canRedo == false` |
| UT4.3 | 多次撤销后多次重做 | append("a") → append("b") → append("c") → undo() → undo() → redo() → redo() | 文本逐步恢复, RedoStack 逐步消耗 |
| UT4.4 | 重做到 RedoStack 为空 | append("a") → undo() → redo() → redo() | 第二次 redo() 无效果, `canRedo == false` |
| UT4.5 | undo-redo-undo 循环 | append("a") → undo() → redo() → undo() | 每次操作正确切换栈 |

### 1.5 EditorViewModel — 栈深度限制

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT5.1 | 追加101次，栈大小为100 | for i in 1..101: append("x") | canUndo == true, UndoStack.size == 100 |
| UT5.2 | 最旧记录被淘汰 | append("first") → 追加99次其他 → append("last") | 第一条记录（"first"）不在栈中，最后一条在 |

### 1.6 EditorViewModel — 字号计算

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT6.1 | 空文本 → 24sp | text = "" | textSize = 24 |
| UT6.2 | ≤3行 → 24sp | append("line1\nline2\nline3") | textSize = 24 |
| UT6.3 | 3<行≤8 → 20sp | append("1\n2\n3\n4\n5") | textSize = 20 |
| UT6.4 | 8<行≤15 → 16sp | append("1\n2\n3\n4\n5\n6\n7\n8\n9") | textSize = 16 |
| UT6.5 | >15行 → 12sp | append(16行文本) | textSize = 12 |
| UT6.6 | 文本单行超长 → 24sp | append("a".repeat(500)) | textSize = 24 (1行) |
| UT6.7 | Undo 后字号恢复 | append(大段多行文本) → undo() | 字号恢复到之前较小的值（或24sp如果空） |

### 1.7 EditorViewModel — updateTextManually()

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT7.1 | 手动编辑后文本更新 | updateTextManually("new text") | `text.value == "new text"` |
| UT7.2 | 手动编辑后字号重新计算 | updateTextManually(多行文本) | textSize 按行数缩放 |
| UT7.3 | 手动编辑不影响 Undo/Redo 栈 | append("a") → updateTextManually("b") | `canUndo == true`, UndoStack 不变 |

### 1.8 EditorViewModel — 按钮状态

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT8.1 | 初始状态按钮均不可用 | 新建 VM | canUndo == false, canRedo == false |
| UT8.2 | 追加后可 Undo | append("a") | canUndo == true, canRedo == false |
| UT8.3 | 撤销后可 Redo | append("a") → undo() | canUndo == false, canRedo == true |
| UT8.4 | 新追加清空 Redo 按钮 | append("a") → undo() → append("b") | canUndo == true, canRedo == false |

### 1.9 QrCodeScanner

| # | 测试项 | 输入 | 预期输出 |
|---|--------|------|---------|
| UT9.1 | createScanIntent 包含正确参数 | 调用 createScanIntent(context) | Intent action 正确, extra 含 QR_CODE 格式 |
| UT9.2 | parseResult with valid data | RESULT_OK + Intent("SCAN_RESULT", "hello") | 返回 "hello" |
| UT9.3 | parseResult with null data | RESULT_OK + data=null | 返回 null |
| UT9.4 | parseResult with CANCELED | RESULT_CANCELED + valid data | 返回 null |

---

## 2. UI 测试 (手动)

### 2.1 基础布局

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI1.1 | 编辑区和按钮组正确显示 | 启动 App | 编辑区在上方占据主要空间, 三个按钮在底部 56dp 高度内 |
| UI1.2 | 按钮三等分宽度 | 在不同宽度设备上启动 | 三个按钮等宽排列 |
| UI1.3 | 初始状态按钮状态 | 启动 App (无任何操作) | "取消"和"重做"按钮灰色不可点击, "追加"按钮可点击 |
| UI1.4 | Placeholder 显示 | 编辑区为空 | 显示提示文字 "输入文本或点击追加按钮扫码录入" |
| UI1.5 | 竖屏锁定 | 旋转设备 | 界面保持竖屏不旋转 |

### 2.2 文本编辑

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI2.1 | 手动输入文本 | 在编辑区输入中文/英文/数字 | 正常显示, 输入流畅 |
| UI2.2 | 全选 | 长按 → 全选 | 所有文本被选中 |
| UI2.3 | 复制 | 选中文本 → 复制 | 文本复制到系统剪贴板 |
| UI2.4 | 剪切 | 选中文本 → 剪切 | 文本移除并复制到剪贴板 |
| UI2.5 | 粘贴 | 编辑区 → 粘贴（剪贴板有内容） | 剪贴板内容粘贴到光标位置 |
| UI2.6 | 光标定位 | 点击文本中间位置 | 光标定位到点击处 |
| UI2.7 | 滚动 | 输入超一屏文本 → 滑动 | 编辑区可上下滚动 |

### 2.3 按钮组固定

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI3.1 | 键盘弹起时按钮组不移位 | 点击编辑区调起键盘 | 按钮组保持在屏幕底部, 不被键盘顶起 |
| UI3.2 | 键盘收起时按钮组位置不变 | 收起键盘 | 按钮组保持在屏幕底部 |

### 2.4 字号自适应

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI4.1 | 少量文本大字号 | 输入 1 行文本 | 字号约 24sp（肉眼辨认较大） |
| UI4.2 | 中等文本中等字号 | 输入 5 行文本（按回车换行） | 字号约 20sp（肉眼可见缩小） |
| UI4.3 | 较多文本小字号 | 输入 10 行文本 | 字号约 16sp |
| UI4.4 | 大量文本最小字号 | 输入 20 行文本 | 字号 ≥ 12sp, 不进一步缩小 |
| UI4.5 | 撤销后字号恢复 | 多行文本 → undo 后回到少量文本 | 字号恢复到大字号 |
| UI4.6 | 重做后字号变化 | undo 回到少内容 → redo 恢复多内容 | 字号变回小字号 |

### 2.5 Undo/Redo 按钮功能

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI5.1 | 追加后可撤销 | 扫码追加一段内容 → 点"取消" | 追加内容被移除 |
| UI5.2 | 连续撤销 | 追加3次 → 点"取消"3次 | 每次移除最后追加的内容 |
| UI5.3 | 撤销后可重做 | 追加 → 取消 → 点"重做" | 被撤销内容恢复 |
| UI5.4 | 连续重做 | 追加3次 → 取消3次 → 点"重做"3次 | 所有内容逐步恢复 |
| UI5.5 | 追加后重做按钮置灰 | 追加 → 点"重做" | 重做按钮灰色, 无法点击 (无重做项) |
| UI5.6 | 全部撤销后取消按钮置灰 | 追加 → 取消 → 点"取消" | 取消按钮灰色 (无更多可撤销) |
| UI5.7 | 混合编辑后撤销 | 追加"hello" → 手动编辑改文本 → 点"取消" | 按容错逻辑处理 (全文搜索或提示) |
| UI5.8 | 撤销后光标在文末 | 多行文本 → 追加 → 点"取消" | 光标在编辑区末尾 |
| UI5.9 | 重做后光标在文末 | 追加 → 取消 → 重做 | 光标在编辑区末尾 |
| UI5.10 | Undo 后手动编辑 + Undo | 追加"a" → 追加"b" → undo → 手动改文本 → undo | "a" 按全文搜索或位置匹配删除 |

### 2.6 扫码追加

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI6.1 | 扫码追加到文末 | 点"追加" → 扫描有效二维码 → 返回 | 二维码内容追加到编辑区末尾 |
| UI6.2 | 追加后光标在文末 | 扫码追加后 | 光标在文本最末尾 |
| UI6.3 | 追加后"取消"按钮可用 | 追加成功 → 返回编辑区 | "取消"按钮高亮可点击 |
| UI6.4 | 扫描中途按返回键 | 点"追加" → 取景框出现 → 按返回 | 返回编辑区, 文本无变化 |
| UI6.5 | 扫码识别空二维码 | 扫描内容为空的二维码 | ZXing 返回空 → 视为取消, 文本无变化 |
| UI6.6 | 多次扫码追加 | 追加3次不同二维码 | 文本 = 3段内容拼接, 可撤销3次 |
| UI6.7 | 特殊字符二维码 | 扫描含 emoji/特殊符号/换行符 的二维码 | 内容正确追加, 含特殊字符也正常显示 |
| UI6.8 | 超长内容二维码 | 扫描含 500+ 字符的二维码 | 内容正确追加, 字号自动缩小, 滚动可用 |

### 2.7 权限处理

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI7.1 | 首次授权 | 点"追加" → 系统权限弹窗 → "允许" | 进入扫码界面 |
| UI7.2 | 首次拒绝 | 点"追加" → 系统权限弹窗 → "拒绝" | Snackbar 提示 + "授权"操作按钮 |
| UI7.3 | Snackbar 重新授权 | 拒绝后 → 点 Snackbar "授权" → "允许" | 进入扫码界面 |
| UI7.4 | "不再询问"引导 | 多次拒绝后 → 点"追加" | 弹出对话框引导至系统设置 |
| UI7.5 | 从设置返回后权限已开 | 设置中开启相机权限 → 返回 App | 再次"追加"可直接扫码 |
| UI7.6 | 从设置返回后权限未开 | 设置中未开启 → 返回 App | 再次"追加"重复提醒 |

### 2.8 生命周期

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI8.1 | 扫码中途切后台 | 进入取景框 → 按Home键 → 按返回键回App | ZXing Activity 消失 → 返回编辑区（ZXing 自行处理） |
| UI8.2 | 编辑中切后台再返回 | 输入一些文本 → Home → 回App | 文本保留 (ViewModel 存活) |
| UI8.3 | 配置变更 | 编辑中 → 系统字体大小变更 | 文本保留, 字号按新规则重新计算 |
| UI8.4 | 息屏后恢复 | 编辑中 → 按电源键息屏 → 再亮屏解锁 | 编辑区内容保留, App 可正常使用 |

### 2.9 边缘场景

| # | 测试项 | 操作 | 预期结果 |
|---|--------|------|---------|
| UI9.1 | 快速连续点击追加 | 快速连击"追加"3次 | 不会启动3个扫码页面，按钮应有防抖处理 |
| UI9.2 | 快速连续点击取消 | 有1次追加 → 快速连击"取消"3次 | 仅撤销1次, 第2/3次无操作, 不崩溃 |
| UI9.3 | 快速连续点击重做 | 有1次撤销 → 快速连击"重做"3次 | 仅重做1次, 后续无操作, 不崩溃 |
| UI9.4 | 追加100+次后的撤销 | 连续追加100次 → 不断撤销 → 再次追加 | 旧记录被淘汰, 新追加正常, 深度≤100 |
| UI9.5 | 无相机设备 | 在模拟器/无相机设备上启动 | App 正常启动，点击"追加"提示相机不可用 |
| UI9.6 | 应用重启 | 退出 App → 重新启动 | 编辑区为空 (无持久化), 按钮初始状态 |

---

## 3. Instrumentation 测试 (可选 v1.1)

| # | 测试项 | 测试方法 | 预期结果 |
|---|--------|---------|---------|
| IT1 | 字号属性精确值 | `etEditor.textSize` 属性读取 | 与 ViewModel 计算的 textSize 一致 |
| IT2 | 光标位置 | `etEditor.selectionEnd` 属性读取 | 追加/撤销/重做后 == text.length |
| IT3 | 按钮 enabled 状态 | `btnUndo.isEnabled` 属性读取 | 与 ViewModel canUndo 一致 |

---

*共: 31 项单元测试 + 42 项 UI 测试 + 3 项 Instrumentation 测试 = 76 项可测试项*
