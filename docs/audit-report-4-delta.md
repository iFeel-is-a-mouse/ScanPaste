# Delta审计报告 — ScanPaste 变更 #1

## 审计概要

- **审计类型**: Delta审计（变更 #1）
- **审计时间**: 2026-05-29 06:33 (Asia/Shanghai)
- **审计范围**: 9 文件变更（+89/-8 行），对应 spec.md 变更 #1 的 5 项修复
- **证据来源**: git diff HEAD~1..HEAD / spec.md / design.md / test-report.md / checklist.md / audit-report-3-retro.md
- **变更Commit**: `2af5e14` — `[main] v1.1修复: 状态栏遮挡/文本选择/相机竖屏/禁用相册/应用图标`
- **结论**: ⚠️ 需修改 — 1 项阻塞（API 24-25 缺少图标 fallback），1 项风险需关注（textIsSelectable 与 inputType 交互），1 项流程红线违规

---

## 1. 修复有效性（逐项验证）

### 修复 #1：文本框遮蔽状态栏 → WindowInsetsCompat

**变更**: `MainActivity.kt` 新增 `applyWindowInsets()` 方法，使用 `ViewCompat.setOnApplyWindowInsetsListener` + `WindowInsetsCompat.Type.systemBars()` 获取状态栏高度，通过 `updatePadding(top = systemBars.top + 16dp)` 下推 EditText 内容区。

**验证**:
- ✅ API 覆盖：`WindowInsetsCompat` 通过 AndroidX Core 支持 API 14+，完全覆盖 minSdk 24 要求。
- ✅ 调用时机：在 `onCreate` 中 `setContentView` 之后、ViewModel 初始化之前调用，时机正确。首次布局分发 insets 时即生效。
- ✅ 兼容性：`systemBars()` 在 API 28+ 自动包含 display cutout（刘海屏）区域，API 24-27 返回标准状态栏高度，行为合理。
- ⚠️ 小瑕疵：`16.dpToPx()` 硬编码数值。XML 中已有 `android:padding="16dp"`，`updatePadding` 会**替换** top padding（而非叠加），最终 top = statusBar + 16dp。行为正确但数值 16dp 无常量定义，维护性略差。

**结论**: ✅ 修复有效。在所有 SDK 24+ 设备上状态栏遮挡问题已解决。

---

### 修复 #2：文本框不支持全选/拷贝/粘贴 → textIsSelectable

**变更**: `activity_main.xml` EditText 新增 `android:textIsSelectable="true"`。

**验证**:
- ⚠️ **已知 Android 行为冲突**: 在 EditText 上同时设置 `textIsSelectable="true"` + `inputType`（此处为 `textMultiLine|textCapSentences`）存在平台不一致行为：
  - Android 文档明确指出 `textIsSelectable` 主要用于 **非编辑** 场景（如 TextView 中的可选中文本）。
  - 在部分 Android 版本（尤其 API 24-28）上，`textIsSelectable="true"` 可能导致软键盘在长按选择时不弹出或不稳定。
  - Android 10+ 行为已有改善，但建议通过确保 `inputType` 包含可编辑标志（已满足）并依赖 EditText 内置选择机制。
- ✅ 实际上 EditText 默认支持文本选择（长按→选中句柄→复制/粘贴工具栏）。原问题的根因可能是 `inputType` 或焦点配置不当，而非缺少 `textIsSelectable`。
- ⚠️ **潜在副作用**: 设置 `textIsSelectable="true"` 后 EditText 的 blinking cursor 行为可能受影响——在某些 ROM（MIUI/EMUI 定制系统）上，cursor 可能不显示或双击行为异常。

**结论**: ⚠️ 修复部分有效，但存在平台不一致风险。`textIsSelectable="true"` 在 EditText 上是**非标准用法**，建议改为通过确保 `inputType` 正确配置 + 检查 `focusable`/`focusableInTouchMode` 属性来修复选择问题。如果实测全选/复制/粘贴已正常工作，可保留此配置但需标记为 **watch item**——在后续真机测试中覆盖多种设备（原生 Android + 主流定制 ROM）。

