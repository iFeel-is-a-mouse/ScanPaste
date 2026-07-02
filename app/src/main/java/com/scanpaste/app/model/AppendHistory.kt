package com.scanpaste.app.model

/**
 * Records a single append operation for undo/redo tracking.
 *
 * Invariants:
 * - position >= 0
 * - text.isNotEmpty()
 * - position <= text length at time of append (guaranteed by append logic)
 */
data class AppendHistory(
    val position: Int,
    val text: String
) {
    init {
        require(position >= 0) { "position must be non-negative, was: $position" }
        // text can be empty — used for "clear" redo entries
    }
}
