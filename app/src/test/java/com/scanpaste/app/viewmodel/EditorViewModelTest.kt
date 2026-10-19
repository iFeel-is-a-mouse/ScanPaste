package com.scanpaste.app.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Unit tests for EditorViewModel.
 *
 * The editor keeps full-text snapshot history: every committed change can be
 * undone and redone. LiveData values are read directly via `.value`; a
 * synchronous ArchTaskExecutor is installed because setValue() asserts the
 * main thread and androidx.arch.core:core-testing is not a dependency.
 */
class EditorViewModelTest {

    private lateinit var vm: EditorViewModel

    @Before
    fun setUp() {
        vm = EditorViewModel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Initial state
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `initial text should be empty`() {
        assertThat(vm.text.value).isEqualTo("")
    }

    @Test
    fun `initial canUndo and canRedo should be false`() {
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // appendText()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `appendText should set text and enable undo`() {
        vm.appendText("hello")

        assertThat(vm.text.value).isEqualTo("hello")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
        assertThat(undoStackSize(vm)).isEqualTo(1)
        assertThat(undoStackSnapshot(vm)).containsExactly("")
    }

    @Test
    fun `multiple appends should concatenate text`() {
        vm.appendText("hello")
        vm.appendText(" ")
        vm.appendText("world")

        assertThat(vm.text.value).isEqualTo("hello world")
        assertThat(undoStackSize(vm)).isEqualTo(3)
    }

    @Test
    fun `append empty string should be a no-op`() {
        vm.appendText("hello")
        val undoSizeBefore = undoStackSize(vm)

        vm.appendText("")

        assertThat(vm.text.value).isEqualTo("hello")
        assertThat(undoStackSize(vm)).isEqualTo(undoSizeBefore)
    }

    @Test
    fun `appendText with whitespace-only should be treated as valid append`() {
        vm.appendText("   ")
        assertThat(vm.text.value).isEqualTo("   ")
        assertThat(vm.canUndo.value).isTrue()
    }

    @Test
    fun `appendText with newlines should preserve them`() {
        vm.appendText("line1\nline2\nline3")
        assertThat(vm.text.value).isEqualTo("line1\nline2\nline3")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // undo()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `undo on empty stack should be no-op`() {
        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `single undo should restore previous text and swap button states`() {
        vm.appendText("hello")
        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
        assertThat(redoStackSnapshot(vm)).containsExactly("hello")
    }

    @Test
    fun `multiple undos should progressively restore snapshots`() {
        vm.appendText("a")
        vm.appendText("bb")
        vm.appendText("ccc")

        vm.undo()
        assertThat(vm.text.value).isEqualTo("abb")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()

        vm.undo()
        assertThat(vm.text.value).isEqualTo("a")

        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
    }

    @Test
    fun `undo all should move every snapshot to the redo stack`() {
        vm.appendText("a")
        vm.appendText("b")

        vm.undo()
        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(redoStackSize(vm)).isEqualTo(2)
    }

    @Test
    fun `undo should restore the exact state before a manual edit`() {
        vm.appendText("hello")
        vm.updateTextManually("xyz")

        vm.undo()

        assertThat(vm.text.value).isEqualTo("hello")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // redo()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `redo on empty stack should be no-op`() {
        vm.redo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `single undo-redo should restore text and button states`() {
        vm.appendText("hello")
        vm.undo()
        vm.redo()

        assertThat(vm.text.value).isEqualTo("hello")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `multiple undo-redo should progressively restore`() {
        vm.appendText("a")
        vm.appendText("b")
        vm.appendText("c")

        vm.undo()
        vm.undo()
        assertThat(vm.text.value).isEqualTo("a")
        assertThat(vm.canRedo.value).isTrue()

        vm.redo()
        assertThat(vm.text.value).isEqualTo("ab")
        vm.redo()
        assertThat(vm.text.value).isEqualTo("abc")

        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.canUndo.value).isTrue()
    }

    @Test
    fun `redo when redo stack empty should be no-op`() {
        vm.appendText("a")
        vm.undo()
        vm.redo()

        vm.redo()

        assertThat(vm.text.value).isEqualTo("a")
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.canUndo.value).isTrue()
    }

    @Test
    fun `undo-redo-undo cycle should work correctly`() {
        vm.appendText("a")

        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canRedo.value).isTrue()
        assertThat(vm.canUndo.value).isFalse()

        vm.redo()
        assertThat(vm.text.value).isEqualTo("a")
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.canUndo.value).isTrue()

        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canRedo.value).isTrue()
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `new append after undo should clear redo stack`() {
        vm.appendText("a")
        vm.undo()
        assertThat(vm.canRedo.value).isTrue()

        vm.appendText("b")

        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.text.value).isEqualTo("b")
        assertThat(vm.canUndo.value).isTrue()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateTextManually()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `updateTextManually should update text`() {
        vm.updateTextManually("new text")
        assertThat(vm.text.value).isEqualTo("new text")
    }

