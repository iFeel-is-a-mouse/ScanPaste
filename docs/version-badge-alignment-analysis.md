# 版本号(v3.1)与标题(扫码笔记)上对齐问题分析报告

## 1. 当前布局代码还原

```xml
<com.google.android.material.appbar.MaterialToolbar
    android:layout_height="48dp"
    android:minHeight="48dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="match_parent"   <!-- 48dp -->
        android:layout_gravity="start"
        android:gravity="top"
        android:orientation="horizontal"
        android:paddingBottom="6dp">

        <!-- 标题: 20sp bold -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="扫码笔记"
            android:textSize="20sp"
            android:textStyle="bold" />

        <!-- 版本号: 7sp, 需要 -13dp 才能上对齐 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="4dp"
            android:translationY="-13dp"
            android:text="v3.1"
            android:textSize="7sp" />

    </LinearLayout>
</MaterialToolbar>
```

---

## 2. 为什么需要 Y=-13dp？—— 根因分析

### 2.1 表面现象：LinearLayout `gravity=top` 没让两个 TextView 的"上沿"对齐

直觉上，`LinearLayout` 设置了 `gravity=top`，子视图应该顶部对齐。但**Android 的 "顶部" 指的是 TextView 的 bounds 上沿（即 FontMetrics.top），而不是人眼感知的 "文字上沿"（cap height / ascent 区域）**。

这是两个完全不同的概念：

| 概念 | 含义 | 位置 |
|------|------|------|
| **Bounds Top** | TextView 组件的物理上边界 | `FontMetrics.top`（含字体预留空间） |
| **Cap Height** | 大写字母实际视觉顶部（如 A、V） | 介于 `ascent` 和 `top` 之间 |
| **Ascent** | 字体设计的上沿（大部分字符最高点） | `FontMetrics.ascent` |

### 2.2 核心根因：FontMetrics 绝对值差异 + includeFontPadding

Android TextView 默认 `includeFontPadding=true`，其 `wrap_content` 高度包含完整的字体 metrics：

```
TextView 高度 = |top| + bottom
             = 字体最高预留位 + 字体最低预留位
```

当两个 TextView 的 bounds top 对齐时，由于字号不同（20sp vs 7sp），**font metrics 的绝对像素值不同**，导致视觉上的 cap height 不在同一水平线：

#### 估算（Roboto 字体，密度无关像素 dp）

| 字号 | `top` 绝对值 | `ascent` 绝对值 | cap height | cap-top 落差 |
|------|-------------|----------------|------------|-------------|
| **20sp** | ~22px (≈22dp) | ~18px | ~14px | **22-14 = 8px** |
| **7sp** | ~7.7px (≈7.7dp) | ~6.3px | ~4.9px | **7.7-4.9 = 2.8px** |
| **落差** | — | — | — | **≈5.2px** |

但这只解释了约 **5dp** 的差异。剩下的 **~8dp** 来自哪里？

### 2.3 关键放大因素：20sp bold 的额外 font padding

**`textStyle="bold"`** 对 "扫码笔记" 20sp 的 TextView 有额外影响：

- Bold 字体的 `top` 预留比 normal 字体更大（粗体字符可能超出 normal 的 ascent）
- 20sp bold 的 `|top|` 可能达到 **~28-30px**，而 7sp normal 的 `|top|` 只有 **~7.7px**
- 两者 cap-top 落差因此扩大到 **~13dp**

加上 MaterialToolbar 的默认 `contentInsetTop` 可能有 **0-2dp** 的偏差，最终需要约 **-13dp** 的补偿。

### 2.4 验证：为什么不是 baseline 对齐问题？

如果是 baseline 对齐（LinearLayout 默认 `baselineAligned=true`），20sp 的 cap height 会比 7sp 高 **~9.1dp**（14px - 4.9px）。但代码中是 **向上** 移动 7sp（-13dp），说明当前确实不是 baseline 对齐——而是 **top bounds 对齐** 后，由于 font metrics 绝对值差异，视觉上 7sp 的 "上沿" 比 20sp 的 **更低**（需要向上提）。

### 2.5 总结：-13dp 的来源公式

```
needed_translationY ≈ (|top_20sp_bold| - capHeight_20sp) - (|top_7sp| - capHeight_7sp)
                    ≈ (28 - 14) - (7.7 - 4.9)
                    ≈ 14 - 2.8
                    ≈ 11.2dp + toolbar/padding 误差
                    ≈ 13dp
```

---

## 3. 是否是字体本身的 ascent/descent 导致的视觉偏差？

**是的，但根本原因是 `includeFontPadding=true` 的默认行为。**

Android 的 TextView 默认会为字体预留完整的 `top` 和 `bottom` 空间（以容纳 Å、j、y 等极端字符）。这意味着：

- **20sp bold** 的 `top` 预留空间绝对值很大（可能 28-30px）
- **7sp normal** 的 `top` 预留空间绝对值很小（约 7.7px）

当两者 bounds top 对齐时，20sp 的 "A" 顶部（cap height）距离 bounds top 有 **~14px** 的空白，而 7sp 的 "v" 顶部只有 **~2.8px** 的空白。视觉上 7sp 的 "v" 看起来更低，需要向上补偿。

**这不是 bug，而是 Android 字体渲染机制的设计。**

---

## 4. 更好的对齐方案

### 方案 A：关闭 includeFontPadding（推荐 ⭐）

```xml
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="扫码笔记"
    android:textSize="20sp"
    android:textStyle="bold"
    android:includeFontPadding="false" />   <!-- 关键改动 -->

<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginStart="4dp"
    android:text="v3.1"
    android:textSize="7sp"
    android:includeFontPadding="false" />   <!-- 关键改动 -->
```