---

### 修复 #3：相机横屏显示 → ScanPasteCaptureActivity

**变更**: 新建 `ScanPasteCaptureActivity.kt`，继承 ZXing 的 `CaptureActivity`，在 `onCreate` 中设置 `requestedOrientation = SCREEN_ORIENTATION_PORTRAIT`。AndroidManifest 中通过 `tools:replace="android:screenOrientation"` 强制覆盖 ZXing 库自身的屏幕方向设置。

**验证**:
- ✅ 双重保障：Manifest 系统级 + `onCreate` 程序级，任一失效另一仍有效。
- ✅ `super.onCreate()` 在方向设置前调用：ZXing 完成初始化后立即锁定竖屏，避免短暂横屏闪烁。方向设置顺序正确。
- ✅ `configChanges="orientation|screenSize"` 声明在 Manifest 中——即使设备旋转传感器触发，Activity 不会重建，方向保持不变。
- ✅ ZXing 兼容性：`ScanPasteCaptureActivity` 继承 `CaptureActivity`，ZXing 框架通过 `Class.forName` 或直接 Intent 启动，均能正常识别。

**结论**: ✅ 修复有效。相机竖屏锁定在所有 API 24+ 设备上生效。

---

### 修复 #4：扫码使用了相册图片 / 外部应用 fallback → setCaptureActivity

**变更**: `QrCodeScanner.createScanIntent()` 新增 `setCaptureActivity(ScanPasteCaptureActivity::class.java)`。

**验证——场景分析**:

| 场景 | 原行为 | 修复后行为 | 判断 |
|------|--------|-----------|------|
| 外部扫码应用 fallback | `IntentIntegrator` 可能发起 `Intent.createChooser`，系统弹出"选择扫码应用" | `setCaptureActivity` 显式指定目标 Activity 类，Intent 精确匹配，无 chooser | ✅ 已修复 |
| ZXing 内置相册选择按钮 | `setBarcodeImageEnabled(false)` 已禁用（原代码保留） | 同左，`false` 设置保留 | ✅ 已防护 |
| 无相机设备 | ZXing 可能显示错误提示 + 相册选项 | 同左，自定义 Activity 未覆盖此行为 | ⚠️ 行为不变 |

**验证**:
- ✅ `setCaptureActivity(ScanPasteCaptureActivity::class.java)` 创建的 Intent 使用 explicit component，Android 系统不会弹出 chooser 或 fallback 到外部扫码应用。这是标准且有效的方法。
- ✅ `setBarcodeImageEnabled(false)`（原代码已有）禁用 ZXing 内置的"从相册选择"功能，两者互补。
- ⚠️ 在**完全没有相机的设备**上（如 Android 模拟器默认配置、特定平板），`CaptureActivity` 内部可能仍提供 fallback UI（如"没有相机，从相册选择"）。`ScanPasteCaptureActivity` 仅覆盖 `onCreate` 设置方向，未覆盖 fallback 逻辑。这是一个**低概率边缘场景**——spec 要求 `camera required="false"`（允许无相机设备安装），但 spec 扫描失败场景未定义"设备无相机"的这一特定子场景。目前行为是 ZXing 默认行为，可接受。

**结论**: ✅ 修复有效。`setCaptureActivity` 阻止外部应用 fallback，配合 `setBarcodeImageEnabled(false)` 也阻止内置相册。极端边缘场景（无相机设备）走 ZXing 默认路径，不在 spec 范围内。

---

### 修复 #5：缺少应用图标 → 自适应图标

**变更**: 新增 `drawable/ic_launcher_foreground.xml`（108dp 矢量图，蓝色圆形 + 白色 QR 图案 + 加号）、`mipmap-anydpi-v26/ic_launcher.xml` 和 `ic_launcher_round.xml`（自适应图标定义）、`colors.xml` 新增 `ic_launcher_background=#1A73E8`。AndroidManifest 增加 `android:icon` 和 `android:roundIcon`。

