package com.scanpaste.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel managing editor text state and undo/redo history.
 *
 * History is stored as full-text snapshots rather than append records. Every
 * committed change (manual typing, scan append, clear, quick note load/import)
 * can therefore be undone and redone, regardless of how the text was edited.
 *
 * Consecutive manual edits are grouped: typing bursts within
 * [MANUAL_GROUP_MS] collapse into one undo step, while programmatic changes
 * (scan append, clear, replace) always start a new step.
 */
class EditorViewModel : ViewModel() {

    /** Where a committed change came from; only manual typing is groupable. */
    private enum class CommitSource { MANUAL, OTHER }

    private val _text = MutableLiveData("")
    val text: LiveData<String> = _text

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> = _canRedo

    /** Full-text states from before each change, oldest first. */
    private val undoStack = ArrayDeque<String>()

    /** Full-text states from after each change, oldest first. */
    private val redoStack = ArrayDeque<String>()

    /** True when the last committed change came from manual typing. */
    private var lastCommitWasManual = false

    /** Timestamp of the last manual commit, in [nowProvider] time. */
    private var lastManualCommitAt = 0L

    /** Clock injection point so tests can control grouping without sleeping. */
    internal var nowProvider: () -> Long = { System.currentTimeMillis() }

    fun appendText(scannedText: String) {
        if (scannedText.isEmpty()) return
        commit((_text.value ?: "") + scannedText, CommitSource.OTHER)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _text.value ?: ""
        redoStack.addLast(current)
        // No budget trim here: trimming mid-undo could discard a state that
        // the current undo/redo path still needs. The budget is enforced on the
        // next commit; the bound is MAX_HISTORY_CHARS + the current text length.
        trimToMaxEntries(redoStack)
        _text.value = undoStack.removeLast()
        resetManualGrouping()
        refreshButtonStates()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _text.value ?: ""
        undoStack.addLast(current)
        trimToMaxEntries(undoStack)
        _text.value = redoStack.removeLast()
        resetManualGrouping()
        refreshButtonStates()
    }

    /**
     * Edits made by the user directly in the editor.
     *
     * Consecutive manual edits within [MANUAL_GROUP_MS] collapse into a single
     * undo step, so undo walks back over typing bursts rather than keystrokes.
     */
    fun updateTextManually(newText: String) {
        commit(newText, CommitSource.MANUAL)
    }

    /** Replaces the document (quick note read/import) as an undoable change. */
    fun replaceText(newText: String) {
        commit(newText, CommitSource.OTHER)
    }

    /**
     * Sets the document without recording history.
     *
     * Used for the automatic load at launch: the restored note is the baseline
     * state, so there is nothing before it to undo.
     */
    fun setInitialText(newText: String) {
        undoStack.clear()
        redoStack.clear()
        _text.value = newText
        resetManualGrouping()
        refreshButtonStates()
    }

    fun clearAll() {
        if ((_text.value ?: "").isEmpty()) return
        commit("", CommitSource.OTHER)
    }

    private fun commit(newText: String, source: CommitSource) {
        val current = _text.value ?: ""
        if (newText == current) return

        val now = nowProvider()
        val coalesce = source == CommitSource.MANUAL &&
            lastCommitWasManual &&
            (now - lastManualCommitAt) <= MANUAL_GROUP_MS &&
            undoStack.isNotEmpty()

        if (!coalesce) {
            undoStack.addLast(current)
            redoStack.clear()
        }

        _text.value = newText
        lastCommitWasManual = source == CommitSource.MANUAL
        lastManualCommitAt = if (source == CommitSource.MANUAL) now else 0L
        trimStacks()
        refreshButtonStates()
    }

    /** Resets grouping so the next manual edit starts a fresh undo step. */
    private fun resetManualGrouping() {
        lastCommitWasManual = false
        lastManualCommitAt = 0L
    }

    /**
     * Enforces both history limits: the entry cap and the total character
     * budget. The oldest snapshots are evicted first.
     */
    private fun trimStacks() {
        trimToMaxEntries(undoStack)
        trimToMaxEntries(redoStack)
        trimToCharBudget()
    }

    private fun trimToMaxEntries(stack: ArrayDeque<String>) {
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    /**
     * Drops the oldest undo snapshots until the history fits the character
     * budget. A single oversized snapshot is kept: the alternative would be an
     * undo history that cannot undo at all.
     */
    private fun trimToCharBudget() {
        var totalChars = historyChars()
        while (totalChars > MAX_HISTORY_CHARS && undoStack.size > 1) {
            totalChars -= undoStack.removeFirst().length
        }
        while (totalChars > MAX_HISTORY_CHARS && redoStack.isNotEmpty()) {
            totalChars -= redoStack.removeFirst().length
        }
    }

    private fun historyChars(): Int =
        undoStack.sumOf { it.length } + redoStack.sumOf { it.length }

    private fun refreshButtonStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    companion object {
        const val MAX_HISTORY = 200

        /** Consecutive manual edits closer than this are one undo step. */
        private const val MANUAL_GROUP_MS = 500L

        /** Snapshot budget for both stacks combined (~4 MB of UTF-16). */
        private const val MAX_HISTORY_CHARS = 2_000_000
    }
}
