package com.stephenmorris.recorder.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * The master-bus compressor (spec §4), after Web Audio's `DynamicsCompressorNode`, with
 * threshold -12 dB, ratio 4:1, and Web Audio's default 30 dB knee, 3 ms attack and 250 ms release. As in Web Audio,
 * the knee sits above the threshold. There's no make-up gain, so it only ever turns sound down.
 *
 * Audio thread only: [process] takes no locks and allocates nothing.
 */
class Compressor(sampleRate: Double) {
    private val attackCoefficient = exp(-1 / (ATTACK * sampleRate)).toFloat()
    private val releaseCoefficient = exp(-1 / (RELEASE * sampleRate)).toFloat()

    /** The followed peak level, as amplitude. */
    private var level = 0f

    fun process(samples: FloatArray, count: Int) {
        for (index in 0 until count) {
            val amplitude = abs(samples[index])
            val coefficient = if (amplitude > level) attackCoefficient else releaseCoefficient
            level = coefficient * level + (1 - coefficient) * amplitude
            if (level < 1e-9f) {
                // Keeps the follower out of subnormal numbers, which are slow, in long silences.
                level = 0f
            }
            if (level <= THRESHOLD_AMPLITUDE) {
                continue
            }
            val change = gainChange(20 * log10(level.toDouble()))
            samples[index] *= 10.0.pow(change / 20).toFloat()
        }
    }

    companion object {
        const val THRESHOLD = -12.0
        const val KNEE = 30.0
        const val RATIO = 4.0
        const val ATTACK = 0.003
        const val RELEASE = 0.25

        private val THRESHOLD_AMPLITUDE = 10.0.pow(THRESHOLD / 20).toFloat()

        /**
         * The gain change, in dB (zero or less), for a signal at [level] dB: none up to the threshold, easing through
         * the knee to the full ratio above it.
         */
        fun gainChange(level: Double): Double {
            val over = level - THRESHOLD
            return when {
                over <= 0 -> 0.0
                over <= KNEE -> (1 / RATIO - 1) * over * over / (2 * KNEE)
                else -> (1 / RATIO - 1) * (over - KNEE / 2)
            }
        }
    }
}
