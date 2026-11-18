package com.stephenmorris.recorder.core

/** What Settings shows (spec §13): the arrow steps for Length and Tempo, and the main page's summaries. */
object SettingsRows {
    fun bpm(bpm: Int, step: Int): Int = Tempo.clamped(Tempo.clamped(bpm) + step)

    fun bars(bars: Int, step: Int): Int = MeterLayout.clampedBars(MeterLayout.clampedBars(bars) + step)

    /** "2 bars", or "1 bar". */
    fun length(bars: Int): String {
        val clamped = MeterLayout.clampedBars(bars)
        return if (clamped == 1) "1 bar" else "$clamped bars"
    }

    /** "108 BPM". */
    fun tempo(bpm: Int): String = "${Tempo.clamped(bpm)} BPM"

    /** Whether the Swing row shows: hidden in 6/8, 7/8 and 12/8, where the stored swing is kept but not played. */
    fun swingShown(meter: MeterId): Boolean = meter.layout.swingAllowed
}
