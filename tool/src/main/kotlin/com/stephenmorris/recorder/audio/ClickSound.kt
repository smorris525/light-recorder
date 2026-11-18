package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The click sounds, synthesized (spec §4): a short sine blip with a fast decay and a 1 ms fade-in, so it starts without
 * a click of its own. 1,760 Hz for beat 1, 1,180 Hz for other beats.
 */
object ClickSound {
    const val DURATION = 0.045

    fun samples(kind: ClickKind, sampleRate: Double): FloatArray {
        val frequency = if (kind == ClickKind.ACCENT) 1_760.0 else 1_180.0
        val peak = 0.35
        val count = (DURATION * sampleRate).toInt()
        val attack = (0.001 * sampleRate).toInt()
        return FloatArray(count) { index ->
            val time = index / sampleRate
            val decay = 0.001.pow(time / DURATION)
            val ramp = if (index < attack) index.toDouble() / attack else 1.0
            (peak * sin(2 * PI * frequency * time) * decay * ramp).toFloat()
        }
    }
}
