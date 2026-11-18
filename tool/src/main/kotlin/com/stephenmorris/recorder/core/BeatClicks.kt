package com.stephenmorris.recorder.core

/** Which click sounds: the accented one on beat 1, or the plain one on other beats (spec §4). */
enum class ClickKind { ACCENT, BEAT }

/** One click of the count-in or the metronome: one per beat, beat 1 accented. */
data class BeatClick(
    /** Step from the start of the bar (or of the count-in). */
    val step: Int,
    val kind: ClickKind,
    /** The count shown during a count-in: the beat number, 1 to the beats in the bar. */
    val number: Int,
)

/** One bar's clicks, one on each beat (7/8 clicks three times: 2 + 2 + 3). */
val MeterLayout.beatClicks: List<BeatClick>
    get() = starts.mapIndexed { beat, start ->
        BeatClick(start, if (beat == 0) ClickKind.ACCENT else ClickKind.BEAT, beat + 1)
    }

/** The count-in's clicks: always one bar (spec §10). */
fun MeterLayout.countInClicks(): List<BeatClick> = beatClicks

/**
 * Whether the metronome clicks while the pattern plays (spec §10), stored by [raw] as a preference. It only clicks on
 * the Pads screen, where its switch is; count-ins always click.
 */
enum class ClickMode(val raw: String) {
    OFF("off"),
    REC("rec");

    /** Whether the pattern's beats click: never, or only on the Pads screen. */
    fun clicks(onPads: Boolean): Boolean = this == REC && onPads

    companion object {
        /** The stored preference. Anything unknown or missing, including the retired "on", reads as rec. */
        fun fromStored(raw: String?): ClickMode = entries.firstOrNull { it.raw == raw } ?: REC
    }
}
