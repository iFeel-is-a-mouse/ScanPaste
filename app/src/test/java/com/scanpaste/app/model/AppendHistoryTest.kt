package com.scanpaste.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for AppendHistory data class.
 *
 * Covers: UT1.1–UT1.4 from testable-items (with one note re UT1.4).
 */
class AppendHistoryTest {

    // ── UT1.1: Normal construction ────────────────────────────────────────

    @Test
    fun `UT1_1 should create AppendHistory with valid position and text`() {
        val history = AppendHistory(position = 0, text = "hello")

        assertThat(history.position).isEqualTo(0)
        assertThat(history.text).isEqualTo("hello")
    }

    @Test
    fun `UT1_1b should create AppendHistory with non-zero position`() {
        val history = AppendHistory(position = 5, text = " world")

        assertThat(history.position).isEqualTo(5)
        assertThat(history.text).isEqualTo(" world")
    }

    @Test
    fun `UT1_1c should create AppendHistory with integer max value position`() {
        // Boundary: position = Int.MAX_VALUE is technically valid (though
        // unreachable in practice since text length can't be that large).
        val history = AppendHistory(position = Int.MAX_VALUE, text = "x")

        assertThat(history.position).isEqualTo(Int.MAX_VALUE)
    }

    // ── UT1.2: data class equals ──────────────────────────────────────────

    @Test
    fun `UT1_2 should have structural equality for same fields`() {
        val a = AppendHistory(position = 0, text = "hello")
        val b = AppendHistory(position = 0, text = "hello")

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `UT1_2b should not equal with different position`() {
        val a = AppendHistory(position = 0, text = "hello")
        val b = AppendHistory(position = 1, text = "hello")

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `UT1_2c should not equal with different text`() {
        val a = AppendHistory(position = 0, text = "hello")
        val b = AppendHistory(position = 0, text = "world")

        assertThat(a).isNotEqualTo(b)
    }

    // ── UT1.3: data class copy ────────────────────────────────────────────

    @Test
    fun `UT1_3 should copy with modified position, preserving text`() {
        val original = AppendHistory(position = 0, text = "hello")
        val copied = original.copy(position = 5)

        assertThat(copied.position).isEqualTo(5)
        assertThat(copied.text).isEqualTo("hello")
        assertThat(copied).isNotEqualTo(original)
    }

    @Test
    fun `UT1_3b should copy with modified text, preserving position`() {
        val original = AppendHistory(position = 0, text = "hello")
        val copied = original.copy(text = "world")

        assertThat(copied.position).isEqualTo(0)
        assertThat(copied.text).isEqualTo("world")
    }

    // ── UT1.4: Empty text validation ──────────────────────────────────────

    @Test
    fun `UT1_4 should allow empty text for clear redo entries`() {
        // Empty text is valid: it represents a "clear" redo entry.
        val history = AppendHistory(position = 0, text = "")

        assertThat(history.position).isEqualTo(0)
        assertThat(history.text).isEmpty()
    }

    @Test
    fun `UT1_4b should throw on negative position`() {
        try {
            AppendHistory(position = -1, text = "hello")
            throw AssertionError("Expected IllegalArgumentException for negative position")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("position must be non-negative")
        }
    }

    @Test
    fun `UT1_4c should accept single-char text and zero position as valid`() {
        val history = AppendHistory(position = 0, text = "A")
        assertThat(history.position).isEqualTo(0)
        assertThat(history.text).isEqualTo("A")
    }

    @Test
    fun `UT1_4d should accept unicode text`() {
        val history = AppendHistory(position = 0, text = "你好世界 🌍")
        assertThat(history.text).isEqualTo("你好世界 🌍")
    }

    // ── Extra: component functions (data class destructuring) ─────────────

    @Test
    fun `should support destructuring into position and text`() {
        val history = AppendHistory(position = 42, text = "answer")
        val (pos, txt) = history

        assertThat(pos).isEqualTo(42)
        assertThat(txt).isEqualTo("answer")
    }

    @Test
    fun `toString should include position and text`() {
        val history = AppendHistory(position = 0, text = "hello")
        val str = history.toString()

        assertThat(str).contains("position=0")
        assertThat(str).contains("text=hello")
    }
}
