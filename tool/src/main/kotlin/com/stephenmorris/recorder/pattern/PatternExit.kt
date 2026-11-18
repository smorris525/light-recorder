package com.stephenmorris.recorder.pattern

import java.util.UUID

/** How a pattern's screens hand back to Patterns: open another pattern, the pattern was deleted, or just go back. */
sealed interface PatternExit {
    data class Open(val id: UUID) : PatternExit

    data object Deleted : PatternExit

    /** Back from Pads, which returns to Patterns, not to Pattern (spec §6). */
    data object ToPatterns : PatternExit
}
