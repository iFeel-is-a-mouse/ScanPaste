package com.scanpaste.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.scanpaste.app.model.AppendHistory

/**
 * ViewModel managing editor text state and undo/redo stacks.
 */
class EditorViewModel : ViewModel() {

    private val _text = MutableLiveData("")
    val text: LiveData<String> = _text

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> = _canRedo

    private val _undoFailed = MutableLiveData(false)
    val undoFailed: LiveData<Boolean> = _undoFailed

    private val undoStack = ArrayDeque<AppendHistory>()
    private val redoStack = ArrayDeque<AppendHistory>()

    fun appendText(scannedText: String) {
        if (scannedText.isEmpty()) return
        val current = _text.value ?: ""
        pushUndo(AppendHistory(position = current.length, text = scannedText))
        redoStack.clear()
        _text.value = current + scannedText
        refreshButtonStates()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val history = undoStack.removeLast()
        val current = _text.value ?: ""

        // Handle "clear" undo: if position=0 and text was cleared,
        // restore the full text from the snapshot
        if (history.position == 0 && current.isEmpty()) {
            _text.value = history.text
            redoStack.addLast(AppendHistory(position = 0, text = ""))  // redo=clear again
            trimStack(redoStack)
            refreshButtonStates()
            return
        }

        if (deleteTextAt(current, history.position, history.text)) {
            redoStack.addLast(history)
            trimStack(redoStack)
            refreshButtonStates()
        } else {
            undoStack.addLast(history)
            _undoFailed.value = true
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val history = redoStack.removeLast()
        val current = _text.value ?: ""

        // Handle "clear" redo: redo empty text → clear again
        if (history.position == 0 && history.text.isEmpty()) {
            undoStack.addLast(AppendHistory(position = 0, text = current))  // undo=restore
            _text.value = ""
            trimStack(undoStack)
            refreshButtonStates()
            return
        }

        val insertPos = history.position.coerceAtMost(current.length)
        _text.value = current.substring(0, insertPos) + history.text + current.substring(insertPos)
        undoStack.addLast(history)
        trimStack(undoStack)
        refreshButtonStates()
    }

    fun updateTextManually(newText: String) {
        _text.value = newText
        refreshButtonStates()
    }

    fun onUndoFailedHandled() {
        _undoFailed.value = false
    }

    fun clearAll() {
        val current = _text.value ?: ""
        if (current.isEmpty()) return
        // Save full text as undoable snapshot (position=0 triggers clear restore)
        pushUndo(AppendHistory(position = 0, text = current))
        redoStack.clear()
        _text.value = ""
        refreshButtonStates()
    }

    private fun pushUndo(history: AppendHistory) {
        if (undoStack.size >= MAX_HISTORY) undoStack.removeFirst()
        undoStack.addLast(history)
    }

    private fun trimStack(stack: ArrayDeque<AppendHistory>) {
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    private fun deleteTextAt(text: String, position: Int, historyText: String): Boolean {
        val len = historyText.length
        if (position + len <= text.length && text.substring(position, position + len) == historyText) {
            _text.value = text.removeRange(position, position + len)
            return true
        }
        val availableLen = (text.length - position).coerceAtLeast(0)
        if (availableLen > 0 && text.substring(position, position + availableLen) == historyText.take(availableLen)) {
            _text.value = text.removeRange(position, position + availableLen)
            return true
        }
        val foundIndex = text.indexOf(historyText)
        if (foundIndex >= 0) {
            _text.value = text.removeRange(foundIndex, foundIndex + len)
            return true
        }
        return false
    }

    private fun refreshButtonStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
