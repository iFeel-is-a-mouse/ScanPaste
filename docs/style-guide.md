# ScanPaste — 代码风格指南

## 1. 语言与平台

| 项目 | 选择 |
|------|------|
| 语言 | Kotlin |
| 最低SDK | API 24 |
| compileSdk | 35 |
| 目标SDK | API 35 |
| 构建工具 | Gradle 8.14 Kotlin DSL (`build.gradle.kts`) |
| AGP | 8.7.3 |
| Kotlin | 2.1.20 |

## 2. 命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `MainActivity`, `ScanViewModel` |
| 函数名 | camelCase | `onUndoClicked()`, `appendScannedText()` |
| 变量名 | camelCase | `appendHistory`, `currentText` |
| 常量 | UPPER_SNAKE | `MAX_UNDO_STACK`, `MIN_TEXT_SIZE` |
| 资源ID | snake_case | `btn_undo`, `et_text_area` |
| 布局文件 | snake_case | `activity_main.xml` |
| 包名 | 全小写 | `com.scanpaste.app` |

## 3. 代码组织

```
src/main/java/com/scanpaste/app/
├── MainActivity.kt              # 主Activity
├── RabbitJumpActivity.kt        # 彩蛋游戏Activity
├── scanner/
│   ├── QrCodeScanner.kt         # 二维码扫描封装
│   └── ScanPasteCaptureActivity.kt  # 自定义ZXing取景框
├── model/
│   └── AppendHistory.kt         # 追加历史栈
├── viewmodel/
│   └── EditorViewModel.kt       # 编辑状态管理
└── widget/
    ├── QuickNoteWidgetProvider.kt   # 桌面小部件Provider
    └── QuickNoteWidgetService.kt    # 桌面小部件Service
src/main/assets/
└── rabbitjump.html              # 彩蛋游戏HTML
```

## 4. 格式规范

- 缩进: 4空格
- 行宽: 120字符
- 文件编码: UTF-8
- 注释语言: 英文（代码注释），中文（文档）
- trailing comma: 启用

## 5. 依赖管理

- 使用 Version Catalog (`libs.versions.toml`) 统一管理版本
- 最小化依赖：不引入不必要的库
- 扫码库：ZXing Embedded（`com.journeyapps:zxing-android-embedded`），纯离线，无需网络
- 禁止引入 ML Kit、CameraX 等大型 Google 库（与纯离线目标冲突）

## 6. 架构原则

- 单 Activity 架构
- 必须使用 ViewModel 管理 UI 状态（Undo/Redo 栈、文本状态、撤销失败事件）
- Undo/Redo 逻辑使用独立数据类，不耦合UI
- 相机扫码逻辑独立封装

## 7. 禁止事项

- ❌ 使用 `!!` 强制解包 → 用 `?.let {}` 或 `?:` 替代
- ❌ 硬编码字符串 → 使用 `strings.xml`
- ❌ 在主线程做IO操作
- ❌ 忽略相机权限检查
- ❌ 使用 deprecated API
- ❌ release build 未启用 minifyEnabled（需配置 R8 + proguard-rules.pro）

## 8. 测试规范

- 单元测试覆盖 Undo/Redo 栈逻辑
- 使用 JUnit4 + Truth assertions（Android 原生兼容）
- 测试文件放 `src/test/`