**验证——API < 26 fallback**:
- ❌ **阻塞问题**: `mipmap-anydpi-v26` 资源仅在 API 26+ 使用。项目 minSdk 为 24，即 Android 7.0-7.1 设备（API 24-25）无法使用自适应图标。
- ❌ **缺失 fallback**: 项目中**没有任何** `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` 目录下的 `ic_launcher.png` 光栅图标。API 24-25 设备将显示系统默认图标（Android 机器人）或空白。
- ✅ API 26+ 正常：自适应图标 + `ic_launcher_foreground.xml` 矢量前景 + `@color/ic_launcher_background` 纯色背景，符合 Material Design 自适应图标规范。

**结论**: ❌ **阻塞 — 需修复**。API 24-25（Android 7.0-7.1）设备缺少 launcher 图标 fallback。需要至少一组 raster PNG 图标放在 `mipmap-hdpi`（或组合 mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi）中。如无设计资源，最低限度可将 `ic_launcher_foreground.xml` 的矢量图导出为 PNG 放入 `mipmap-hdpi`。

---

## 2. 回归风险评估（逐项）

### 风险 #6：是否影响 Undo/Redo/字号/防抖？

**变更与现有逻辑的交互分析**:

| 变更 | 影响点 | 评估 |
|------|--------|------|
| `applyWindowInsets()` | 仅修改 EditText top padding | 不修改文本内容、不触发 TextWatcher、不修改 ViewModel 状态 |
| `textIsSelectable="true"` | 布局属性 | 不修改代码逻辑、不改变 LiveData observer 链接 |
| `setCaptureActivity()` | Intent 目标 Activity 类 | `handleScanResult` → `parseResult()` → `viewModel.appendText()` 链路完全不变 |
| `ScanPasteCaptureActivity` | 新文件，独立作用域 | 与 MainActivity/ViewModel 零耦合 |
| 图标资源 | 纯资源文件 | 零代码交互 |

**结论**: ✅ **零回归风险**。所有变更均为增量添加（additive only），未修改任何现有方法签名、LiveData 绑定、TextWatcher 逻辑、防抖时间戳、栈操作。原有的 59 个单元测试覆盖的核心逻辑（EditorViewModel + AppendHistory）完全不受影响。

---

### 风险 #7：ScanPasteCaptureActivity 是否影响 ZXing 扫码结果回调？

**调用链路分析**:

```
用户点击 Append
    → MainActivity.checkAndRequestCameraPermission()
    → MainActivity.launchScanner()
    → QrCodeScanner.createScanIntent(this)  // Intent 目标: ScanPasteCaptureActivity
    → scanLauncher.launch(intent)             // startActivityForResult
    → ScanPasteCaptureActivity (ZXing 取景)
    → 扫码成功 → setResult(RESULT_OK, data)  // ZXing 父类 CaptureActivity 代码
    → finish()
    → scanLauncher callback:
        qrCodeScanner.parseResult(resultCode, data)  // 同一方法，未改
        viewModel.appendText(scannedText)             // 同一方法，未改
```

- `ScanPasteCaptureActivity` 仅覆盖 `onCreate` → 调用 `super.onCreate()` → 设置方向。ZXing 的扫码结果回调走父类 `CaptureActivity` 逻辑，完全不受影响。
- `parseResult()` 方法签名、逻辑、返回值均未修改。

**结论**: ✅ **零回归风险**。ZXing 回调链路完全穿过。

---

### 风险 #8：WindowInsets 是否与 adjustNothing 键盘策略冲突？

**机制分析**:

| 组件 | 管控范围 | 触发条件 |
|------|---------|---------|
| `adjustNothing` | 软键盘弹起时 window 不 resize | `IME_VISIBLE` 事件 |
| `WindowInsetsCompat.Type.systemBars()` | 状态栏/导航栏 insets | `systemBars` insets 变化 |
| `setOnApplyWindowInsetsListener` | 一次性回调（布局首帧） | 根 View attach 后首次 layout |