    @Test
    fun `each manual edit should be undoable`() {
        vm.updateTextManually("h")
        vm.updateTextManually("he")
        vm.updateTextManually("hel")

        assertThat(vm.text.value).isEqualTo("hel")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("he")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("h")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `manual undo should be redoable`() {
        vm.updateTextManually("abc")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("")

        vm.redo()
        assertThat(vm.text.value).isEqualTo("abc")
    }

    @Test
    fun `manual edit after undo should clear redo`() {
        vm.updateTextManually("abc")
        vm.undo()

        vm.updateTextManually("xyz")

        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.text.value).isEqualTo("xyz")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
    }

    @Test
    fun `updateTextManually with unchanged text should be a no-op`() {
        vm.updateTextManually("same")
        val undoSizeBefore = undoStackSize(vm)

        vm.updateTextManually("same")

        assertThat(undoStackSize(vm)).isEqualTo(undoSizeBefore)
        assertThat(vm.text.value).isEqualTo("same")
    }

    @Test
    fun `updateTextManually with empty string should be undoable`() {
        vm.appendText("hello")
        vm.updateTextManually("")

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isTrue()
        vm.undo()
        assertThat(vm.text.value).isEqualTo("hello")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // clearAll()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `clearAll should empty text and stay undoable`() {
        vm.appendText("hello")

        vm.clearAll()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isTrue()

        vm.undo()
        assertThat(vm.text.value).isEqualTo("hello")
    }

