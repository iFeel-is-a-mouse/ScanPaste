# Android 文本文件保存最佳实践 — SAF + MediaStore

> 来源: 搜索结果 + Android 官方文档
> ⚠️ 辩证批判地使用

## 推荐方案: ACTION_CREATE_DOCUMENT (SAF)

**优点**: 无需存储权限(Android 10+), 用户自选位置, 初始目录指向 Documents

```kotlin
// 1. 注册 launcher
private val saveFileLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri -> writeContent(uri, text) }
    }
}

// 2. 启动保存 (指定扩展名和MIME)
fun saveFile(fileName: String, extension: String) {
    val mimeType = when (extension.lowercase()) {
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "html" -> "text/html"
        "log" -> "text/plain"
        else -> "text/plain"
    }
    saveFileLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, "$fileName.$extension")
        // 初始目录 = Documents (API 26+)
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Documents"
            ))
    })
}

// 3. 写入内容
private fun writeContent(uri: Uri, content: String) {
    contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
}
```

## 批判要点
- `EXTRA_INITIAL_URI` 仅 API 26+, 低版本需降级处理
- SAF 不适用于需要自动保存(无需用户交互)的场景 → 用 MediaStore
- `primary:Documents` 硬编码了内部存储, 外置SD卡需不同 authority