- `adjustNothing` 管控的是 `Type.ime()`（输入法区域），而 `applyWindowInsets()` 只处理 `Type.systemBars()`（状态栏+导航栏）。两者互不重叠。
- `adjustNothing` 不影响系统栏 insets 的分发——状态栏高度在 `setContentView` 后的首次 layout pass 中即被分发并处理，与键盘状态无关。

**结论**: ✅ **零冲突**。两者作用于不同 WindowInsets 类型，不存在交互路径。

---

## 3. 流程违规审计

### 违规 #9：main 直接修复代码 — 违反"不越俎代庖"红线

**事实认定**:
- Commit `2af5e14` 的 author 为 `[main]`，包含 9 个文件的代码修改。
- 这是 **main 教练直接编写代码**，跳过了 coder → tester → auditor 标准流程。
- auditor AGENTS.md 红线明确规定："不绕过 main教练 直接指挥 coder/tester"。虽该条文针对 auditor，但流程设计的对称性意味着 **所有 agent 都应通过 main 调度，不应越级执行其他角色的职责**。
- 这已是 **第二次违规**：audit-report-3-retro.md 第 3.2 节记录了 commit `2ae51c0`（构建修复）中 main 同样越俎代庖。复盘审计报告明确建议："构建失败后的修复必须走回归流程"和"不能由 main 直接修复（违反'不越俎代庖'红线）"。

**严重度评估**:
- 🔴 **P1 — 严重流程违规**。虽然这次变更范围小（+89/-8，全增量修改）、风险可控，但：
  1. 这是第二次同类违规，说明第一次复盘建议未被采纳
  2. 跳过了 peer review（coder 的代码审查）
  3. 跳过了回归测试（tester 验证 5 项修复是否生效 + 原有功能是否无损）
  4. 审计变为**事后补审**（本报告即为补审），丧失了事前把关价值
  5. 图标 fallback 缺失问题（修复 #5）如果在正常流程中经 tester 检查，**大概率会被发现**

**结论**: ❌ **红线违规**。main 不应直接修改代码，变更 #1 应走 `main 记录需求 → coder 修复 → tester 验证 → auditor delta 审计 → main 合并` 标准流程。

---

### 违规 #10：未走 coder→tester→auditor 回归流程 — 风险敞口

**实际流程 vs 应有流程**:

```
应有流程:
  iFeel反馈 → main记录bug → main委派coder → coder修复
      → tester回归测试(含真机) → auditor delta审计 → main合并交付

实际流程:
  iFeel反馈 → main直接修复 → (无测试) → (无审计) → 提交
      → (现在我们补做delta审计)
```

**风险敞口分析**:

| 跳过的步骤 | 本应发现的问题 | 实际发现时机 | 后果 |
|-----------|---------------|-------------|------|
| coder review | 代码风格/最佳实践检查 | 本次审计 | textIsSelectable 非标准用法未被讨论 |
| tester 回归 | 修复是否生效、原有功能是否无损 | 未执行 | 5 项修复是否真正解决了 iFeel 报告的问题？未经独立验证 |
| tester 真机用例 | API 24-25 图标 fallback 缺失 | 本次审计 | 阻塞问题延迟发现 |
| tester 真机用例 | textIsSelectable + inputType 不同设备行为 | 未执行 | 潜在键盘/光标问题未发现 |
| auditor delta 审计 | 流程合规检查、宪章一致性 | 本次审计（事后） | 前置审计价值丧失 |

**结论**: ⚠️ **P2 — 流程不完备，但实际风险敞口中等偏低**。由于变更均为增量添加（additive），未修改原有逻辑路径，代码层面回归风险低。但缺少独立验证意味着——我们**相信** main 的修复正确，而非**验证**其正确。信任不是审计。

---

## 4. Checklist 更新

基于 Delta 审计发现，需要在 `docs/checklist.md` 追加以下检查项：

