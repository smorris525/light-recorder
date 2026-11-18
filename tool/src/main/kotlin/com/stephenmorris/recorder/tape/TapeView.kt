package com.stephenmorris.recorder.tape

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

/**
 * The stretch of tape on screen: where it starts and how many seconds fit across. There's no zoom: the screen always
 * shows [STANDARD_SECONDS_ACROSS], and the rest of a longer song is scrolled to.
 */
data class TapeView(val start: Double = 0.0, val secondsAcross: Double = STANDARD_SECONDS_ACROSS) {
    /** Where [time] falls across [width]. */
    fun x(time: Double, width: Double): Double = (time - start) / secondsAcross * width

    /** The time at [x] across [width]. */
    fun time(x: Double, width: Double): Double = start + x / width * secondsAcross

    /** Moved later by [dx] points of tape; negative moves earlier. */
    fun scrolled(dx: Double, width: Double, songLength: Double): TapeView =
        copy(start = start + dx / width * secondsAcross).clamped(songLength)

    /** Within the song: never before 0, or showing past the end. */
    fun clamped(songLength: Double): TapeView = copy(start = maxOf(0.0, minOf(start, songLength - secondsAcross)))

    /**
     * Unchanged while [time] is in view; once it passes the right edge (or falls before the left), the page starts at
     * [time].
     */
    fun following(time: Double, songLength: Double): TapeView {
        if (time >= start && time < start + secondsAcross) {
            return this
        }
        return copy(start = time).clamped(songLength)
    }

    companion object {
        /** The seconds across the screen: a new song's blank tape, exactly. */
        const val STANDARD_SECONDS_ACROSS = Song.BLANK_TAPE
    }
}

/** The ruler's ticks: a label every [major] seconds, a tick every [minor]. */
data class RulerScale(val major: Double, val minor: Double) {
    /** The nearest minor tick. */
    fun snap(time: Double): Double = roundHalfAway(time / minor) * minor

    companion object {
        /** Finest first. */
        private val LADDER = listOf(
            RulerScale(0.5, 0.05),
            RulerScale(1.0, 0.1),
            RulerScale(10.0, 1.0),
            RulerScale(60.0, 10.0),
            RulerScale(600.0, 60.0),
        )

        /**
         * The finest scale whose minor ticks are at least 6 dp apart and major labels at least 44 dp apart, else the
         * coarsest.
         */
        fun forView(secondsAcross: Double, width: Double): RulerScale = LADDER.firstOrNull {
            it.minor / secondsAcross * width >= 6 && it.major / secondsAcross * width >= 44
        } ?: LADDER.last()

        /** "1:05", with as many decimals as the minor ticks need: "1:05.5", "1:05.55". */
        fun label(time: Double, scale: RulerScale): String {
            val decimals = if (scale.minor >= 1) 0 else if (scale.minor >= 0.1) 1 else 2
            val unit = 10.0.pow(decimals).toInt()
            val units = roundHalfAway(maxOf(0.0, time) * unit).toInt()
            val seconds = units / unit
            var text = "${seconds / 60}:" + (seconds % 60).toString().padStart(2, '0')
            if (decimals > 0) {
                text += "." + (units % unit).toString().padStart(decimals, '0')
            }
            return text
        }
    }
}

/** Rounded to the nearest whole number, halves away from zero. */
internal fun roundHalfAway(value: Double): Double = sign(value) * floor(abs(value) + 0.5)
