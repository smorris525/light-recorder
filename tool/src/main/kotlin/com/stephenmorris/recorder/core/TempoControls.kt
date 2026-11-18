package com.stephenmorris.recorder.core

/**
 * TAP on the Tempo page (spec §13): the tempo from the average interval of up to the last five
 * taps within 2.5 seconds. Needs two taps; clamped to the tempo range.
 */
class TapTempo {
    private var taps: List<Double> = emptyList()

    /** Records a tap at [timeSeconds] on any monotonic clock. Returns the tempo once there are two taps in the window. */
    fun tap(timeSeconds: Double): Int? {
        // A tap no later than the last one (a clock reset, or the same event twice) starts over.
        if (taps.isNotEmpty() && timeSeconds <= taps.last()) {
            taps = emptyList()
        }
        taps = (taps.filter { timeSeconds - it < WINDOW } + timeSeconds).takeLast(TAP_COUNT)
        if (taps.size < 2) {
            return null
        }
        val interval = (taps.last() - taps.first()) / (taps.size - 1)
        return Tempo.clamped(Math.round(60 / interval).toInt())
    }

    companion object {
        /** Taps older than this, before the latest, no longer count. */
        const val WINDOW = 2.5
        const val TAP_COUNT = 5
    }
}

/**
 * The tempo typed on the Tempo page's keypad (spec §13). It opens showing the tempo as if typed, so delete can take
 * digits off it, while the first digit typed replaces it. Once the digits make a tempo, a digit that can't extend it
 * (80 then 1, or a full 120 then 1) starts a new number, so typing tempo after tempo works. No leading 0.
 */
data class TempoEntry private constructor(
    val digits: String,
    /** The digits are the shown tempo, not typed: the next digit starts afresh. */
    private val replacesNext: Boolean,
) {
    fun pressed(digit: Int): TempoEntry {
        if (digit !in 0..9) {
            return this
        }
        val startsAfresh = replacesNext || (tempo(digits) != null && tempo(digits + digit) == null)
        val base = if (startsAfresh) "" else digits
        if (base.isEmpty() && digit == 0) {
            return TempoEntry(base, replacesNext = false)
        }
        return TempoEntry(base + digit, replacesNext = false)
    }

    fun deleted(): TempoEntry = TempoEntry(digits.dropLast(1), replacesNext = false)

    /** The tempo to play as the digits change: only digits that already make one ("12" on the way to "120" doesn't). */
    val liveTempo: Int?
        get() = tempo(digits)

    /** The tempo to set when leaving the page: the digits clamped to the range, or `null` with none. */
    val finalTempo: Int?
        get() = digits.toIntOrNull()?.let(Tempo::clamped)

    companion object {
        /** The page as it opens, showing [tempo] (clamped). */
        fun showing(tempo: Int): TempoEntry = TempoEntry(Tempo.clamped(tempo).toString(), replacesNext = true)

        private fun tempo(digits: String): Int? {
            if (digits.length > 3) {
                return null
            }
            return digits.toIntOrNull()?.takeIf { it in Tempo.RANGE }
        }
    }
}