**效果**：TextView 高度只包含 `ascent + descent`，不含额外的 `top/bottom` 预留。两者的 cap height 落差从 **~13dp** 缩小到 **~2-3dp**，可能完全不需要 `translationY`。

**风险**：如果版本号包含下降字符（如 g, y, j），可能超出 TextView bounds，被截断。

---

### 方案 B：使用 baseline 对齐（适合"底部对齐"需求）

如果业务需求实际上是 **"版本号的 baseline 与标题的 baseline 对齐"**（即 "v" 的底和 "扫" 的底在一条线上）：

```xml
<LinearLayout
    android:layout_width="wrap_content"
    android:layout_height="match_parent"
    android:gravity="bottom"          <!-- 改为 bottom -->
    android:orientation="horizontal">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="扫码笔记"
        android:textSize="20sp"
        android:textStyle="bold" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="v3.1"
        android:textSize="7sp" />
        <!-- 去掉 translationY，baseline 自动对齐 -->
</LinearLayout>
```

**效果**：LinearLayout 默认 `baselineAligned=true`，两个 TextView 的 baseline 会自动对齐。视觉上 "v" 的底部和 "扫" 的底部在一条线上，但 "v3.1" 的顶部会低于 "扫码笔记" 的顶部。

---

### 方案 C：使用 ConstraintLayout + baseline 约束（最精确）

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="wrap_content"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="扫码笔记"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/version"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="v3.1"
        android:textSize="7sp"
        app:layout_constraintTop_toTopOf="@id/title" />  <!-- 顶部严格对齐 -->
</androidx.constraintlayout.widget.ConstraintLayout>
```

**效果**：ConstraintLayout 的 `top_toTopOf` 将两个 TextView 的 bounds top 严格对齐。但和当前方案一样，仍然会有 font metrics 导致的视觉差异。

**改进**：结合 `includeFontPadding="false"`：

```xml
<TextView
    android:id="@+id/title"
    ...
    android:includeFontPadding="false"
    app:layout_constraintTop_toTopOf="parent" />

<TextView
    android:id="@+id/version"
    ...
    android:includeFontPadding="false"
    app:layout_constraintTop_toTopOf="@id/title" />
```

---

### 方案 D：使用 FirstBaseline / LastBaseline 对齐（真正的视觉对齐）

如果目标是**大写字母的顶部严格对齐**（cap height 对齐），可以使用自定义 View 或计算偏移量。但对于简单场景，推荐：

```xml
<!-- 去掉 LinearLayout 的 gravity，让子视图自行控制 -->
<LinearLayout
    android:layout_width="wrap_content"
    android:layout_height="match_parent"
    android:orientation="horizontal">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="扫码笔记"
        android:textSize="20sp"
        android:textStyle="bold"
        android:includeFontPadding="false" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="v3.1"
        android:textSize="7sp"
        android:includeFontPadding="false"
        android:translationY="-2dp" />  <!-- 微调即可 -->
</LinearLayout>
```

---

## 5. 方案对比

| 方案 | 改动量 | 效果 | 风险 | 推荐度 |
|------|--------|------|------|--------|
| **当前方案** | 仅加 `-13dp` | 能工作，但 magic number | 脆弱，换字体/字号后失效 | ⭐⭐ |
| **A: 关 fontPadding** | 两个 TextView 加属性 | 落差从 13dp→2dp，可能无需 translationY | 极端字符可能被截断 | ⭐⭐⭐⭐ |
| **B: baseline 对齐** | 改 gravity=bottom | 底部对齐，顶部不对齐 | 不符合"上沿对齐"需求 | ⭐⭐⭐ |
| **C: ConstraintLayout** | 换布局 + 关 fontPadding | 最精确，最可控 | 改动较大 | ⭐⭐⭐⭐⭐ |
| **D: 微调 translationY** | 关 fontPadding + -2dp | 保留微调，但基于合理计算 | 仍依赖 magic number | ⭐⭐⭐⭐ |

---

## 6. 结论与建议

### 根因
- **不是** LinearLayout gravity 失效
- **不是** 字体本身的 ascent/descent 比例不同（比例是相同的）
- **是** `includeFontPadding=true` 导致不同字号 TextView 的 **font metrics 绝对值差异** 被放大
- **是** 20sp bold 与 7sp normal 的 `top` 预留空间差异巨大（约 20px vs 7px），导致 cap height 视觉落差约 **13dp**

### 推荐方案

**首选：方案 C（ConstraintLayout + includeFontPadding="false"）**

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="wrap_content"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="扫码笔记"
        android:textSize="20sp"
        android:textStyle="bold"
        android:textColor="@color/md_on_primary"
        android:includeFontPadding="false"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

    <TextView
        android:id="@+id/version"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="v3.1"
        android:textSize="7sp"
        android:textColor="#80FFFFFF"
        android:includeFontPadding="false"
        app:layout_constraintTop_toTopOf="@id/title" />
        <!-- 去掉 translationY，靠约束对齐 -->
</androidx.constraintlayout.widget.ConstraintLayout>
```

**备选：方案 A（最小改动）**

在现有 LinearLayout 基础上，仅给两个 TextView 加 `android:includeFontPadding="false"`，然后移除 `translationY="-13dp"`，观察是否满足视觉需求。如果仍有微小偏差，使用 `-2dp` 微调代替 `-13dp`。

---

## 7. 参考

- Android FontMetrics: https://developer.android.com/reference/android/graphics/Paint.FontMetrics
- TextView includeFontPadding: https://developer.android.com/reference/android/widget/TextView#attr_android:includeFontPadding
- LinearLayout gravity vs layout_gravity: https://developer.android.com/reference/android/widget/LinearLayout#attr_android:gravity
