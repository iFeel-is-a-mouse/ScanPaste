# Submitting ScanPaste to F-Droid

## 1. Create a GitLab.com account

1. Open https://gitlab.com/users/sign_up
2. Register with an email address, username, and password.
3. Verify the email address.
4. A free account is enough. 2FA is optional but recommended.

F-Droid uses GitLab.com for its `fdroiddata` repository, so a GitLab.com account is required.

## 2. Fork `fdroiddata`

1. Open https://gitlab.com/fdroid/fdroiddata
2. Click **Fork** and create the fork under your account.
3. The repository is large; the fork is created server-side.

## 3. Add the recipe

In your fork, add this file:

```text
metadata/com.scanpaste.app.yml
```

Copy the contents of `fdroid/com.scanpaste.app.yml` from the ScanPaste repository.

The recipe uses:

- `Repo`: https://github.com/iFeel-is-a-mouse/ScanPaste.git
- `commit`: `v5.8`
- `subdir`: `app`
- `gradle`: `yes`
- `License`: MIT
- `Categories`: Text Editors

The upstream repository already contains Fastlane metadata in
`fastlane/metadata/android/{en-US,zh-CN}` (title, descriptions, changelogs,
icon, screenshots), so the recipe does not need a long description.

## 4. Open a merge request

1. Commit the new file on a branch, e.g. `scanpaste-5.8`.
2. Open a merge request from your fork to `fdroid/fdroiddata` (`master`).
3. Suggested title:

```text
New app: ScanPaste (com.scanpaste.app)
```

4. Suggested description:

```text
New app: ScanPaste / 扫码笔记

- Package: com.scanpaste.app
- Source: https://github.com/iFeel-is-a-mouse/ScanPaste
- License: MIT
- Version: 5.8 (versionCode 58), tag v5.8
- Build: Gradle, subdir app, gradle: yes
- Fastlane metadata is in the upstream repository (en-US, zh-CN), including icon and screenshots.
- Offline QR scanner and note editor: no INTERNET permission, no ads, no analytics, no accounts.
```

## 5. Review and build

- F-Droid maintainers review the recipe and the app's license/privacy.
- Their build server builds from tag `v5.8` and signs the APK with the F-Droid key.
- They may ask for changes; answer on the merge request.
- After merge, the app appears in the next F-Droid index update.

F-Droid-signed builds cannot be installed over the GitHub APK (different signing
keys); users switching channels must uninstall first.

## Alternative: request for packaging (RFP)

If you do not want to maintain the recipe yourself, open an issue in
`fdroiddata` using the **Request for Packaging** template and include the app
name, package, source URL, license, and version. An MR is usually faster.

## References

- F-Droid inclusion policy: https://f-droid.org/docs/Inclusion_Policy/
- Submitting to F-Droid: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- fdroiddata contributing guide: https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md
