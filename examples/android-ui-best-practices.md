# Android UI 设计最佳实践 — 供 coder 参考

> 来源: Android 官方文档 + Material Design 3 指南 + 搜索结果
> ⚠️ 辩证批判地使用：这些模式适合大多数场景，但需根据本项目具体需求调整

---

## 1. 状态栏透明 + Toolbar 集成模式

### 原理
- Android 5.0+ 支持 `statusBarColor=transparent`
- 使用 `WindowCompat.setDecorFitsSystemWindows(window, false)` 启用 edge-to-edge
- Toolbar 设 `fitsSystemWindows=true`，内容自动在状态栏下方，但背景延伸到状态栏后

### 代码示例 (themes.xml)
```xml
<style name="Theme.App" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>
```

### 代码示例 (Activity)
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 关键：让内容绘制到系统栏后面
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContentView(binding.root)
}
```

### ⚠️ 批判要点
- `fitsSystemWindows=true` 只在特定 ViewGroup（CoordinatorLayout, AppBarLayout等）自动生效
- 普通 ConstraintLayout 需手动用 WindowInsetsCompat 处理 padding
- **本项目建议**：Toolbar 作为独立组件设 `fitsSystemWindows=true`，根布局用 ConstraintLayout

---

## 2. 键盘自适应 + 按钮组固定底部模式

### 原理
- `adjustResize` 让布局随键盘缩放
- 底部按钮组约束到 `parent` 底部 → 始终在屏幕底部
- 内容区约束在 Toolbar 和按钮组之间 → 键盘弹起时缩小

### 布局结构
```xml
<ConstraintLayout>
    <!-- 状态栏背景色块 (延伸到状态栏后) -->
    <View app:layout_constraintTop_toTopOf="parent"
          android:background="@color/primary" />

    <!-- Toolbar (fitsSystemWindows 处理状态栏避让) -->
    <MaterialToolbar app:layout_constraintTop_toTopOf="parent"
                     android:fitsSystemWindows="true"
                     android:background="@android:color/transparent" />

    <!-- 可滚动内容区 (在 Toolbar 和按钮组之间) -->
    <ScrollView app:layout_constraintTop_toBottomOf="@id/toolbar"
                app:layout_constraintBottom_toTopOf="@id/buttonBar" />

    <!-- 底部按钮组 (约束到 parent 底部) -->
    <LinearLayout app:layout_constraintBottom_toBottomOf="parent" />
</ConstraintLayout>
```

### ⚠️ 批判要点
- `adjustResize` 在 Android 11+ 需配合透明导航栏
- 部分 OEM（小米/华为）对 `adjustResize` 有定制行为
- **本项目建议**：测试时需在真机上验证

---

## 3. Material Design 3 色彩系统

### 原理
MD3 使用动态色彩 Token，但静态色板也完全可用

### 推荐色彩方案
```xml
<!-- 蓝色主色调 (匹配 App 图标) -->
<color name="primary">#1A73E8</color>
<color name="on_primary">#FFFFFF</color>
<color name="surface">#FFFFFF</color>
<color name="on_surface">#1C1B1F</color>
<color name="background">#FAFAFA</color>
```

### ⚠️ 批判要点
- MD3 推荐 tonal palette 生成工具，但简单 App 用固定色值更直接
- 蓝色 Toolbar + 白色内容区 是经典组合，用户熟悉度高

---

## 4. 扫码入口 UX 模式

### 原理
- 主操作（拍照扫码）应一步直达，不需要中间弹窗
- 次要操作（相册选择）通过长按或小图标入口

### 推荐方案
```
┌──────────────────────────────┐
│                    [🖼️相册]  │  ← 小图标，走相册
│      [📷 拍照扫码]           │  ← 大按钮，走相机（默认）
│                              │
│  [取消]  [重做]  [追加]      │
└──────────────────────────────┘
```

更好的方案：追加按钮直接拍照，旁边加相册小图标
```
[取消]  [重做]  [ 追加 ▼ ]  [🖼️]
                     ↑            ↑
                 点击=拍照    点击=相册
```

### ⚠️ 批判要点
- 主路径必须零思考——点追加=拍照
- 相册入口够小不干扰主路径，够大能点到

---

## 5. 常用间距与字体规范

| 元素 | 推荐值 |
|------|--------|
| 水平边距 | 16dp |
| 列表项间距 | 8dp |
| 标题字号 | 20sp |
| 正文字号 | 16sp |
| 说明字号 | 14sp |
| 最小触控区域 | 48dp |
| 按钮栏高度 | 56dp |
| 状态栏高度 | 24dp (自动) |
| 导航栏高度 | 48dp (自动) |
