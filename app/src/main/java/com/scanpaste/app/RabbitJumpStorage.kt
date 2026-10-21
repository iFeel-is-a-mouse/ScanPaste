package com.scanpaste.app

import android.content.Context
import android.util.Log

/**
 * Persistence for the Rabbit Jump save files (best.txt, game.txt).
 *
 * Both files live next to the quick note in the app-private directory
 * (`Android/data/<package>/files/Documents/scanpaste/`) via [QuickNoteStorage],
 * so they share one storage location, need no storage permission and are
 * removed on uninstall. Only that private directory is read: the app never
 * scans shared storage on its own.
 *
 * The distinction between "missing" and "unreadable" is deliberate. A missing
 * file has no user data, so a default may be written. A file that exists but
 * cannot be read may still be the user's configuration; it is reported and
 * left untouched instead of being replaced.
 */
object RabbitJumpStorage {

    private const val TAG = "RabbitJumpStorage"

    /**
     * game.txt/best.txt are tiny; cap reads so a huge file cannot blow up the
     * game.
     */
    internal const val MAX_SAVE_BYTES = 64 * 1024

    /**
     * Reads the best score.
     *
     * @return the stored value (possibly empty), never null.
     */
    fun readBest(context: Context): String {
        val privateRead = QuickNoteStorage.readFile(context, RabbitJumpFiles.BEST_FILE, MAX_SAVE_BYTES)
        val privateText = when (privateRead) {
            is QuickNoteStorage.FileRead.Ok -> privateRead.text
            is QuickNoteStorage.FileRead.Failed -> {
                Log.e(TAG, "Cannot read ${RabbitJumpFiles.BEST_FILE}", privateRead.cause)
                return ""
            }
            QuickNoteStorage.FileRead.Missing -> null
        }
        return RabbitJumpFiles.resolveBest(privateText).text
    }

    /**
     * Writes the best score.
     *
     * @return false when the write did not happen; callers must not treat that
     * as success.
     */
    fun writeBest(context: Context, data: String): Boolean {
        val written = QuickNoteStorage.writeFile(context, RabbitJumpFiles.BEST_FILE, data)
        if (!written) Log.w(TAG, "Could not write ${RabbitJumpFiles.BEST_FILE}")
        return written
    }

    /**
     * Returns the effective game.txt content.
     *
     * An existing non-blank file is user configuration and wins. A blank
     * existing file is filled with the localized default. When the file is
     * missing the localized default (sprites plus [defaultPangram]) is written
     * so the user can edit it.
     *
     * @param defaultPangram default pangram already resolved for the current UI
     * language by the caller (`R.string.rabbit_default_pangram`).
     */
    fun loadGame(context: Context, defaultPangram: String): String {
        val privateRead = QuickNoteStorage.readFile(context, RabbitJumpFiles.GAME_FILE, MAX_SAVE_BYTES)
        val privateText = when (privateRead) {
            is QuickNoteStorage.FileRead.Ok -> privateRead.text
            is QuickNoteStorage.FileRead.Failed -> {
                // Do not write: an unreadable file may still be the user's
                // configuration. Play with the default in memory.
                Log.e(TAG, "Cannot read ${RabbitJumpFiles.GAME_FILE}; using in-memory default", privateRead.cause)
                return RabbitJumpFiles.defaultGameText(defaultPangram)
            }
            QuickNoteStorage.FileRead.Missing -> null
        }

        val resolution = RabbitJumpFiles.resolveGame(privateText, defaultPangram)
        if (resolution.needsWrite &&
            !QuickNoteStorage.writeFile(context, RabbitJumpFiles.GAME_FILE, resolution.text)
        ) {
            // Still playable: the game runs with the returned text, but the file
            // is not there for the user to edit.
            Log.w(TAG, "Could not write ${RabbitJumpFiles.GAME_FILE}; using in-memory content")
        }
        return resolution.text
    }
}
