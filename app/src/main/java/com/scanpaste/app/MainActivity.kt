package com.scanpaste.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
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
import java.io.ByteArrayOutputStream
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

    /** Result of a bounded background read. `text == null && !tooLarge` means failure. */
    private class ImportReadResult(val text: String?, val tooLarge: Boolean)

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

    private val importNoteLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(this, R.string.toast_quicknote_pick_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                importQuickNote(uri)
            }
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
        // Restore the private quick note as the baseline document (no history).
        // Only runs when the file exists, so first launch stays empty and never
        // opens a file picker.
        if (savedInstanceState == null && QuickNoteStorage.mainFile(this).exists()) {
            loadQuickNote(recordHistory = false)
        }
        // Only handle the launch intent on first creation: recreate() (language switch)
        // must not reload the quick note over unsaved edits.
        if (savedInstanceState == null) handleIntent(intent, recordHistory = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, recordHistory = true)
    }

    private fun handleIntent(intent: Intent, recordHistory: Boolean) {
        if (intent.getBooleanExtra(QuickNoteWidgetProvider.EXTRA_LOAD_QUICKNOTE, false) &&
            QuickNoteStorage.mainFile(this).exists()
        ) {
            loadQuickNote(recordHistory)
        }
    }

    private fun setupObservers() {
        viewModel.text.observe(this) { t ->
            if (binding.etEditor.text?.toString() != t) binding.etEditor.setText(t)
        }
        viewModel.canUndo.observe(this) { binding.btnUndo.isEnabled = it }
        viewModel.canRedo.observe(this) { binding.btnRedo.isEnabled = it }
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

    /**
     * Decodes a QR code from a picked gallery image.
     *
     * Opening the content stream and decoding (including the median-filter
     * retry) touch disk and run for hundreds of milliseconds, so both happen on
     * a background thread. Only the UI update returns to the main thread.
     */
    private fun decodeQrFromUri(uri: Uri) {
        Thread {
            var text: String? = null
            var failed = false
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    failed = true
                } else {
                    stream.use { text = qrCodeScanner.decodeQrFromImage(it) }
                }
            } catch (e: Exception) {
                failed = true
            }

            val decoded = text
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (decoded != null) {
                    viewModel.appendText(decoded)
                    moveCursorToEnd()
                } else {
                    val message = if (failed) R.string.toast_qr_decode_failed else R.string.toast_qr_not_found
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
            if (QuickNoteStorage.mainFile(this).exists()) {
                showOverwriteDialog(text)
            } else {
                doSaveQuickNote(text)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOverwriteDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.overwrite_title)
            .setMessage(R.string.overwrite_message)
            .setPositiveButton(R.string.btn_overwrite) { _, _ ->
                backupAndSaveQuickNote(text)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun backupAndSaveQuickNote(text: String) {
        // Read before writing: the atomic write keeps the old note intact, but
        // the backup has to capture the content that is about to be replaced.
        val oldContent = QuickNoteStorage.read(this)
        if (oldContent == null) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // An empty note has nothing worth preserving, so it does not consume a slot.
        if (oldContent.isNotEmpty() && !QuickNoteStorage.backup(this, oldContent)) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        if (!QuickNoteStorage.write(this, text)) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.toast_quicknote_saved, Toast.LENGTH_SHORT).show()
        QuickNoteWidgetProvider.sendUpdateBroadcast(this)
    }

    private fun doSaveQuickNote(text: String) {
        if (!QuickNoteStorage.write(this, text)) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.toast_quicknote_saved, Toast.LENGTH_SHORT).show()
        QuickNoteWidgetProvider.sendUpdateBroadcast(this)
    }

    /**
     * Loads the private quick note into the editor.
     *
     * @param recordHistory true for an explicit user action (Read/import), so
     *   the previous content stays reachable via undo; false for launch/widget
     *   loads, which establish the baseline document.
     */
    private fun loadQuickNote(recordHistory: Boolean = true) {
        val text = QuickNoteStorage.read(this)
        if (text != null) {
            if (recordHistory) viewModel.replaceText(text) else viewModel.setInitialText(text)
            moveCursorToEnd()
            return
        }

        // Distinguish "file is absent" from "file exists but could not be read".
        // A read failure must not silently send the user into the import flow.
        if (QuickNoteStorage.mainFile(this).exists()) {
            Toast.makeText(this, R.string.toast_quicknote_read_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // Automatic loads must never fall through to the import flow.
        if (!recordHistory) return

        // No note yet: let the user pick a file through the system picker.
        launchQuickNoteImport()
    }

    /**
     * Opens the system file picker and imports the chosen text file into the
     * editor. The URI is deliberately not persisted: this is a one-shot read,
     * and saving targets the app-private quick note.
     */
    private fun launchQuickNoteImport() {
        try {
            importNoteLauncher.launch(arrayOf("text/plain"))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_quicknote_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importQuickNote(uri: Uri) {
        if ((viewModel.text.value ?: "").isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_replace_title)
                .setMessage(R.string.import_replace_message)
                .setPositiveButton(R.string.import_replace_confirm) { _, _ ->
                    readAndApplyImport(uri)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        } else {
            readAndApplyImport(uri)
        }
    }

    /** Reads [uri] on a worker thread and applies the result on the UI thread. */
    private fun readAndApplyImport(uri: Uri) {
        Thread {
            val result = readTextWithLimit(uri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    result.tooLarge ->
                        Toast.makeText(this, R.string.import_too_large, Toast.LENGTH_SHORT).show()
                    result.text == null ->
                        Toast.makeText(this, R.string.toast_quicknote_read_failed, Toast.LENGTH_SHORT).show()
                    else -> {
                        viewModel.replaceText(result.text)
                        moveCursorToEnd()
                        Toast.makeText(this, R.string.toast_quicknote_imported, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    /**
     * Reads at most [MAX_IMPORT_BYTES]. Anything larger is rejected before it
     * can be materialized in memory.
     */
    private fun readTextWithLimit(uri: Uri): ImportReadResult {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) return ImportReadResult(null, true)
                    output.write(buffer, 0, read)
                }
                ImportReadResult(String(output.toByteArray(), Charsets.UTF_8), false)
            } ?: ImportReadResult(null, false)
        } catch (e: Exception) {
            ImportReadResult(null, false)
        }
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
        private const val MAX_IMPORT_BYTES = 1024 * 1024
    }
}
