# 扫码笔记 — 二维码扫码追加工具 v4.3

> 扫一扫，文字自动追加到编辑区。支持撤销/重做/保存/速记/桌面小部件。内含 🐰 彩蛋游戏。

## 功能

- **扫码追加** — 点击"追加"拍照扫码，🖼️图标从相册选图；二维码内容自动追加到文末
- **编辑区** — 支持全选/复制/剪切/粘贴，18sp固定字号，长文本可滚动
- **撤销/重做** — 追加/清空操作可撤销和重做，最多100次历史记录
- **保存文件** — 自定义文件名+扩展名(txt/md/csv/json等)，默认Documents目录
- **速记** — 一键保存到 `Documents/scanpaste/quicknote.txt`，文件已存在时弹窗确认覆盖，覆盖前自动备份为 quicknote-1~5.txt
- **速读** — 一键从 `Documents/scanpaste/quicknote.txt` 加载到编辑区
- **桌面小部件** — 可缩放+滚动+圆角+10%透明，展示quicknote内容，点击打开文件
- **纯离线** — ZXing扫码引擎，无网络权限
- **🐰 彩蛋游戏** — 点击标题进入 Rabbit Jump 小游戏

## Documents/scanpaste/ 文件说明

应用会在 `Documents/scanpaste/` 下维护以下文件，可自行查看和编辑：

| 文件 | 用途 | 格式 |
|------|------|------|
| `quicknote.txt` | 速记内容（速记保存/速读加载） | 纯文本 |
| `quicknote-1.txt` ~ `quicknote-5.txt` | 🆕 速记覆盖前的备份（空位优先，满了覆盖最旧） | 纯文本 |
| `best.txt` | 彩蛋游戏最高纪录 | `关卡数,日期时间` |
| `game.txt` | 🆕 彩蛋游戏角色配置 | 3行：🐰 / 🦊 / 展示文字 |

### game.txt 定制

游戏中的兔子、狐狸和底部文字全部从 `game.txt` 读取。修改任意一行，重启游戏即可看到变化：

```
🐰          ← 第1行：玩家角色（兔子 emoji）
🦊          ← 第2行：障碍物（狐狸 emoji）
The cute rabbit jumps over the lazy fox  ← 第3行：底部展示文字
```

首次启动游戏时自动创建。删除文件后重启游戏会重新生成默认值。

示例定制：
```
🐱
🐶
The quick brown cat jumps over the lazy dog
```

## 技术栈

| 类别 | 选择 |
|------|------|
| 语言 | Kotlin |
| 架构 | MVVM (单Activity + ViewModel + LiveData) |
| UI | Material Design 3 + MaterialToolbar + ViewBinding |
| 扫码 | ZXing Embedded 4.3.0 |
| 存储 | SAF + MediaStore (Android 10+) |
| 小部件 | AppWidget + RemoteViewsService (ListView) |
| 彩蛋游戏 | WebView + HTML5 Canvas + JS |
| 测试 | JUnit4 + Truth |

## 构建

### 环境要求

- JDK 17+（实测 JDK 26 可编译）
- Android SDK 35（platform + build-tools）
- Gradle 8.14（项目自带 gradlew，无需手动安装）

### 1. 安装 Android SDK

```bash
# 下载 cmdline-tools
curl -L -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"

# 解压到目标目录
unzip -qo /tmp/cmdline-tools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

# 设置环境变量
export ANDROID_HOME=~/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# 安装必需组件
yes | sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"
```

### 2. 配置 local.properties

项目根目录的 `local.properties` 指向你的 Android SDK：

```properties
sdk.dir=/Users/你的用户名/android-sdk
```

### 3. 构建 APK

```bash
cd scanpaste
./gradlew assembleDebug    # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease  # Release APK → app/build/outputs/apk/release/（需签名）
```

> **macOS 注意**：JDK 26 下 sdkmanager 会有 JNA 警告，不影响使用。Linux/Windows 同理，只需替换 cmdline-tools 的下载链接。

## 演进历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-05-29 | MVP：白底EditText + 三个基础按钮 + 扫码追加 |
| v1.1 | 2026-05-29 | 修复：撤销重做逻辑、键盘自适应 |
| v1.2 | 2026-05-29 | 新增：相册扫码入口、清空按钮 |
| v2.0 | 2026-05-29 | MD3主题 + 蓝色Toolbar + 图标按钮 |
| v2.1–v2.6 | 2026-05-29 | 保存功能、速记、速读、桌面小部件 |
| v3.0 | 2026-05-29 | IME insets键盘自适应、撤销重做收敛 |
| v3.1 | 2026-05-29 | 小部件滚动、圆角、透明优化 |
| v3.2 | 2026-05-29 | 全选/复制/剪切/粘贴、EditorActionListener移除 |
| v4.0 | 2026-05-29 | UI重做：NestedScrollView + 5按钮底部栏 + 版本号 |
| v4.1 | 2026-05-29 | 彩蛋游戏：标题点击进入Rabbit Jump + best.txt持久化 |
| v4.3 | 2026-05-29 | 速记覆盖提示：文件已存在时弹窗确认，覆盖前备份为 quicknote-1~5.txt |
| v4.2 | 2026-05-29 | 彩蛋游戏配置化：🐰🦊文字从 game.txt 动态读取，可自定义角色 |

## 彩蛋触发方式

点击 Toolbar 的「扫码笔记」标题即可进入 Rabbit Jump 小游戏。

游戏规则：兔兔从左侧出发向右移动，狐狸站在第15格。在兔兔到达第11-14格时点击跳跃，精准跳过狐狸即通关。通关后速度递增。撞到狐狸需等3秒重来。
