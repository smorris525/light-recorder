package com.stephenmorris.recorder.engine

/** The export's file name (spec §13). */
object ExportName {
    /** The song's name without the characters a path can't hold (each becomes `-`), trimmed; "Song" if nothing's left. */
    fun fileName(name: String): String {
        val cleaned = name.replace(Regex("[/:\\\\]"), "-").trim()
        return cleaned.ifEmpty { "Song" }
    }
}
