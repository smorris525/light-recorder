package com.stephenmorris.recorder.core

/** Tempo limits, in beats per minute of the meter's beat unit (spec §5). */
object Tempo {
    val RANGE = 30..300

    /** [bpm] limited to [RANGE]; stored values may be corrupt. */
    fun clamped(bpm: Int): Int = bpm.coerceIn(RANGE)
}
