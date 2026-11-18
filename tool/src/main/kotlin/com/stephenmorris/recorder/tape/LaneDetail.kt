package com.stephenmorris.recorder.tape

import kotlin.math.abs
import kotlin.math.sin

/**
 * A stand-in level for audio the lane can't measure: a take still being recorded, which has no recording yet, or a
 * span with no recording behind it.
 */
object LaneDetail {
    /** The stand-in level, from 0 to 1, of whichever span without a recording covers [time], or `null` when none does. */
    fun standIn(time: Double, spans: List<Span>): Double? =
        spans.firstOrNull { it.clip == null && time >= it.start && time < it.end }?.let { amplitude(time, it.seed) }

    private fun amplitude(time: Double, seed: Double): Double {
        val wave = abs(
            sin(time * 3.7 + seed) * 0.55 + sin(time * 9.6 + seed * 2.3) * 0.3 + sin(time * 61 + seed) * 0.15,
        )
        val gate = if (sin(time * 0.8 + seed * 0.7) < -0.8) 0.05 else 1.0
        return minOf(1.0, (0.18 + 0.82 * wave) * gate)
    }
}
