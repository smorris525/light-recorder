package com.stephenmorris.recorder.core

/** User-visible text about patterns, and the rule for naming duplicates. */
object PatternText {
    /** The library's meta line, for example "68 BPM · 4/4 · 4 bars" (spec §7). Swing isn't shown. */
    fun libraryMeta(bpm: Int, meter: MeterId, bars: Int): String =
        "${Tempo.clamped(bpm)} BPM · ${meter.raw} · ${SettingsRows.length(bars)}"

    /** The name a rename saves (spec §11): what was typed, trimmed, at any length; an empty result keeps [current]. */
    fun renamed(typed: String, current: String): String = sanitizedTitle(typed) ?: current

    /** Trimmed, or `null` if nothing is left. */
    fun sanitizedTitle(raw: String): String? = raw.trim().ifEmpty { null }

    /** A duplicate's name: [source] plus the lowest free numeric suffix, starting at 2. */
    fun duplicateTitle(source: String, existing: List<String>): String {
        val taken = existing.toSet()
        return generateSequence(2) { it + 1 }.map { "$source $it" }.first { it !in taken }
    }
}
