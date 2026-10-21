package com.scanpaste.app

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * App-private storage for the quick note.
 *
 * The note lives in the app's external files directory
 * (`Android/data/<package>/files/Documents/scanpaste/`), falling back to the
 * internal files directory when external storage is unavailable. Files here
 * need no storage permission and are removed when the app is uninstalled.
 * On Android 11+ other apps cannot read this directory; on Android 10 and
 * below, apps holding storage permission may still be able to read it.
 *
 * MediaStore is deliberately not used: the quick note is private state, not a
 * document the user manages through the system file manager.
 */
object QuickNoteStorage {

    private const val DIR_NAME = "scanpaste"
    private const val MAIN_NAME = "quicknote.txt"
    private const val MAIN_TMP_NAME = "quicknote.tmp"
    private const val BACKUP_COUNT = 5

    /** Plain file names only: no separators, no traversal. */
    private val SAFE_FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    /**
     * Result of reading a file.
     *
     * "The file is not there" and "the file could not be read" are different
     * facts: only [Missing] may be treated as "no data yet". A [Failed] read
     * must never cause an existing file to be overwritten.
     */
    sealed class FileRead {
        data class Ok(val text: String) : FileRead()
        data object Missing : FileRead()
        data class Failed(val reason: String, val cause: Throwable? = null) : FileRead()
    }

    /** Base directory for all quick-note files. It may not exist yet. */
    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(base, DIR_NAME)
    }

    fun mainFile(context: Context): File = File(dir(context), MAIN_NAME)

    /**
     * Resolves a sibling file in the same directory as the quick note.
     *
     * Shared so other features (Rabbit Jump) do not duplicate the directory
     * layout, which would drift from the quick note over time.
     *
     * @throws IllegalArgumentException when [fileName] is not a plain file name.
     */
    fun file(context: Context, fileName: String): File {
        require(SAFE_FILE_NAME.matches(fileName)) { "Unsafe file name: $fileName" }
        return File(dir(context), fileName)
    }

    /**
     * Reads a sibling file as UTF-8, capped at [maxBytes].
     *
     * The cap protects the caller from unbounded reads (a save file is a few
     * lines). An oversized file is reported as [FileRead.Failed] instead of
     * being silently truncated, so callers never persist a partial value.
     */
    fun readFile(context: Context, fileName: String, maxBytes: Int): FileRead =
        readLimited(file(context, fileName), maxBytes)

    /** Reads [file] as UTF-8 with the same size cap semantics as [readFile]. */
    internal fun readLimited(file: File, maxBytes: Int): FileRead {
        if (!file.exists()) return FileRead.Missing
        if (!file.isFile) {
            return FileRead.Failed("Not a regular file: ${file.absolutePath}")
        }
        if (file.length() > maxBytes) {
            return FileRead.Failed("File exceeds $maxBytes bytes: ${file.length()}")
        }
        return try {
            FileRead.Ok(String(file.readBytes(), Charsets.UTF_8))
        } catch (e: Exception) {
            FileRead.Failed("Could not read ${file.absolutePath}", e)
        }
    }

    /**
     * Writes a sibling file atomically, using the same temp-file + rename
     * strategy as [write].
     *
     * @return true only when the new content is in place; false never means
     * "probably fine" — callers must report it.
     */
    fun writeFile(context: Context, fileName: String, text: String): Boolean {
        val target = file(context, fileName)
        return writeAtomic(context, target, "$fileName.tmp", text)
    }

    /** Backup slot [n] in 1..5. */
    fun backupFile(context: Context, n: Int): File {
        require(n in 1..BACKUP_COUNT) { "Backup slot must be between 1 and $BACKUP_COUNT, got $n" }
        return File(dir(context), "quicknote-$n.txt")
    }

    /** Creates the directory if needed. Returns true when it exists afterwards. */
    fun ensureDir(context: Context): Boolean {
        val directory = dir(context)
        return directory.isDirectory || directory.mkdirs()
    }

    /**
     * Reads the quick note as UTF-8.
     *
     * @return the content, or null when the file does not exist or cannot be read.
     */
    fun read(context: Context): String? {
        val file = mainFile(context)
        if (!file.isFile) return null
        return try {
            String(file.readBytes(), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes the quick note atomically.
     *
     * The new content goes to `quicknote.tmp` first and is fsynced, then renamed
     * over `quicknote.txt`. A failed write therefore leaves the previous note
     * untouched instead of truncating it.
     */
    fun write(context: Context, text: String): Boolean =
        writeAtomic(context, mainFile(context), MAIN_TMP_NAME, text)

    /**
     * Stores [content] in one of five backup slots.
     *
     * The first free slot is used; when all slots are full the one with the
     * oldest `lastModified()` is replaced.
     *
     * @return true when the backup was written.
     */
    fun backup(context: Context, content: String): Boolean {
        val directory = dir(context)
        if (!directory.isDirectory && !directory.mkdirs()) return false

        var slot = 0
        for (n in 1..BACKUP_COUNT) {
            if (!backupFile(context, n).exists()) {
                slot = n
                break
            }
        }
        if (slot == 0) {
            // Pick the oldest slot with a real timestamp. A zero timestamp means
            // "unknown" and must not let slot 1 win by default; only when every
            // slot reports zero do we fall back to slot 1.
            var oldestTime = Long.MAX_VALUE
            var oldestSlot = 0
            for (n in 1..BACKUP_COUNT) {
                val modified = backupFile(context, n).lastModified()
                if (modified <= 0L) continue
                if (modified < oldestTime) {
                    oldestTime = modified
                    oldestSlot = n
                }
            }
            slot = if (oldestSlot != 0) oldestSlot else 1
        }

        val tmpName = "quicknote-$slot.tmp"
        return writeAtomic(context, backupFile(context, slot), tmpName, content)
    }

    private fun writeAtomic(context: Context, target: File, tmpName: String, text: String): Boolean {
        val directory = target.parentFile ?: return false
        if (!directory.isDirectory && !directory.mkdirs()) return false

        val tmp = File(directory, tmpName)
        try {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
        } catch (e: Exception) {
            tmp.delete()
            return false
        }

        if (tmp.renameTo(target)) return true
        // renameTo replaces the destination on Linux/Android. Keep a guarded
        // java.nio fallback for file systems where it does not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
                return true
            } catch (e: Exception) {
                // Fall through: keeping the old note is better than losing it.
            }
        }
        tmp.delete()
        return false
    }
}
