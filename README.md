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
- **撤销/重做**：追加和清空操作可回退，最多 100 步。
- **保存与速记**：可另存为 txt、md、csv、json 等文件；速记写入 `Documents/scanpaste/quicknote.txt`。
- **桌面小部件**：展示速记内容，可缩放、滚动，点击打开应用。
- **纯离线**：ZXing 在设备本地解码，应用没有 INTERNET 权限。
- **系统要求**：Android 7.0（API 24）及以上。

## Features

- **Scan and append** — scan with the camera or pick an image from the gallery.
  The decoded text is appended to the end of the note.
- **Editing** — select all, copy, cut, and paste. Fixed 18sp text, scrollable for
  long notes.
- **Undo and redo** — append and clear actions can be undone, up to 100 steps.
- **Copy** — copy the whole note to the clipboard in one tap.
- **Save as file** — choose a filename and extension (txt, md, csv, json, and so
  on). Files go to Documents by default.
- **Quick note and quick read** — save to `Documents/scanpaste/quicknote.txt` or
  load it back with one tap. If the file exists, the app asks before overwriting
  and keeps five backups (`quicknote-1.txt` … `quicknote-5.txt`).
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
4. Tap **保存 (Save)** to write a file, or **速记 (Quick note)** to update
   `Documents/scanpaste/quicknote.txt`.
5. Long-press the home screen and add the ScanPaste widget to see the quick note
   without opening the app.

## Files and data

The app keeps its files in `Documents/scanpaste/`:

| File | Purpose | Format |
|------|---------|--------|
| `quicknote.txt` | Quick note content | Plain text |
| `quicknote-1.txt` … `quicknote-5.txt` | Backups made before a quick note overwrite (empty slots first, then the oldest) | Plain text |
| `best.txt` | Rabbit Jump high score | `level,date time` |
| `game.txt` | Rabbit Jump characters and caption | 3 lines: player emoji, obstacle emoji, caption |

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
- Notes are saved only where you put them: usually `Documents/scanpaste/` or a
  location you choose. Unsaved text stays in memory. Android backup is disabled
  (`allowBackup="false"`).
- Uninstalling the app does not delete files you saved in Documents.

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
