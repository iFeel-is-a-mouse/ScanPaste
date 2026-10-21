# Positioning and alternatives

This note explains what ScanPaste is for and what it deliberately does not do.
It is background for maintainers and reviewers, not marketing copy.

中文版见下半部分。

## Intended use

ScanPaste moves small amounts of text between a screen and a note: a few KB,
maybe tens of KB. Typical cases:

- Text that exists only as a QR code on a screen, and needs to end up in a file.
- Several codes that belong together, scanned one after another into one note.
- A machine with no usable network or no account to sign in with.
- Text that should stay available after the scan, not disappear with the
  scanner's result screen.

The app is a single editor with a scan button. Scanned text is appended to the
note, where it can be edited, undone, and saved as `txt`, `md`, `csv`, or
`json`. A quick note and a home-screen widget keep the latest text one tap away.

## What it is not

- Not a general note app. There is no notebook hierarchy, no tags, no sync.
- Not a sync service. No account, no cloud history, no `INTERNET` permission.
- Not a high-speed or high-volume transfer tool. It is not meant for MB-scale
  payloads, animated multi-frame codes, or continuous streaming.

## Alternatives and where they fall short

Described neutrally; no product names, since features change.

| Alternative | What it does well | Gap for this use case |
|---|---|---|
| Stock camera or system scanner | Fast one-off scan, built in, no install | Shows a result once; no note, no append, no file. Several codes mean copy-and-paste each one by hand. |
| General note apps | Good editing, organization, sometimes sync | Usually no built-in scan-to-append; scanning means leaving the app and pasting back. |
| QR file-transfer tools | Move larger payloads, sometimes multi-frame | Aimed at bulk transfer between two devices; heavier than needed for a few KB of text. |
| Clipboard sync tools | Seamless across devices | Need a network and usually an account; the text does not stay in a note. |
| Manual retyping | No tools at all | Slow and error-prone for anything beyond a short string. |

The gap ScanPaste fills is narrow: scan a few codes, keep the text in an
editable note, with no network and no account.

## When not to use it

- You need to move megabytes, or a whole file.
- You need automatic cross-device sync.
- You need long-term note organization: folders, search, tags.
- You need to scan codes without a camera or gallery image — there is no
  clipboard-listening or background scanning.

## Relevance to F-Droid

F-Droid reviewers ask what an app is for and whether it duplicates existing
functionality. The honest answer: it overlaps with scanners, note apps, and
clipboard tools at the edges, but none of them covers scan-to-append text
capture in a persistent, offline note. It is a small tool for a specific
workflow, not a replacement for a general note app.

---

# 定位与替代方案

这份文档说明 ScanPaste 是做什么的、刻意不做什么。给维护者和审核人看，不是宣传材料。

## 适用场景

ScanPaste 搬运少量文字：几 KB，最多几十 KB。典型情况：

- 文字只存在于屏幕上的二维码里，最后要落到一个文件里。
- 好几个码属于同一份内容，需要连着扫进同一篇笔记。
- 机器上没有可用网络，或者没有可登录的账号。
- 扫完的文字要留得住，而不是随扫码器的结果页一起消失。

应用就是一个编辑框加一个扫码按钮。扫到的文字追加到笔记末尾，可以编辑、撤销，
存成 `txt`、`md`、`csv` 或 `json`。速记和桌面小部件让最近的文字随时够得着。

## 它不是什么

- 不是通用笔记应用：没有笔记本层级、没有标签、没有同步。
- 不是同步服务：没有账号、没有云历史、不申请 `INTERNET` 权限。
- 不是高速或大流量传输工具：不面向 MB 级数据、动态多帧二维码或持续流式传输。

## 替代方案与不足

中性描述，不点名具体产品，因为功能会变。

| 替代方案 | 擅长什么 | 在这个场景下的不足 |
|---|---|---|
| 系统相机或系统扫码器 | 一次性扫码快，系统自带，不用装 | 结果只显示一次；没有笔记、没有追加、不落文件。多个码得逐个复制粘贴。 |
| 通用笔记应用 | 编辑、组织能力强，部分带同步 | 通常没有内置扫码追加，扫码要离开应用再粘回来。 |
| 二维码传文件工具 | 传更大数据，有的支持多帧 | 面向两台设备之间的大流量传输，搬几 KB 文字显得重。 |
| 剪贴板同步工具 | 跨设备无缝 | 要联网，通常要账号；文字不会留在一篇笔记里。 |
| 手工重打 | 不需要任何工具 | 稍长一点就慢且容易出错。 |

ScanPaste 填的空很窄：扫几个码，把文字留在可编辑的笔记里，不联网、不要账号。

## 什么情况下不该用它

- 要搬 MB 级数据或整个文件。
- 需要自动跨设备同步。
- 需要长期笔记管理：文件夹、搜索、标签。
- 需要在没有相机或相册图片的情况下取码：它不监听剪贴板，也不做后台扫描。

## 与 F-Droid 的关系

F-Droid 审核人会问这个应用是做什么的、是否与已有功能重复。诚实的回答是：它在边缘
上与扫码器、笔记应用、剪贴板工具都有重叠，但没有一个覆盖"扫码追加到持久、离线
笔记"这件事。它是一个针对特定流程的小工具，不是通用笔记应用的替代品。
