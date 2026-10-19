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
 */
class EditorViewModel : ViewModel() {

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

    fun appendText(scannedText: String) {
        if (scannedText.isEmpty()) return
        commit((_text.value ?: "") + scannedText)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _text.value ?: ""
        redoStack.addLast(current)
        trimStack(redoStack)
        _text.value = undoStack.removeLast()
        refreshButtonStates()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _text.value ?: ""
        undoStack.addLast(current)
        trimStack(undoStack)
        _text.value = redoStack.removeLast()
        refreshButtonStates()
    }

    /** Edits made by the user directly in the editor; every edit is undoable. */
    fun updateTextManually(newText: String) {
        commit(newText)
    }

    /** Replaces the document (quick note read/import) as an undoable change. */
    fun replaceText(newText: String) {
        commit(newText)
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
        refreshButtonStates()
    }

    fun clearAll() {
        if ((_text.value ?: "").isEmpty()) return
        commit("")
    }

    private fun commit(newText: String) {
        val current = _text.value ?: ""
        if (newText == current) return
        undoStack.addLast(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _text.value = newText
        refreshButtonStates()
    }

    private fun trimStack(stack: ArrayDeque<String>) {
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    private fun refreshButtonStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    companion object {
        const val MAX_HISTORY = 200
    }
}
