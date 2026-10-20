<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="96" alt="ScanPaste icon">
</p>

# ScanPaste · 扫码笔记

[![GitHub](https://img.shields.io/badge/GitHub-iFeel--is--a--mouse%2FScanPaste-181717?logo=github)](https://github.com/iFeel-is-a-mouse/ScanPaste)

ScanPaste is a small offline QR code notebook for Android. Scan a code, and the
decoded text is appended to the note you are editing. No account, no sync, no
network permission.

**中文简介：** 扫码笔记是一款纯离线的二维码记录工具。扫描二维码后，文字会自动追加到编辑区。支持撤销/重做、复制、保存、速记和桌面小部件，不联网，也不收集数据。

## 中文要点

- **扫码追加**：拍照或从相册选图，二维码内容追加到文末。
- **编辑**：全选、复制、剪切、粘贴；长文本可滚动。
- **撤销/重做**：每次编辑、追加、清空都可回退/重做，最多 200 步；连续输入 500ms 内合并为一步。
- **保存与速记**：可另存为 txt、md、csv、json 等文件；速记保存在应用私有目录，可用「保存」导出到 Documents。
- **桌面小部件**：展示速记内容，可缩放、滚动，点击打开应用。
- **纯离线**：ZXing 在设备本地解码，应用没有 INTERNET 权限。
- **系统要求**：Android 7.0（API 24）及以上。

## Features

- **Scan and append** — scan with the camera or pick an image from the gallery.
  Photos of screens are retried with a 3×3 median filter. The decoded text is
  appended to the end of the note.
- **Editing** — select all, copy, cut, and paste. Fixed 18sp text, scrollable for
  long notes.
- **Undo and redo** — every edit, append, and clear can be undone and redone,
  up to 200 steps. Consecutive typing within 500 ms is grouped into one step.
- **Copy** — copy the whole note to the clipboard in one tap.
- **Save as file** — choose a filename and extension (txt, md, csv, json, and so
  on). Files go to Documents by default.
- **Quick note and quick read** — the quick note lives in app-private storage and
  is loaded automatically when the app starts.
  If it exists, the app asks before overwriting and keeps five backups
  (`quicknote-1.txt` … `quicknote-5.txt`). Use **Save** to export a copy to
  Documents; **Quick read** can import an existing text file.
- **Home screen widget** — resizable, scrollable, rounded, and slightly
  translucent. It shows the quick note and opens the app on tap.
- **Offline by design** — QR decoding runs on-device with ZXing. The app
  declares no `INTERNET` permission.
- **Easter egg** — tap the title to play Rabbit Jump. The characters and caption
  can be changed in `game.txt`.

## Install

### F-Droid

<!-- F-Droid badge. Uncomment and keep once the listing is live:
<a href="https://f-droid.org/packages/com.scanpaste.app/">
  <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
</a>
-->

The F-Droid listing is in preparation. Until it is published, install the APK
from GitHub Releases.

### GitHub Releases

Download the latest APK from
[Releases](https://github.com/iFeel-is-a-mouse/scanpaste/releases) and open it on
a device running Android 7.0 (API 24) or newer. You may need to allow installs
from unknown sources for your browser or file manager.

The app asks for camera permission only when you scan. Scanning an image from
the gallery does not need the camera.

## Usage

1. Type in the editor, or tap **扫码 (Scan)** to add text from a QR code.
2. Choose **拍照扫码 (Camera)** or **从相册选择 (Gallery)**.
3. Edit the note as needed. **复制 (Copy)**, **撤销 (Undo)**, and **重做 (Redo)**
   are in the bottom bar.
4. Tap **保存 (Save)** to write a file, or **速记 (QuickS)** to update the
   app-private quick note. Use Save to export a copy to Documents.
   **Quick read replaces the editor with the saved quick note; save unsaved
   text first.**
5. Long-press the home screen and add the ScanPaste widget to see the quick note
   without opening the app.

## Files and data

Quick note files are kept in app-specific external storage:

`Android/data/com.scanpaste.app/files/Documents/scanpaste/`

On Android 11+ other apps cannot read this directory. On Android 10 and below,
apps that hold storage permission may still be able to read it.

| File | Purpose | Format |
|------|---------|--------|
| `quicknote.txt` | Quick note content | Plain text |
| `quicknote-1.txt` … `quicknote-5.txt` | Backups made before a quick note overwrite (empty slots first, then the oldest) | Plain text |

Rabbit Jump files are kept in `Documents/scanpaste/` (Android 10+):

| File | Purpose | Format |
|------|---------|--------|
| `best.txt` | Rabbit Jump high score | `level,date time` |
| `game.txt` | Rabbit Jump characters and caption | 3 lines: player emoji, obstacle emoji, caption |

These files are removed when the app is uninstalled. Android backup is disabled;
use **Save** to export a copy to Documents before uninstalling or switching
devices. On first **Quick read**, the app looks for an old
`Documents/scanpaste/quicknote.txt` and offers to import it. Only the main file
is imported; old backups remain in Documents until you delete them.

### Customizing `game.txt`

The game reads the rabbit, the fox, and the caption from `game.txt`. Edit any
line and restart the game:

```
🐰
🦊
The cute rabbit jumps over the lazy fox
```

The file is created on first launch. Delete it and restart the game to restore
the defaults.

## Privacy

ScanPaste is designed to work without a network.

- The app has no `INTERNET` permission. The manifest explicitly removes it.
- There are no analytics, ads, crash reporters, or accounts.
- Camera frames are decoded on-device and are not stored by the app.
- Gallery images are read only to decode the selected image.
- Quick notes are stored in app-specific external storage; use **Save** to
  export a copy to Documents. Unsaved text stays in memory. Cloud backup is disabled
  (`allowBackup="false"`). Device-to-device migration depends on the OEM;
  export a copy before switching devices.
- Uninstalling the app deletes the quick note and its backups in app-specific
  storage.
  Files you exported to Documents are not deleted.

## Build from source

Requirements:

- JDK 17 or newer
- Android SDK Platform 35 and Build-Tools 35.0.0
- The Gradle wrapper included in the repository

Install the SDK with Android Studio's SDK Manager, or with the Android
command-line tools. Then point the build at your SDK, either with the
`ANDROID_HOME` environment variable or with `sdk.dir` in `local.properties`:

```properties
sdk.dir=/path/to/your/android-sdk
```

Build a debug APK:

```bash
git clone https://github.com/iFeel-is-a-mouse/scanpaste.git
cd scanpaste
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For a release build:

```bash
./gradlew assembleRelease
```

Release builds can use a local signing key. Create `keystore.properties` in the
project root with the `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`
entries, and keep the file and the keystore out of version control. Without that
file, `assembleRelease` produces an unsigned APK.

## Version history

| Version | Date | Changes |
|---------|------|---------|
| v5.9 | 2026-10-20 | 相册扫码增加 3×3 中值滤波重试；解码移到后台线程 |
| v5.8 | 2026-10-19 | 连续输入合并；撤销快照约 4MB 预算；关闭系统原生撤销避免冲突 |
| v5.7 | 2026-10-19 | 全文 undo/redo；启动自动读速记；英文按钮 Note → QuickS |
| v5.6 | 2026-10-19 | 速读/导入速记后重置撤销历史，避免出现无法撤销的提示 |
| v5.5 | 2026-10-19 | 速记改存应用私有目录；5 个备份；速读可导入旧文件；保存导出到 Documents |
| v5.3 | 2026-10-19 | 修复速记文件缺失时速读/小部件 ENOENT；增加路径兼容与友好提示 |
| v5.2 | 2026-10-19 | 标题菜单下移与正文顶部对齐；语言项统一为 English / 中文；修复语言切换 |
| v5.1 | 2026-10-19 | 标题菜单去掉右侧单选圆点，三项改为纯文字 |
| v5.0 | 2026-10-19 | 界面默认英文；点击标题打开语言/彩蛋菜单；桌面名称固定 ScanPaste |
| v4.4 | 2026-10-19 | 新增复制按钮；压缩底部操作栏；新增英文界面（短标签） |
| v4.3 | 2026-05-29 | 速记覆盖提示：文件已存在时弹窗确认，覆盖前备份为 quicknote-1~5.txt |
| v4.2 | 2026-05-29 | 彩蛋游戏配置化：🐰🦊文字从 game.txt 动态读取，可自定义角色 |
| v4.1 | 2026-05-29 | 彩蛋游戏：标题点击进入 Rabbit Jump + best.txt 持久化 |
| v4.0 | 2026-05-29 | UI 重做：NestedScrollView + 5 按钮底部栏 + 版本号 |
| v3.2 | 2026-05-29 | 全选/复制/剪切/粘贴、EditorActionListener 移除 |
| v3.1 | 2026-05-29 | 小部件滚动、圆角、透明优化 |
| v3.0 | 2026-05-29 | IME insets 键盘自适应、撤销重做收敛 |
| v2.1–v2.6 | 2026-05-29 | 保存功能、速记、速读、桌面小部件 |
| v2.0 | 2026-05-29 | MD3 主题 + 蓝色 Toolbar + 图标按钮 |
| v1.2 | 2026-05-29 | 新增：相册扫码入口、清空按钮 |
| v1.1 | 2026-05-29 | 修复：撤销重做逻辑、键盘自适应 |
| v1.0 | 2026-05-29 | MVP：白底 EditText + 三个基础按钮 + 扫码追加 |

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin |
| Architecture | MVVM (single Activity + ViewModel + LiveData) |
| UI | Material Design 3, MaterialToolbar, ViewBinding |
| Scanning | ZXing Embedded 4.3.0 |
| Storage | Storage Access Framework + MediaStore (Android 10+) |
| Widget | AppWidget + RemoteViewsService |
| Easter egg | WebView + HTML5 Canvas + JavaScript |
| Tests | JUnit4 + Truth |

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md)
for the workflow.

Two rules matter more than style: keep the app offline, and do not add the
`INTERNET` permission or any network dependency.

## License

ScanPaste is released under the MIT License. See [LICENSE](LICENSE) for the full
text.

Copyright (c) 2026 iFeel.
