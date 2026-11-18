package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Sound
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Output levels (spec §4). The kit is levelled by how loud it sounds: its kick and snare at [TARGET_LOUDNESS]. No
 * single sound at accent, with the accented click, may pass [CEILING]; a stack of sounds that would is caught by the
 * [Limiter].
 */
object KitMix {
    /** The loudest the mix may reach. */
    const val CEILING = 0.99f

    /** The clicks' level. */
    const val CLICK_GAIN = 0.37f

    /** Where the kit puts the mean power of its kick and snare, as [loudness] measures it. */
    const val TARGET_LOUDNESS = -23.0

    /** How much of each hit [loudness] measures: about how long the ear takes to judge one. */
    const val LOUDNESS_WINDOW = 0.3

    /** The accented click's peak at [CLICK_GAIN]. */
    fun clickPeak(sampleRate: Double): Float = peak(ClickSound.samples(ClickKind.ACCENT, sampleRate)) * CLICK_GAIN

    /**
     * Roughly how loud one hit sounds, in dB: its power over the first [LOUDNESS_WINDOW], K-weighted as ITU-R BS.1770
     * (lows turned down, highs up a little) to follow the ear.
     */
    fun loudness(samples: FloatArray, sampleRate: Double): Double {
        val count = (LOUDNESS_WINDOW * sampleRate).toInt()
        val weighting = KWeighting(sampleRate)
        var energy = 0.0
        for (index in 0 until count) {
            val weighted = weighting.process(if (index < samples.size) samples[index].toDouble() else 0.0)
            energy += weighted * weighted
        }
        return 10 * log10(energy / count)
    }

    /** The mean power of a kit's kick and snare, in dB, as [loudness] measures each. */
    fun backboneLoudness(samples: Map<Sound, FloatArray>, sampleRate: Double): Double {
        val powers = listOf(Sound.KICK, Sound.SNARE).map {
            10.0.pow(loudness(samples[it] ?: FloatArray(0), sampleRate) / 10)
        }
        return 10 * log10(powers.sum() / powers.size)
    }

    /**
     * The gain that puts a kit's kick and snare at [TARGET_LOUDNESS], held back if that would let one of its sounds
     * with the click pass [CEILING].
     */
    fun gain(samples: Map<Sound, FloatArray>, clickPeak: Float, sampleRate: Double): Float {
        val loudest = samples.values.maxOfOrNull { peak(it) } ?: 0f
        if (loudest <= 0f) {
            return 1f
        }
        val toTarget = 10.0.pow((TARGET_LOUDNESS - backboneLoudness(samples, sampleRate)) / 20).toFloat()
        return minOf(toTarget, (CEILING - clickPeak) / loudest)
    }

    /** The largest absolute sample. */
    fun peak(samples: FloatArray): Float = samples.fold(0f) { peak, sample -> maxOf(peak, abs(sample)) }
}

/**
 * ITU-R BS.1770's K-weighting: a +4 dB high shelf above ~1.7 kHz, then a high-pass at ~38 Hz. Coefficients for any
 * sample rate, from the standard's analogue prototypes.
 */
private class KWeighting(sampleRate: Double) {
    private val shelf: Section
    private val highpass: Section

    init {
        val shelfGain = 3.99984385397
        val shelfQ = 0.7071752369554193
        val shelfFrequency = 1681.9744509555319
        val a = 10.0.pow(shelfGain / 40)
        var w = 2 * PI * shelfFrequency / sampleRate
        var alpha = sin(w) / (2 * shelfQ)
        val rootA = 2 * sqrt(a) * alpha
        shelf = Section(
            b0 = a * ((a + 1) + (a - 1) * cos(w) + rootA),
            b1 = -2 * a * ((a - 1) + (a + 1) * cos(w)),
            b2 = a * ((a + 1) + (a - 1) * cos(w) - rootA),
            a0 = (a + 1) - (a - 1) * cos(w) + rootA,
            a1 = 2 * ((a - 1) - (a + 1) * cos(w)),
            a2 = (a + 1) - (a - 1) * cos(w) - rootA,
        )
        val highpassQ = 0.5003270373253953
        val highpassFrequency = 38.13547087613982
        w = 2 * PI * highpassFrequency / sampleRate
        alpha = sin(w) / (2 * highpassQ)
        highpass = Section(
            b0 = (1 + cos(w)) / 2,
            b1 = -(1 + cos(w)),
            b2 = (1 + cos(w)) / 2,
            a0 = 1 + alpha,
            a1 = -2 * cos(w),
            a2 = 1 - alpha,
        )
    }

    fun process(x: Double): Double = highpass.process(shelf.process(x))

    /** A biquad from raw coefficients, normalised by `a0`. */
    private class Section(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        private val b0 = b0 / a0
        private val b1 = b1 / a0
        private val b2 = b2 / a0
        private val a1 = a1 / a0
        private val a2 = a2 / a0
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }
}
