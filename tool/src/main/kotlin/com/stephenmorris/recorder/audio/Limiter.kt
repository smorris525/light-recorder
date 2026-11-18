package com.stephenmorris.recorder.audio

import kotlin.math.abs
import kotlin.math.exp

/**
 * The last stage of the mix (spec §4). Kits are levelled by how loud they sound, not by their loudest possible stack,
 * so a step that piles up enough sounds could pass [ceiling]. The limiter turns the mix down at once on any sample
 * that would, so nothing past [ceiling] reaches the output, then lets go over [RELEASE]. It does nothing until then.
 *
 * Audio thread only: [process] takes no locks and allocates nothing.
 */
class Limiter(val ceiling: Float = KitMix.CEILING, sampleRate: Double) {
    private val releaseCoefficient = exp(-1 / (RELEASE * sampleRate)).toFloat()
    private var gain = 1f

    fun process(samples: FloatArray, count: Int) {
        for (index in 0 until count) {
            gain = 1 - releaseCoefficient * (1 - gain)
            val amplitude = abs(samples[index])
            if (amplitude * gain > ceiling) {
                gain = ceiling / amplitude
            }
            // Rounding in the division above could leave the sample a hair over.
            samples[index] = (samples[index] * gain).coerceIn(-ceiling, ceiling)
        }
    }

    companion object {
        /** Seconds for the gain to recover about two-thirds of the way back to unity. */
        const val RELEASE = 0.05
    }
}
