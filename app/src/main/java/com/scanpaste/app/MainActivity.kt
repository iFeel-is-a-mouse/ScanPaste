package com.scanpaste.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
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
        super.attachBaseContext(LanguagePrefs.wrap(newBase))
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
        val popup = PopupMenu(this, binding.tvTitle)
        val menu = popup.menu
        menu.add(MENU_GROUP, MENU_LANGUAGE_EN, 0, R.string.menu_language_en)
        menu.add(MENU_GROUP, MENU_LANGUAGE_ZH, 1, R.string.menu_language_zh)
        menu.add(MENU_GROUP, MENU_EASTER_EGG, 2, R.string.menu_easter_egg)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_LANGUAGE_EN -> switchLanguage(LanguagePrefs.LANG_EN)
                MENU_LANGUAGE_ZH -> switchLanguage(LanguagePrefs.LANG_ZH)
                MENU_EASTER_EGG ->
                    startActivity(Intent(this, RabbitJumpActivity::class.java))
            }
            true
        }
        popup.show()
    }

    /** Recreates the activity so the new locale applies to the inflated layout. */
    private fun switchLanguage(lang: String) {
        if (LanguagePrefs.get(this) == lang) return
        LanguagePrefs.set(this, lang)
        recreate()
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

    private fun findQuickNoteUri(): Uri? {
        val sel = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val a = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/scanpaste/", "quicknote.txt")
        contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID), sel, a, null)
            ?.use { c -> if (c.moveToFirst()) return MediaStore.Files.getContentUri("external").buildUpon().appendPath(c.getLong(0).toString()).build() }
        return null
    }

    private fun findBakUri(fileName: String): Uri? {
        val sel = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val a = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/scanpaste/", fileName)
        contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID), sel, a, null)
            ?.use { c -> if (c.moveToFirst()) return MediaStore.Files.getContentUri("external").buildUpon().appendPath(c.getLong(0).toString()).build() }
        return null
    }

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
        try {
            val uri = findOrCreateQuickNoteUri() ?: return
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = String(stream.readBytes(), Charsets.UTF_8)
                viewModel.updateTextManually(text)
                moveCursorToEnd()
            }
        } catch (_: Exception) {}
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
        private const val MENU_GROUP = 0
        private const val MENU_LANGUAGE_EN = 1
        private const val MENU_LANGUAGE_ZH = 2
        private const val MENU_EASTER_EGG = 3
    }
}
