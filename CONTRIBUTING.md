# Contributing to ScanPaste

Thanks for taking the time to help. Bug reports, fixes, translations, and
focused improvements are all welcome.

## Report a bug

Open an issue at
<https://github.com/iFeel-is-a-mouse/scanpaste/issues> and include:

- app version (shown in the app) and Android version;
- device model, if the problem is device-specific;
- steps to reproduce, expected result, and actual result;
- a screenshot or screen recording when it helps;
- relevant logcat output, with personal data removed.

Search existing issues first. Please do not paste notes or QR contents that
contain private information.

## Suggest a feature

Open an issue and describe the problem you want to solve, not only the feature
you have in mind. Small, offline-friendly additions fit this project best.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Build and test locally:

   ```bash
   ./gradlew test assembleDebug
   ```

3. Keep the change focused. Separate unrelated refactors from fixes.
4. Describe what changed and why. Mention the device and Android version you
   tested on.
5. For UI changes, include before/after screenshots.

## Hard constraints

These are part of the app's design, not preferences:

- **No network.** Do not add the `INTERNET` permission, network libraries, or
  code that sends data off the device.
- **No tracking.** No analytics, ads, crash reporting SDKs, or accounts.
- **Offline decoding.** QR decoding stays on-device.
- **Android 7.0+.** Keep `minSdk` 24 working.
- **No generated files in commits.** Do not commit `local.properties`,
  keystores, APKs, or build output.

## Code style

- Kotlin, 4-space indentation, following the existing MVVM structure.
- Prefer the existing AndroidX and Material components over new dependencies.
- Keep user-facing strings in `app/src/main/res/values/strings.xml`.
- Add or update unit tests when changing editor, undo/redo, or file logic.

## Commit messages

Use a short imperative summary, then explain the reason in the body if needed:

```
Add copy button to the editor

The note could only be copied through text selection, which is awkward
for long notes.
```

## License

By contributing, you agree that your contributions are licensed under the MIT
License. See [LICENSE](LICENSE).

---

## 中文说明

欢迎提交问题和改进。提交前请先搜索已有 issue；反馈问题时附上应用版本、Android
版本、复现步骤和必要截图，并移除笔记或二维码中的隐私内容。

提交代码前请运行 `./gradlew test assembleDebug`。请保持改动聚焦，并遵守两条硬性
约束：**不添加网络权限或联网依赖**，**不加入统计、广告或追踪 SDK**。贡献的代码
按 MIT 授权。
