package com.scanpaste.app

/**
 * Pure (Android-free) rules for the Rabbit Jump save files.
 *
 * Keeping the decision logic here means the default-text selection can be
 * unit tested on the JVM. All Android I/O lives in [RabbitJumpStorage].
 */
object RabbitJumpFiles {

    const val BEST_FILE = "best.txt"
    const val GAME_FILE = "game.txt"

    /** Sprite defaults; not translatable, so they are not resources. */
    const val DEFAULT_RABBIT = "🐰"
    const val DEFAULT_FOX = "🦊"

    /** Result of deciding what a save file should contain. */
    data class TextResolution(val text: String, val needsWrite: Boolean)

    fun isUsable(text: String?): Boolean = text != null && text.isNotBlank()

    /**
     * Builds the content written when game.txt does not exist.
     *
     * Format: rabbit sprite, fox sprite, pangram — one per line, as documented
     * for user-editable game.txt. Line breaks inside [pangram] would corrupt
     * that format, so they are collapsed to spaces.
     */
    fun defaultGameText(pangram: String): String {
        val singleLine = pangram.trim().replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return "$DEFAULT_RABBIT\n$DEFAULT_FOX\n$singleLine"
    }

    /**
     * Decides the effective game.txt content.
     *
     * Priority: non-blank private file > localized default. A file has to be
     * written when the value came from the default (first run or blank file).
     */
    fun resolveGame(privateContent: String?, defaultPangram: String): TextResolution {
        if (isUsable(privateContent)) {
            return TextResolution(privateContent!!.trim(), false)
        }
        return TextResolution(defaultGameText(defaultPangram), true)
    }

    /**
     * Re-localizes the pangram line of an existing game.txt.
     *
     * game.txt stores the pangram, so a file created while the UI was in one
     * language would keep showing that language after the user switches. The
     * file cannot be fixed by hand (it lives in the app-private directory), so
     * the pangram is re-localized in memory: when line 3 is one of
     * [knownDefaults] (the built-in pangrams of any supported language), it is
     * replaced by [currentPangram]; a user-written pangram is left alone.
     * Nothing is written back, otherwise the user's file would flip language.
     *
     * Line 1 and 2 (sprites) and the line count are always preserved.
     *
     * @param text current file content.
     * @param currentPangram built-in pangram for the active UI language.
     * @param knownDefaults every built-in pangram, in any language.
     * @return [text] with line 3 substituted, or [text] unchanged when it is
     *   empty, has fewer than 3 lines, or line 3 is not a known default.
     */
    fun localizePangram(
        text: String,
        currentPangram: String,
        knownDefaults: Collection<String>
    ): String {
        if (text.isEmpty()) return text
        val lines = text.split("\n")
        if (lines.size < 3) return text

        val pangram = lines[2].trim()
        val isBuiltIn = pangram.isNotEmpty() && knownDefaults.any { it.trim() == pangram }
        if (!isBuiltIn) return text

        val localized = ArrayList<String>(lines.size)
        localized.addAll(lines)
        localized[2] = currentPangram
        return localized.joinToString("\n")
    }

    /**
     * Decides the effective best.txt content.
     *
     * An existing private file always wins, even when empty: an empty best
     * score is a legitimate state and must not be replaced by a default.
     */
    fun resolveBest(privateContent: String?): TextResolution {
        if (privateContent != null) {
            return TextResolution(privateContent.trim(), false)
        }
        return TextResolution("", false)
    }

    /** Escapes [value] as a JSON string literal (no external dependency needed). */
    fun jsonString(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ') {
                    out.append("\\u").append(String.format("%04x", ch.code))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
        return out.toString()
    }

    /** Builds a flat JSON object; values are string literals. */
    fun jsonObject(fields: Map<String, String>): String =
        fields.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { entry ->
            jsonString(entry.key) + ":" + jsonString(entry.value)
        }
}