    @Test
    fun `clearAll should be redoable`() {
        vm.appendText("hello")
        vm.clearAll()
        vm.undo()

        vm.redo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `clearAll on empty text should be a no-op`() {
        vm.appendText("hello")
        vm.clearAll()
        val undoSizeBefore = undoStackSize(vm)

        vm.clearAll()

        assertThat(undoStackSize(vm)).isEqualTo(undoSizeBefore)
        assertThat(vm.canRedo.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // replaceText()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `replaceText should be undoable`() {
        vm.appendText("draft")

        vm.replaceText("loaded note")

        assertThat(vm.text.value).isEqualTo("loaded note")
        assertThat(vm.canUndo.value).isTrue()

        vm.undo()
        assertThat(vm.text.value).isEqualTo("draft")

        vm.redo()
        assertThat(vm.text.value).isEqualTo("loaded note")
    }

    @Test
    fun `replaceText should clear an existing redo stack`() {
        vm.appendText("a")
        vm.undo()
        assertThat(vm.canRedo.value).isTrue()

        vm.replaceText("loaded note")

        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `replaceText with identical text should be a no-op`() {
        vm.appendText("same")
        val undoSizeBefore = undoStackSize(vm)

        vm.replaceText("same")

        assertThat(undoStackSize(vm)).isEqualTo(undoSizeBefore)
        assertThat(vm.text.value).isEqualTo("same")
    }

    @Test
    fun `replaceText with empty text should be undoable`() {
        vm.appendText("hello")

        vm.replaceText("")

        assertThat(vm.text.value).isEqualTo("")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("hello")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // setInitialText()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `setInitialText should set text and clear all history`() {
        vm.appendText("typed")
        vm.undo()
        assertThat(vm.canRedo.value).isTrue()

        vm.setInitialText("restored note")

        assertThat(vm.text.value).isEqualTo("restored note")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isFalse()
        assertThat(undoStackSize(vm)).isEqualTo(0)
        assertThat(redoStackSize(vm)).isEqualTo(0)
    }

    @Test
    fun `after setInitialText the baseline itself is not undoable`() {
        vm.setInitialText("restored note")

        vm.undo()

        assertThat(vm.text.value).isEqualTo("restored note")
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `editing after setInitialText should undo back to the baseline`() {
        vm.setInitialText("restored note")
        vm.appendText("!")

        vm.undo()

        assertThat(vm.text.value).isEqualTo("restored note")
        assertThat(vm.canUndo.value).isFalse()

        vm.redo()
        assertThat(vm.text.value).isEqualTo("restored note!")
    }

    @Test
    fun `setInitialText should clear history even when text is unchanged`() {
        vm.appendText("same")
        vm.undo()
        vm.redo()

        vm.setInitialText("same")

        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAX_HISTORY limit
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `history should keep exactly MAX_HISTORY entries without eviction`() {
        repeat(EditorViewModel.MAX_HISTORY) { i -> vm.updateTextManually("t$i") }

        assertThat(undoStackSize(vm)).isEqualTo(EditorViewModel.MAX_HISTORY)

        repeat(EditorViewModel.MAX_HISTORY) { vm.undo() }
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `history should evict the oldest snapshot beyond MAX_HISTORY`() {
        val commits = EditorViewModel.MAX_HISTORY + 50
        repeat(commits) { i -> vm.updateTextManually("t$i") }

        assertThat(undoStackSize(vm)).isEqualTo(EditorViewModel.MAX_HISTORY)

        repeat(EditorViewModel.MAX_HISTORY) { vm.undo() }

        // The first 50 snapshots were evicted, so undo stops at t49.
        assertThat(vm.text.value).isEqualTo("t49")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(undoStackSize(vm)).isEqualTo(0)
    }

    @Test
    fun `redo stack should also be capped at MAX_HISTORY`() {
        repeat(EditorViewModel.MAX_HISTORY + 50) { i -> vm.updateTextManually("t$i") }
        repeat(EditorViewModel.MAX_HISTORY) { vm.undo() }

        assertThat(redoStackSize(vm)).isEqualTo(EditorViewModel.MAX_HISTORY)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Button states and integration-like sequences
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `button states should follow the snapshot stacks`() {
        vm.appendText("a")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()

        vm.undo()
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()

        vm.redo()
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `mixed append manual clear sequence should undo and redo step by step`() {
        vm.appendText("a")
        vm.updateTextManually("ab")
        vm.appendText("c")
        vm.clearAll()

        assertThat(vm.text.value).isEqualTo("")

        vm.undo()
        assertThat(vm.text.value).isEqualTo("abc")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("ab")
        vm.undo()
        assertThat(vm.text.value).isEqualTo("a")

        vm.redo()
        assertThat(vm.text.value).isEqualTo("ab")
        vm.redo()
        assertThat(vm.text.value).isEqualTo("abc")
        vm.redo()
        assertThat(vm.text.value).isEqualTo("")
    }

    private class InstantTaskExecutor : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread(): Boolean = true
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun installInstantTaskExecutor() {
            ArchTaskExecutor.getInstance().setDelegate(InstantTaskExecutor())
        }

        @Suppress("UNCHECKED_CAST")
        private fun undoStackSize(viewModel: EditorViewModel): Int =
            (stackField(viewModel, "undoStack") as ArrayDeque<*>).size

        @Suppress("UNCHECKED_CAST")
        private fun redoStackSize(viewModel: EditorViewModel): Int =
            (stackField(viewModel, "redoStack") as ArrayDeque<*>).size

        @Suppress("UNCHECKED_CAST")
        private fun undoStackSnapshot(viewModel: EditorViewModel): List<String> =
            (stackField(viewModel, "undoStack") as ArrayDeque<String>).toList()

        @Suppress("UNCHECKED_CAST")
        private fun redoStackSnapshot(viewModel: EditorViewModel): List<String> =
            (stackField(viewModel, "redoStack") as ArrayDeque<String>).toList()

        private fun stackField(viewModel: EditorViewModel, name: String): Any {
            val field = EditorViewModel::class.java.getDeclaredField(name)
            field.isAccessible = true
            return field.get(viewModel)
        }
    }
}
