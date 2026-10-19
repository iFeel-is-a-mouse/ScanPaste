package com.scanpaste.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.scanpaste.app.databinding.ActivityMainBinding
import com.scanpaste.app.scanner.QrCodeScanner
import com.scanpaste.app.viewmodel.EditorViewModel
import com.scanpaste.app.widget.QuickNoteWidgetProvider
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: EditorViewModel
    private lateinit var qrCodeScanner: QrCodeScanner

    private var lastAppendTime = 0L
    private var lastUndoTime = 0L
    private var lastRedoTime = 0L
    private var saveFileName = ""
    private var saveExtension = "txt"

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner() else onCameraPermissionDenied()
        }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = qrCodeScanner.parseResult(result.resultCode, result.data)
            if (text != null) { viewModel.appendText(text); moveCursorToEnd() }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { decodeQrFromUri(it) }
        }

    private val saveFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) result.data?.data?.let { writeContentToUri(it) }
        }

    override fun attachBaseContext(newBase: Context) {
        val locale = Locale(LanguagePrefs.get(newBase))
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        applyOverrideConfiguration(config)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        saveFileName = getString(R.string.save_default_name)
        applyWindowInsets()
        viewModel = ViewModelProvider(this)[EditorViewModel::class.java]
        qrCodeScanner = QrCodeScanner()
        setupObservers()
        setupClickListeners()
        setupTextWatcher()
        // Only handle the launch intent on first creation: recreate() (language switch)
        // must not reload the quick note over unsaved edits.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(QuickNoteWidgetProvider.EXTRA_LOAD_QUICKNOTE, false)) {
            loadQuickNote()
        }
    }

    private fun setupObservers() {
        viewModel.text.observe(this) { t ->
            if (binding.etEditor.text?.toString() != t) binding.etEditor.setText(t)
        }
        viewModel.canUndo.observe(this) { binding.btnUndo.isEnabled = it }
        viewModel.canRedo.observe(this) { binding.btnRedo.isEnabled = it }
        viewModel.undoFailed.observe(this) { failed ->
            if (failed) {
                Toast.makeText(this, R.string.toast_undo_changed, Toast.LENGTH_SHORT).show()
                viewModel.onUndoFailedHandled()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnUndo.setOnClickListener {
            if (debounce(lastUndoTime)) { lastUndoTime = now(); viewModel.undo(); moveCursorToEnd() }
        }
        binding.btnRedo.setOnClickListener {
            if (debounce(lastRedoTime)) { lastRedoTime = now(); viewModel.redo(); moveCursorToEnd() }
        }
        binding.btnAppend.setOnClickListener {
            if (debounce(lastAppendTime)) { lastAppendTime = now(); checkAndRequestCameraPermission() }
        }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.btnClear.setOnClickListener { viewModel.clearAll() }
        binding.btnSave.setOnClickListener { showSaveDialog() }
        binding.btnQuicknote.setOnClickListener { saveQuickNote() }
        binding.btnReadnote.setOnClickListener { loadQuickNote() }
        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ScanPaste", viewModel.text.value ?: "")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
        }
        // Title hotspot: language switch + Easter egg entry
        binding.tvTitle.setOnClickListener { showTitleMenu() }
    }

    private fun showTitleMenu() {
        val items = listOf(
            getString(R.string.menu_language_en),
            getString(R.string.menu_language_zh),
            getString(R.string.menu_easter_egg)
        )
        val popup = ListPopupWindow(this).apply {
            anchorView = binding.tvTitle
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, items))
            isModal = true
            width = resources.getDimensionPixelSize(R.dimen.menu_width)
            verticalOffset = titleMenuVerticalOffset()
        }
        popup.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> switchLanguage(LanguagePrefs.LANG_EN)
                1 -> switchLanguage(LanguagePrefs.LANG_ZH)
                2 -> startActivity(Intent(this, RabbitJumpActivity::class.java))
            }
            popup.dismiss()
        }
        popup.show()
    }

    /**
     * Vertical distance from the title's bottom edge to the note area's top edge.
     *
     * ListPopupWindow anchors its top edge to the anchor's bottom and shifts it
     * down by verticalOffset, so this value makes the menu top line up with the
     * note area. Clamped at 0: should the note area ever start above the title
     * bottom, the menu stays just below the title instead of covering the toolbar.
     */
    private fun titleMenuVerticalOffset(): Int {
        val titleLocation = IntArray(2)
        val editorLocation = IntArray(2)
        binding.tvTitle.getLocationOnScreen(titleLocation)
        binding.scrollEditor.getLocationOnScreen(editorLocation)
        val titleBottom = titleLocation[1] + binding.tvTitle.height
        return (editorLocation[1] - titleBottom).coerceAtLeast(0)
    }

    /**
     * Persists the language and rebuilds the UI.
     *
     * There is deliberately no "already selected" early return: the stored
     * preference can already match while the visible resources still use the
     * old locale, and in that case the user still expects a rebuild.
     */
    private fun switchLanguage(lang: String) {
        LanguagePrefs.set(this, lang)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        } else {
            recreate()
        }
    }

    private fun setupTextWatcher() {
        binding.etEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val current = viewModel.text.value ?: ""
                if (text != current) viewModel.updateTextManually(text)
            }
        })
    }

    private fun decodeQrFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = qrCodeScanner.decodeQrFromImage(stream)
                if (text != null) { viewModel.appendText(text); moveCursorToEnd() }
                else Toast.makeText(this, R.string.toast_qr_not_found, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_qr_decode_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> launchScanner()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ->
                Snackbar.make(binding.root, R.string.permission_camera_snackbar, Snackbar.LENGTH_LONG)
                    .setAction(R.string.dialog_authorize) { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }.show()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun onCameraPermissionDenied() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Snackbar.make(binding.root, R.string.permission_camera_snackbar, Snackbar.LENGTH_LONG)
                .setAction(R.string.dialog_authorize) { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }.show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_camera_title).setMessage(R.string.permission_camera_message)
                .setPositiveButton(R.string.dialog_go_to_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                }
                .setNegativeButton(R.string.dialog_cancel, null).show()
        }
    }

    private fun launchScanner() { scanLauncher.launch(qrCodeScanner.createScanIntent(this)) }

    private fun saveQuickNote() {
        val text = viewModel.text.value ?: ""
        try {
            val existingUri = findQuickNoteUri()
            if (existingUri != null) {
                showOverwriteDialog(text, existingUri)
            } else {
                doSaveQuickNote(text)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOverwriteDialog(text: String, existingUri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.overwrite_title)
            .setMessage(R.string.overwrite_message)
            .setPositiveButton(R.string.btn_overwrite) { _, _ ->
                backupAndSaveQuickNote(text, existingUri)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun backupAndSaveQuickNote(text: String, existingUri: Uri) {
        try {
            // Read old content, then delete old file before any MediaStore writes
            val oldContent = contentResolver.openInputStream(existingUri)?.use {
                String(it.readBytes(), Charsets.UTF_8)
            } ?: ""
            contentResolver.delete(existingUri, null, null)

            // Backup old content to bak slot
            if (oldContent.isNotEmpty()) {
                saveToBackupSlot(oldContent)
            }

            // Create fresh quicknote.txt with new content
            val newUri = createQuickNoteUri()
            if (newUri != null) {
                contentResolver.openOutputStream(newUri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            Toast.makeText(this, R.string.toast_quicknote_saved, Toast.LENGTH_SHORT).show()
            QuickNoteWidgetProvider.sendUpdateBroadcast(this)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save content into quicknote-1~5.txt.
     * Fill first empty slot; if all full, overwrite the oldest one.
     */
    private fun saveToBackupSlot(content: String) {
        for (n in 1..5) {
            val fileName = "quicknote-$n.txt"
            if (findBakUri(fileName) == null) {
                writeBakFile(fileName, content)
                return
            }
        }
        // All full: find and overwrite the oldest
        var oldestN = 1
        var oldestTime = Long.MAX_VALUE
        for (n in 1..5) {
            val uri = findBakUri("quicknote-$n.txt") ?: continue
            val modTime = getFileModifiedTime(uri)
            if (modTime < oldestTime) {
                oldestTime = modTime
                oldestN = n
            }
        }
        deleteBakIfExists("quicknote-$oldestN.txt")
        writeBakFile("quicknote-$oldestN.txt", content)
    }

    private fun getFileModifiedTime(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun doSaveQuickNote(text: String) {
        val uri = findOrCreateQuickNoteUri() ?: return
        contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        Toast.makeText(this, R.string.toast_quicknote_saved, Toast.LENGTH_SHORT).show()
        QuickNoteWidgetProvider.sendUpdateBroadcast(this)
    }

    /**
     * MediaStore may report RELATIVE_PATH with or without a trailing slash depending
     * on the Android version and how the file was created, so try both forms.
     */
    private fun findDocumentUri(displayName: String): Uri? {
        val paths = arrayOf(
            "${Environment.DIRECTORY_DOCUMENTS}/scanpaste/",
            "${Environment.DIRECTORY_DOCUMENTS}/scanpaste"
        )
        val collection = MediaStore.Files.getContentUri("external")
        val sel = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        for (path in paths) {
            contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), sel, arrayOf(path, displayName), null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        return collection.buildUpon().appendPath(c.getLong(0).toString()).build()
                    }
                }
        }
        return null
    }

    private fun findQuickNoteUri(): Uri? {
        findDocumentUri("quicknote.txt")?.let { return it }
        // Fallback: some providers normalize RELATIVE_PATH differently, so match by
        // display name within the scanpaste folder using a prefix match.
        val collection = MediaStore.Files.getContentUri("external")
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val a = arrayOf("quicknote.txt", "${Environment.DIRECTORY_DOCUMENTS}/scanpaste%")
        contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), sel, a, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    return collection.buildUpon().appendPath(c.getLong(0).toString()).build()
                }
            }
        return null
    }

    private fun findBakUri(fileName: String): Uri? = findDocumentUri(fileName)

    private fun deleteBakIfExists(fileName: String) {
        findBakUri(fileName)?.let { contentResolver.delete(it, null, null) }
    }

    private fun writeBakFile(fileName: String, content: String) {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/scanpaste")
        }
        contentResolver.insert(MediaStore.Files.getContentUri("external"), v)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun loadQuickNote() {
        val uri = try {
            findQuickNoteUri()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_quicknote_read_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (uri == null) {
            Toast.makeText(this, R.string.toast_quicknote_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val stream = contentResolver.openInputStream(uri)
            if (stream == null) {
                Toast.makeText(this, R.string.toast_quicknote_read_failed, Toast.LENGTH_SHORT).show()
                return
            }
            val text = stream.use { String(it.readBytes(), Charsets.UTF_8) }
            viewModel.updateTextManually(text)
            moveCursorToEnd()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_quicknote_read_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createQuickNoteUri(): Uri? {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "quicknote.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/scanpaste")
        }
        return contentResolver.insert(MediaStore.Files.getContentUri("external"), v)
    }

    private fun findOrCreateQuickNoteUri(): Uri? {
        findQuickNoteUri()?.let { return it }
        return createQuickNoteUri()
    }

    private fun showSaveDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_save, null)
        val etF = view.findViewById<EditText>(R.id.et_filename); etF.setText(saveFileName)
        val etE = view.findViewById<EditText>(R.id.et_extension); etE.setText(saveExtension)
        AlertDialog.Builder(this).setTitle(R.string.save_dialog_title).setView(view)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                saveFileName = etF.text.toString().ifBlank { getString(R.string.save_default_name) }
                saveExtension = etE.text.toString().ifBlank { "txt" }
                launchSaveFile()
            }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun launchSaveFile() {
        val mime = when (saveExtension.lowercase()) { "md","markdown"->"text/markdown"; "csv"->"text/csv"; "json"->"application/json"; "xml"->"text/xml"; "html","htm"->"text/html"; else->"text/plain" }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, "$saveFileName.$saveExtension")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"))
        }
        saveFileLauncher.launch(intent)
    }

    private fun writeContentToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write((viewModel.text.value ?: "").toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show() }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            binding.statusBarSpacer.layoutParams.height = bars.top; binding.statusBarSpacer.requestLayout()
            view.updatePadding(bottom = bars.bottom); insets
        }
    }

    private fun moveCursorToEnd() { binding.etEditor.post { binding.etEditor.setSelection(binding.etEditor.text?.length ?: 0) } }
    private fun debounce(last: Long) = now() - last >= DEBOUNCE_MS
    private fun now() = System.currentTimeMillis()

    companion object {
        private const val DEBOUNCE_MS = 500L
    }
}
