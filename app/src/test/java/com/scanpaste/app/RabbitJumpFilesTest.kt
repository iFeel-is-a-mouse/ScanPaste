package com.scanpaste.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the pure Rabbit Jump save-file rules.
 *
 * Covers: localized default text selection and the JSON escaping used to hand
 * copy to the WebView.
 */
class RabbitJumpFilesTest {

    private val defaultPangram = "The cute rabbit jumps over the lazy fox"

    // ── resolveGame: user configuration wins ──────────────────────────────

    @Test
    fun `resolveGame keeps a non-blank private file and writes nothing`() {
        val result = RabbitJumpFiles.resolveGame(
            privateContent = "🐰\n🦊\nmy own pangram",
            defaultPangram = defaultPangram
        )

        assertThat(result.text).isEqualTo("🐰\n🦊\nmy own pangram")
        assertThat(result.needsWrite).isFalse()
    }

    @Test
    fun `resolveGame trims surrounding whitespace from the private file`() {
        val result = RabbitJumpFiles.resolveGame(
            privateContent = "\n🐰\n🦊\ncustom\n\n",
            defaultPangram = defaultPangram
        )

        assertThat(result.text).isEqualTo("🐰\n🦊\ncustom")
        assertThat(result.needsWrite).isFalse()
    }

    // ── resolveGame: localized default ────────────────────────────────────

    @Test
    fun `resolveGame writes the localized default when no file is usable`() {
        val result = RabbitJumpFiles.resolveGame(null, defaultPangram)

        assertThat(result.text).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(result.needsWrite).isTrue()
    }

    @Test
    fun `resolveGame replaces a blank private file with the default`() {
        val result = RabbitJumpFiles.resolveGame("   \n", defaultPangram)

        assertThat(result.text).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(result.needsWrite).isTrue()
    }

    @Test
    fun `resolveGame selects the Chinese default when that is the language`() {
        val chinese = "可爱的小兔子跳过那只懒狐狸"

        val result = RabbitJumpFiles.resolveGame(null, chinese)

        assertThat(result.text).isEqualTo("🐰\n🦊\n$chinese")
        assertThat(result.text).doesNotContain(defaultPangram)
    }

    // ── localizePangram ───────────────────────────────────────────────────

    private val chinesePangram = "可爱的小兔子跳过那只懒狐狸"
    private val knownPangrams = setOf(defaultPangram, chinesePangram)

    @Test
    fun `localizePangram swaps a Chinese default for English when UI is English`() {
        val stored = "🐰\n🦊\n$chinesePangram"

        val result = RabbitJumpFiles.localizePangram(stored, defaultPangram, knownPangrams)

        assertThat(result).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(result).doesNotContain(chinesePangram)
    }

    @Test
    fun `localizePangram swaps an English default for Chinese when UI is Chinese`() {
        val stored = "🐰\n🦊\n$defaultPangram"

        val result = RabbitJumpFiles.localizePangram(stored, chinesePangram, knownPangrams)

        assertThat(result).isEqualTo("🐰\n🦊\n$chinesePangram")
        assertThat(result).doesNotContain(defaultPangram)
    }

    @Test
    fun `localizePangram keeps a user-written pangram untouched`() {
        val stored = "🐰\n🦊\nmy own pangram"

        val result = RabbitJumpFiles.localizePangram(stored, chinesePangram, knownPangrams)

        assertThat(result).isEqualTo(stored)
    }

    @Test
    fun `localizePangram returns text unchanged when it has fewer than three lines`() {
        val twoLines = "🐰\n🦊"

        assertThat(RabbitJumpFiles.localizePangram(twoLines, chinesePangram, knownPangrams))
            .isEqualTo(twoLines)
        assertThat(RabbitJumpFiles.localizePangram("", chinesePangram, knownPangrams)).isEmpty()
    }

    @Test
    fun `localizePangram preserves the sprite lines`() {
        val stored = "🐰\n🦊\n$defaultPangram"

        val lines = RabbitJumpFiles.localizePangram(stored, chinesePangram, knownPangrams).split("\n")

        assertThat(lines).hasSize(3)
        assertThat(lines[0]).isEqualTo(RabbitJumpFiles.DEFAULT_RABBIT)
        assertThat(lines[1]).isEqualTo(RabbitJumpFiles.DEFAULT_FOX)
    }

    @Test
    fun `localizePangram recognises a default that carries surrounding spaces`() {
        val stored = "🐰\n🦊\n  $defaultPangram  "

        val result = RabbitJumpFiles.localizePangram(stored, chinesePangram, knownPangrams)

        assertThat(result).isEqualTo("🐰\n🦊\n$chinesePangram")
    }

    @Test
    fun `localizePangram keeps a blank third line and unknown lines`() {
        val stored = "🐰\n🦊\n"

        assertThat(RabbitJumpFiles.localizePangram(stored, chinesePangram, knownPangrams))
            .isEqualTo(stored)
    }

    // ── defaultGameText format ────────────────────────────────────────────

    @Test
    fun `defaultGameText keeps the documented three-line format`() {
        val text = RabbitJumpFiles.defaultGameText(defaultPangram)

        val lines = text.split("\n")
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).isEqualTo("🐰")
        assertThat(lines[1]).isEqualTo("🦊")
        assertThat(lines[2]).isEqualTo(defaultPangram)
    }

    @Test
    fun `defaultGameText collapses line breaks so the pangram stays on line three`() {
        val text = RabbitJumpFiles.defaultGameText("  first line\nsecond line  ")

        assertThat(text).isEqualTo("🐰\n🦊\nfirst line second line")
        assertThat(text.split("\n")).hasSize(3)
    }

    // ── resolveBest ───────────────────────────────────────────────────────

    @Test
    fun `resolveBest keeps the private score`() {
        val result = RabbitJumpFiles.resolveBest("3,2026-10-21 10:00:00")

        assertThat(result.text).isEqualTo("3,2026-10-21 10:00:00")
        assertThat(result.needsWrite).isFalse()
    }

    @Test
    fun `resolveBest treats an existing empty private file as authoritative`() {
        val result = RabbitJumpFiles.resolveBest("")

        assertThat(result.text).isEmpty()
        assertThat(result.needsWrite).isFalse()
    }

    @Test
    fun `resolveBest returns empty without writing when nothing exists`() {
        val result = RabbitJumpFiles.resolveBest(null)

        assertThat(result.text).isEmpty()
        assertThat(result.needsWrite).isFalse()
    }

    // ── JSON bridging ─────────────────────────────────────────────────────

    @Test
    fun `jsonString escapes quotes backslashes and newlines`() {
        assertThat(RabbitJumpFiles.jsonString("a\"b\\c\nd"))
            .isEqualTo("\"a\\\"b\\\\c\\nd\"")
    }

    @Test
    fun `jsonString escapes control characters`() {
        assertThat(RabbitJumpFiles.jsonString("a\u0001b")).isEqualTo("\"a\\u0001b\"")
    }

    @Test
    fun `jsonObject emits a flat object with every key`() {
        val json = RabbitJumpFiles.jsonObject(linkedMapOf("jump" to "跳跃", "level" to "第%1\$d关"))

        assertThat(json).isEqualTo("{\"jump\":\"跳跃\",\"level\":\"第%1\$d关\"}")
    }
}
