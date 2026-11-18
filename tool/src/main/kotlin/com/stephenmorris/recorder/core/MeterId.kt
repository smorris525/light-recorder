package com.stephenmorris.recorder.core

/**
 * One of the six meters (spec §5). Patterns store [raw], so it must never change. Entry order is the Time Signature
 * page's order.
 */
enum class MeterId(val raw: String) {
    THREE_FOUR("3/4"),
    FOUR_FOUR("4/4"),
    FIVE_FOUR("5/4"),
    SIX_EIGHT("6/8"),
    SEVEN_EIGHT("7/8"),
    TWELVE_EIGHT("12/8");

    /** How this meter's bar divides into steps. */
    val layout: MeterLayout
        get() = when (this) {
            THREE_FOUR -> MeterLayout(listOf(4, 4, 4), 0.25, swingAllowed = true)
            FOUR_FOUR -> MeterLayout(listOf(4, 4, 4, 4), 0.25, swingAllowed = true)
            FIVE_FOUR -> MeterLayout(listOf(4, 4, 4, 4, 4), 0.25, swingAllowed = true)
            SIX_EIGHT -> MeterLayout(listOf(3, 3), 1.0 / 3, swingAllowed = false)
            SEVEN_EIGHT -> MeterLayout(listOf(2, 2, 3), 1.0, swingAllowed = false)
            TWELVE_EIGHT -> MeterLayout(listOf(3, 3, 3, 3), 1.0 / 3, swingAllowed = false)
        }

    companion object {
        /** The meter stored as [raw]. Unknown, corrupt or missing values read as 4/4. */
        fun fromRaw(raw: String?): MeterId = entries.firstOrNull { it.raw == raw } ?: FOUR_FOUR
    }
}

/** A step's beat within the bar, and its step within that beat. */
data class BeatPosition(val beat: Int, val offset: Int)

/**
 * How a meter's bar divides into steps. Only [MeterId.layout] makes one,
 * so every layout is one of the six meters.
 */
class MeterLayout internal constructor(
    /** Steps in each beat. */
    val groups: List<Int>,
    /** One step as a fraction of the beat the BPM counts. */
    val unit: Double,
    /** Whether swing applies (x/4 only). */
    val swingAllowed: Boolean,
) {
    /** The first step of each beat within the bar. */
    val starts: List<Int> = groups.runningFold(0) { sum, group -> sum + group }.dropLast(1)

    val stepsPerBar: Int = groups.sum()

    /** Seconds per step at [bpm], clamped to the tempo range (a corrupt tempo would stall or spin the scheduler). */
    fun stepDuration(bpm: Int): Double = 60.0 / Tempo.clamped(bpm) * unit

    /** The beat, and the step within it, for [stepInBar] (`0 until stepsPerBar`). */
    fun position(stepInBar: Int): BeatPosition {
        val beat = starts.indexOfLast { it <= stepInBar }.coerceAtLeast(0)
        return BeatPosition(beat, stepInBar - starts[beat])
    }

    /**
     * How late [stepInBar] lands under [swing], in steps. Zero in meters that don't swing, so a stored swing survives
     * a switch to 6/8, 7/8 or 12/8 without playing.
     */
    fun swingDelay(swing: Swing, stepInBar: Int): Double =
        if (swingAllowed) swing.delay(position(stepInBar).offset) else 0.0

    companion object {
        val BAR_RANGE = 1..8

        /** [bars] limited to [BAR_RANGE]; stored values may be corrupt. */
        fun clampedBars(bars: Int): Int = bars.coerceIn(BAR_RANGE)
    }
}
