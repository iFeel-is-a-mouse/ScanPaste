package com.scanpaste.app

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric tests for the Rabbit Jump I/O layer.
 *
 * The pure decision rules are covered by [RabbitJumpFilesTest]; these tests
 * exercise the real file system behaviour of the app-private directory:
 * reads, three-state results, size caps and write failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RabbitJumpStorageTest {

    private lateinit var context: Context
    private lateinit var privateDir: File

    private val defaultPangram = "The cute rabbit jumps over the lazy fox"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        privateDir = QuickNoteStorage.dir(context)
        privateDir.deleteRecursively()
        privateDir.mkdirs()
    }

    private fun privateFile(name: String) = File(privateDir, name)

    private fun pinModified(file: File): Long {
        val pinned = 1_600_000_000_000L
        check(file.setLastModified(pinned)) { "Could not pin mtime of $file" }
        return pinned
    }

    // ── Private file exists ───────────────────────────────────────────────

    @Test
    fun `readBest returns the private file without writing`() {
        privateFile("best.txt").writeText("3,2026-10-21 10:00:00\n")
        val pinned = pinModified(privateFile("best.txt"))

        val value = RabbitJumpStorage.readBest(context)

        assertThat(value).isEqualTo("3,2026-10-21 10:00:00")
        assertThat(privateFile("best.txt").readText()).isEqualTo("3,2026-10-21 10:00:00\n")
        assertThat(privateFile("best.txt").lastModified()).isEqualTo(pinned)
    }

    @Test
    fun `loadGame returns the private file without writing`() {
        privateFile("game.txt").writeText("🐰\n🦊\nmy own pangram\n")
        val pinned = pinModified(privateFile("game.txt"))

        val value = RabbitJumpStorage.loadGame(context, defaultPangram)

        assertThat(value).isEqualTo("🐰\n🦊\nmy own pangram")
        assertThat(privateFile("game.txt").lastModified()).isEqualTo(pinned)
    }

    @Test
    fun `empty private best is authoritative and is not rewritten`() {
        privateFile("best.txt").writeText("")
        val pinned = pinModified(privateFile("best.txt"))

        val value = RabbitJumpStorage.readBest(context)

        assertThat(value).isEmpty()
        assertThat(privateFile("best.txt").lastModified()).isEqualTo(pinned)
        assertThat(privateFile("best.txt").readText()).isEmpty()
    }

    @Test
    fun `blank private game is filled with the localized default`() {
        privateFile("game.txt").writeText("   \n")

        val value = RabbitJumpStorage.loadGame(context, defaultPangram)

        assertThat(value).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(privateFile("game.txt").readText()).isEqualTo("🐰\n🦊\n$defaultPangram")
    }

    // ── Private file missing ──────────────────────────────────────────────

    @Test
    fun `missing best stays empty and writes nothing`() {
        val value = RabbitJumpStorage.readBest(context)

        assertThat(value).isEmpty()
        assertThat(privateFile("best.txt").exists()).isFalse()
    }

    @Test
    fun `missing game writes the localized default`() {
        val value = RabbitJumpStorage.loadGame(context, defaultPangram)

        assertThat(value).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(privateFile("game.txt").readText()).isEqualTo("🐰\n🦊\n$defaultPangram")
    }

    // ── Three-state read ──────────────────────────────────────────────────

    @Test
    fun `readFile distinguishes missing ok and failed`() {
        assertThat(QuickNoteStorage.readFile(context, "best.txt", 1024))
            .isEqualTo(QuickNoteStorage.FileRead.Missing)

        privateFile("best.txt").writeText("ok")
        assertThat(QuickNoteStorage.readFile(context, "best.txt", 1024))
            .isEqualTo(QuickNoteStorage.FileRead.Ok("ok"))

        privateFile("blocked.txt").mkdirs()
        assertThat(QuickNoteStorage.readFile(context, "blocked.txt", 1024))
            .isInstanceOf(QuickNoteStorage.FileRead.Failed::class.java)
    }

    // ── Private read failure must never be overwritten ────────────────────

    @Test
    fun `unreadable private best is reported empty and its file is kept`() {
        val privateAsDirectory = privateFile("best.txt")
        privateAsDirectory.mkdirs()
        File(privateAsDirectory, "child").writeText("x")

        val value = RabbitJumpStorage.readBest(context)

        assertThat(value).isEmpty()
        assertThat(privateAsDirectory.isDirectory).isTrue()
        assertThat(File(privateAsDirectory, "child").readText()).isEqualTo("x")
    }

    @Test
    fun `unreadable private game returns the default without writing`() {
        val privateAsDirectory = privateFile("game.txt")
        privateAsDirectory.mkdirs()
        File(privateAsDirectory, "child").writeText("x")

        val value = RabbitJumpStorage.loadGame(context, defaultPangram)

        assertThat(value).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(privateAsDirectory.isDirectory).isTrue()
        assertThat(File(privateAsDirectory, "child").readText()).isEqualTo("x")
    }

    // ── Size cap ──────────────────────────────────────────────────────────

    @Test
    fun `oversized private best is rejected instead of read`() {
        privateFile("best.txt").writeText("a".repeat(RabbitJumpStorage.MAX_SAVE_BYTES + 1))

        val value = RabbitJumpStorage.readBest(context)

        assertThat(value).isEmpty()
        assertThat(privateFile("best.txt").length()).isEqualTo(
            (RabbitJumpStorage.MAX_SAVE_BYTES + 1).toLong()
        )
    }

    @Test
    fun `oversized private game falls back to the default in memory`() {
        privateFile("game.txt").writeText("a".repeat(RabbitJumpStorage.MAX_SAVE_BYTES + 1))
        val pinned = pinModified(privateFile("game.txt"))

        val value = RabbitJumpStorage.loadGame(context, defaultPangram)

        assertThat(value).isEqualTo("🐰\n🦊\n$defaultPangram")
        assertThat(privateFile("game.txt").length()).isEqualTo(
            (RabbitJumpStorage.MAX_SAVE_BYTES + 1).toLong()
        )
        assertThat(privateFile("game.txt").lastModified()).isEqualTo(pinned)
    }

    // ── Atomic write failure ──────────────────────────────────────────────

    @Test
    fun `writeFile returns false and leaves the old file intact when the temp path is blocked`() {
        privateFile("best.txt").writeText("old")
        // A non-empty directory where best.txt.tmp is expected makes the temp
        // write fail; the previous content must survive.
        val blockedTemp = File(privateDir, "best.txt.tmp")
        blockedTemp.mkdirs()
        File(blockedTemp, "child").writeText("x")

        val written = QuickNoteStorage.writeFile(context, "best.txt", "new")

        assertThat(written).isFalse()
        assertThat(privateFile("best.txt").readText()).isEqualTo("old")
    }
}