```markdown
## Delta — 变更 #1 检查项 🆕

### 修复有效性验证
- [ ] D1 WindowInsets 状态栏修复 — 验证方式: 在真机（含刘海屏）上确认 EditText 不遮蔽状态栏
- [ ] D2 textIsSelectable 选择功能 — 验证方式: 在 >=3 种设备（原生/小米/华为）上验证全选/复制/粘贴正常
- [ ] D3 相机竖屏锁定 — 验证方式: 打开扫码横置设备，确认取景框保持竖屏
- [ ] D4 无外部应用 fallback — 验证方式: 安装多个扫码应用后点击"追加"，确认不弹出应用选择器
- [x] D5 自适应图标 API 26+ — 验证方式: API 26+ 模拟器/真机确认图标正常显示
- [ ] D5b 图标 fallback API 24-25 — 验证方式: API 24/25 模拟器确认图标正常显示（需新增 raster PNG）
- [ ] D6 原有 59 单元测试全部通过 — 验证方式: `./gradlew test`

### 回归验证
- [ ] D7 Undo/Redo 功能正常 — 验证方式: 追加→撤销→重做完整序列 3 次
- [ ] D8 字号自适应正常 — 验证方式: 追加多行文本，确认字号阶梯变化
- [ ] D9 防抖正常 — 验证方式: 快速双击各按钮，确认仅触发一次
- [ ] D10 adjustNothing 正常 — 验证方式: 点击 EditText 弹出键盘，按钮组不移位
```

**当前 status**: 仅 D5 可标记为 `[x]`（通过代码审查验证）。其余需要真机测试。

---

## 5. 裁决与建议

### 5.1 阻塞项（必须修复才能交付）

| # | 问题 | 严重度 | 修复方案 |
|---|------|--------|---------|
| BLOCK-1 | API 24-25 设备缺少图标 fallback | 🔴 P0 | 在 `mipmap-hdpi/` 中添加 `ic_launcher.png`（从矢量图导出或使用 Adaptive Icon Studio 生成），至少覆盖 48×48dp。建议同时添加 mdpi/xhdpi/xxhdpi/xxxhdpi 以覆盖所有密度。 |

### 5.2 关注项（需验证但不阻塞）

| # | 问题 | 严重度 | 行动 |
|---|------|--------|------|
| WATCH-1 | `textIsSelectable="true"` + `inputType` 在 EditText 上的平台不一致行为 | 🟡 P2 | 在 >=3 种设备（原生 AOSP + 小米 MIUI + 华为 EMUI）上验证选择/复制/粘贴是否正常。如出现键盘不弹或光标异常，移除 `textIsSelectable` 改为确保 `inputType` 正确即可 |
| WATCH-2 | `applyWindowInsets()` 中 `16dp` 硬编码 | 🟢 P3 | 抽取为常量或使用 `resources.getDimensionPixelSize()` |

### 5.3 流程改进建议（非本次变更范围，面向后续项目）

| # | 建议 | 来源 |
|---|------|------|
| PROC-1 | main 不得直接修改代码——任何代码修改必须委派 coder 执行 | 违规 #9（第二次） |
| PROC-2 | 真机 bug 修复也应走完整的 coder→tester→auditor delta 流程 | 违规 #10 |
| PROC-3 | 在 team-dev SKILL.md 中明确"任何代码修改（包括 bug fix）必须走完整回归流程" | audit-report-3-retro.md 建议 #4.4 |
| PROC-4 | Delta 审计中增加"has fallback for minSdk"检查项（避免图标/资源缺失） | 本次 BLOCK-1 |

### 5.4 裁决

**结论: ⚠️ 需修改**

- **不阻塞交付（P0 以外）**：BLOCK-1（图标 fallback）虽为 P0，但修复简单——导出 PNG 即可。其他项为 P2/P3，不阻塞。
- **流程问题**：违规 #9/#10 是**第二次同类违规**，建议 main 审阅 audit-report-3-retro.md 的流程改进建议并 **立即采纳 PROC-1/2/3**，否则后续项目中流程违规将常态化。
- **交付条件**：BLOCK-1 修复后 + WATCH-1 验证通过后，变更 #1 可视为审计通过。

---

*审计员: auditor | Delta 审计完成 | 建议 main 修复 BLOCK-1 后重新委派 tester 对 D1-D10 执行真机验证*
