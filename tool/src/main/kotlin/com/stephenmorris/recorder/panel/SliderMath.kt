package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Track
import com.stephenmorris.recorder.tape.roundHalfAway
import kotlin.math.abs

/**
 * A fader's and a pan's arithmetic: where a finger along the rail puts the
 * value, catching at home within [DETENT] dp of it, and how a value becomes a track's volume or pan.
 */
object SliderMath {
    const val DETENT = 8.0

    /** Below the quietest volume: the fader's foot is silent. */
    val VOLUME_RANGE = (Track.VOLUMES.first - 1).toDouble()..Track.VOLUMES.last.toDouble()

    /** Where 0 dB sits along the fader, 0…1 from its foot: the meter beside it marks its hot zone here too. */
    val VOLUME_HOME = (0 - VOLUME_RANGE.start) / (VOLUME_RANGE.endInclusive - VOLUME_RANGE.start)

    val PAN_RANGE = -50.0..50.0

    /** Where a finger leaves the value, and whether it's caught at home. */
    data class Slide(val value: Double, val isHome: Boolean)

    /** A finger [x] dp along a rail [length] long. */
    fun slide(x: Double, length: Double, range: ClosedFloatingPointRange<Double>, home: Double?): Slide {
        val along = x.coerceIn(0.0, maxOf(length, 0.0))
        val value = if (length > 0) range.start + along / length * (range.endInclusive - range.start) else range.start
        if (home == null) {
            return Slide(value, false)
        }
        val isHome = abs(along - position(home, length, range)) <= DETENT
        return Slide(if (isHome) home else value, isHome)
    }

    /** Where [value] sits along a rail [length] long. */
    fun position(value: Double, length: Double, range: ClosedFloatingPointRange<Double>): Double =
        (value.coerceIn(range) - range.start) / (range.endInclusive - range.start) * length

    /** The fader's value as a volume: whole dB, silent below the quietest. */
    fun volume(value: Double): Int {
        val decibels = roundHalfAway(value).toInt()
        return if (decibels < Track.VOLUMES.first) Track.SILENT else decibels
    }

    /** The pan's value as a pan: L50…C…R50 in steps of 5. */
    fun pan(value: Double): Int = roundHalfAway(value / 5).toInt() * 5

    /** Where a track's volume puts the fader: silence at its foot. */
    fun volumeValue(track: Track): Double =
        if (track.volume <= Track.SILENT) VOLUME_RANGE.start else track.volume.toDouble()
}
