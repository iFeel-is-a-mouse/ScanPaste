package com.scanpaste.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.scanpaste.app.model.AppendHistory
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EditorViewModel.
 *
 * Covers UT2–UT8 from testable-items.md (31 items total).
 *
 * Note: LiveData values are accessed directly via `.value` — this works
 * in JUnit unit tests because the setter is synchronous when no main-thread
 * observer is attached.  No InstantTaskExecutorRule is needed for the tests
 * below since we never call `observe()`.
 */
class EditorViewModelTest {

    private lateinit var vm: EditorViewModel

    @Before
    fun setUp() {
        vm = EditorViewModel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT2: Initial state
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT2_1a initial text should be empty`() {
        assertThat(vm.text.value).isEqualTo("")
    }

    @Test
    fun `UT2_1b initial textSize should be 24sp`() {
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT2_1c initial canUndo should be false`() {
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `UT2_1d initial canRedo should be false`() {
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `UT2_1e initial undoFailed should be false`() {
        assertThat(vm.undoFailed.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT2.2–UT2.5: appendText()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT2_2a appendText should set text and enable undo`() {
        vm.appendText("hello")

        assertThat(vm.text.value).isEqualTo("hello")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `UT2_2b appendText should keep textSize 24 for single line`() {
        vm.appendText("single line")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT2_3a multiple appends should concatenate text`() {
        vm.appendText("hello")
        vm.appendText(" ")
        vm.appendText("world")

        assertThat(vm.text.value).isEqualTo("hello world")
        assertThat(vm.canUndo.value).isTrue()
    }

    @Test
    fun `UT2_3b multiple appends should allow undo count equal to append count`() {
        repeat(3) { vm.appendText("x") }

        vm.undo()
        assertThat(vm.canUndo.value).isTrue()
        vm.undo()
        assertThat(vm.canUndo.value).isTrue()
        vm.undo()
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.text.value).isEqualTo("")
    }

    @Test
    fun `UT2_4 append after undo should clear redo stack`() {
        vm.appendText("a")
        vm.undo()
        assertThat(vm.canRedo.value).isTrue()

        vm.appendText("b")

        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.text.value).isEqualTo("b")
        assertThat(vm.canUndo.value).isTrue()
    }

    @Test
    fun `UT2_5 append empty string should be a no-op`() {
        vm.appendText("hello")
        val textBefore = vm.text.value
        val canUndoBefore = vm.canUndo.value

        vm.appendText("")

        assertThat(vm.text.value).isEqualTo(textBefore)
        assertThat(vm.canUndo.value).isEqualTo(canUndoBefore)
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
    // UT3: undo()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT3_1 undo on empty stack should be no-op`() {
        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.undoFailed.value).isFalse()
    }

    @Test
    fun `UT3_2 single undo should restore text and swap button states`() {
        vm.appendText("hello")
        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
    }

    @Test
    fun `UT3_3 multiple undos should progressively restore text`() {
        vm.appendText("a")
        vm.appendText("bb")
        vm.appendText("ccc")

        vm.undo()
        assertThat(vm.text.value).isEqualTo("abb")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()

        vm.undo()
        assertThat(vm.text.value).isEqualTo("a")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()

        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
    }

    @Test
    fun `UT3_4 undo all to empty and verify redo stack size`() {
        vm.appendText("a")
        vm.appendText("b")
        vm.undo()
        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
        assertThat(realRedoStackSize(vm)).isEqualTo(2)
    }

    @Test
    fun `UT3_5 undo should recalculate text size`() {
        vm.appendText("1\n2\n3\n4\n5\n6\n7\n8\n9")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_SMALL)

        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT3_6 fault-tolerant undo with full-text search`() {
        vm.appendText("hello")
        vm.updateTextManually("_hello")

        vm.undo()

        assertThat(vm.text.value).isEqualTo("_")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
        assertThat(vm.undoFailed.value).isFalse()
    }

    @Test
    fun `UT3_7 undo when text not found should keep state and signal failure`() {
        vm.appendText("hello")
        vm.updateTextManually("xyz")

        vm.undo()

        assertThat(vm.text.value).isEqualTo("xyz")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.undoFailed.value).isTrue()
    }

    @Test
    fun `undoFailed handled should reset flag`() {
        vm.appendText("hello")
        vm.updateTextManually("xyz")
        vm.undo()
        assertThat(vm.undoFailed.value).isTrue()

        vm.onUndoFailedHandled()
        assertThat(vm.undoFailed.value).isFalse()
    }

    @Test
    fun `undo when text is shorter but prefix matches should partial-delete`() {
        vm.appendText("hello")
        vm.updateTextManually("hel")

        vm.undo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
    }

    @Test
    fun `undo when text changed in middle should use full-text search`() {
        vm.appendText("world")
        vm.updateTextManually("hello_world")

        vm.undo()
        assertThat(vm.text.value).isEqualTo("hello_")
        assertThat(vm.canUndo.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT4: redo()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT4_1 redo on empty stack should be no-op`() {
        vm.redo()

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `UT4_2 single undo-redo should restore text and button states`() {
        vm.appendText("hello")
        vm.undo()
        vm.redo()

        assertThat(vm.text.value).isEqualTo("hello")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `UT4_3 multiple undo-redo should progressively restore`() {
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
    fun `UT4_4 redo when redo stack empty should be no-op`() {
        vm.appendText("a")
        vm.undo()
        vm.redo()

        vm.redo()

        assertThat(vm.text.value).isEqualTo("a")
        assertThat(vm.canRedo.value).isFalse()
        assertThat(vm.canUndo.value).isTrue()
    }

    @Test
    fun `UT4_5 undo-redo-undo cycle should work correctly`() {
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
    fun `redo should recalculate text size`() {
        vm.appendText("1\n2\n3\n4\n5\n6\n7\n8\n9")
        vm.undo()
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)

        vm.redo()
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_SMALL)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT5: Stack depth limit
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT5_1 101 appends should keep only last 100 undo entries`() {
        vm.appendText("first")
        repeat(100) { i -> vm.appendText("x$i") }

        assertThat(realUndoStackSize(vm)).isEqualTo(EditorViewModel.MAX_HISTORY)
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.text.value).endsWith("x99")
    }

    @Test
    fun `UT5_2 after 101 appends the first entry should be evicted`() {
        vm.appendText("first")
        repeat(100) { vm.appendText("fill") }

        repeat(100) { vm.undo() }

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
    }

    @Test
    fun `stack should handle exactly MAX_HISTORY appends without eviction`() {
        vm.appendText("first")
        repeat(99) { vm.appendText("x") }

        assertThat(realUndoStackSize(vm)).isEqualTo(EditorViewModel.MAX_HISTORY)

        repeat(100) { vm.undo() }
        assertThat(vm.text.value).isEqualTo("")
    }

    @Test
    fun `stack should evict oldest when exceeding limit by a lot`() {
        repeat(200) { i -> vm.appendText("a$i") }

        assertThat(realUndoStackSize(vm)).isEqualTo(EditorViewModel.MAX_HISTORY)
        assertThat(vm.canUndo.value).isTrue()

        repeat(100) { vm.undo() }
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.canUndo.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT6: Font size calculation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT6_1 empty text should have 24sp`() {
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT6_2 exactly 3 lines should be 24sp`() {
        vm.appendText("1\n2\n3")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT6_2b less than 3 lines should be 24sp`() {
        vm.appendText("single line")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)

        vm.appendText("\n")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT6_3 boundary 4 lines should be 20sp`() {
        vm.appendText("1\n2\n3\n4")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_MEDIUM)
    }

    @Test
    fun `UT6_3b exactly 8 lines should be 20sp`() {
        vm.appendText("1\n2\n3\n4\n5\n6\n7\n8")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_MEDIUM)
    }

    @Test
    fun `UT6_4 boundary 9 lines should be 16sp`() {
        vm.appendText("1\n2\n3\n4\n5\n6\n7\n8\n9")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_SMALL)
    }

    @Test
    fun `UT6_4b exactly 15 lines should be 16sp`() {
        val text = (1..15).joinToString("\n")
        vm.appendText(text)
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_SMALL)
    }

    @Test
    fun `UT6_5 boundary 16 lines should be 12sp`() {
        val text = (1..16).joinToString("\n")
        vm.appendText(text)
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_MIN)
    }

    @Test
    fun `UT6_5b many lines should stay at 12sp minimum`() {
        val text = (1..100).joinToString("\n")
        vm.appendText(text)
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_MIN)
    }

    @Test
    fun `UT6_6 very long single line should be 24sp`() {
        vm.appendText("a".repeat(500))
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    @Test
    fun `UT6_7 undo should restore text size to previous value`() {
        vm.appendText("1\n2\n3\n4\n5\n6\n7\n8\n9")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_SMALL)

        vm.undo()
        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT7: updateTextManually()
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT7_1 updateTextManually should update text`() {
        vm.updateTextManually("new text")
        assertThat(vm.text.value).isEqualTo("new text")
    }

    @Test
    fun `UT7_2 updateTextManually should recalculate text size`() {
        vm.updateTextManually("1\n2\n3\n4\n5\n6\n7\n8\n9")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_SMALL)
    }

    @Test
    fun `UT7_3 updateTextManually should not affect undo-redo stacks`() {
        vm.appendText("a")
        assertThat(vm.canUndo.value).isTrue()

        vm.updateTextManually("completely different")

        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.text.value).isEqualTo("completely different")
    }

    @Test
    fun `updateTextManually with empty string should set empty text and 24sp`() {
        vm.appendText("hello")
        vm.updateTextManually("")

        assertThat(vm.text.value).isEqualTo("")
        assertThat(vm.textSize.value).isEqualTo(EditorViewModel.TEXT_SIZE_LARGE)
        assertThat(vm.canUndo.value).isTrue()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UT8: Button states
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `UT8_1 initial button states should both be disabled`() {
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `UT8_2 after append canUndo should be true and canRedo false`() {
        vm.appendText("a")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `UT8_3 after undo canUndo should be false and canRedo true`() {
        vm.appendText("a")
        vm.undo()
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
    }

    @Test
    fun `UT8_4 new append after undo should clear redo button`() {
        vm.appendText("a")
        vm.undo()
        assertThat(vm.canRedo.value).isTrue()

        vm.appendText("b")
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    @Test
    fun `button states after all undos consumed should be correct`() {
        vm.appendText("a")
        vm.appendText("b")
        vm.appendText("c")

        vm.undo()
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()

        vm.undo()
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()

        vm.undo()
        assertThat(vm.canUndo.value).isFalse()
        assertThat(vm.canRedo.value).isTrue()
    }

    @Test
    fun `button states after redo all should be correct`() {
        vm.appendText("a")
        vm.appendText("b")
        vm.undo()
        vm.undo()

        vm.redo()
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isTrue()

        vm.redo()
        assertThat(vm.canUndo.value).isTrue()
        assertThat(vm.canRedo.value).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Extra: integration-like tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `complex sequence append-undo-manual-append-redo should behave correctly`() {
        vm.appendText("a")
        vm.appendText("b")
        vm.undo()
        vm.updateTextManually("a_modified")
        vm.redo()
        assertThat(vm.text.value).isEqualTo("ab_modified")
    }

    @Test
    fun `undo-redo should preserve text for single character appends`() {
        vm.appendText("X")
        vm.undo()
        vm.redo()
        assertThat(vm.text.value).isEqualTo("X")
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun realUndoStackSize(viewModel: EditorViewModel): Int {
            val field = EditorViewModel::class.java.getDeclaredField("undoStack")
            field.isAccessible = true
            return (field.get(viewModel) as ArrayDeque<*>).size
        }

        @Suppress("UNCHECKED_CAST")
        fun realRedoStackSize(viewModel: EditorViewModel): Int {
            val field = EditorViewModel::class.java.getDeclaredField("redoStack")
            field.isAccessible = true
            return (field.get(viewModel) as ArrayDeque<*>).size
        }
    }
}
