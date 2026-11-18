package com.stephenmorris.recorder.engine

import kotlin.math.log10

/**
 * A peak meter's needle: 0…1 on a decibel scale down to −60 dB, jumping up to each peak it's given and falling back from
 * it at [FALL] dB a second.
 */
class PeakMeter {
    private var decibels = FLOOR
    private var read: Long? = null

    fun level(peak: Float, nowNanos: Long): Double {
        val peakDecibels = if (peak > 0) maxOf(FLOOR, 20 * log10(peak.toDouble())) else FLOOR
        val elapsed = read?.let { ((nowNanos - it) / 1e9).coerceIn(0.0, 1.0) } ?: 1.0
        read = nowNanos
        decibels = maxOf(peakDecibels, decibels - FALL * elapsed)
        return minOf(1.0, 1 + decibels / -FLOOR)
    }

    private companion object {
        /** 0 dB to the floor in 2 s. */
        const val FALL = 30.0
        const val FLOOR = -60.0
    }
}
