package com.stephenmorris.recorder.audio

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * An envelope: from silence (0.0001) up to [peak] over [attack], then down to 0.0001 over [decay], both as
 * exponential ramps, as Web Audio's `exponentialRampToValueAtTime`.
 */
class Envelope(peak: Double, val attack: Double, val decay: Double) {
    val peak: Double = maxOf(0.0002, peak)

    /** The gain [time] seconds after the start. */
    fun gain(time: Double): Double = when {
        time <= 0 -> FLOOR
        time < attack -> FLOOR * (peak / FLOOR).pow(time / attack)
        time < attack + decay -> peak * (FLOOR / peak).pow((time - attack) / decay)
        else -> FLOOR
    }

    companion object {
        const val FLOOR = 0.0001
    }
}

/** A Web Audio oscillator shape. Square and triangle are band-limited, as Web Audio's are, so high notes don't alias. */
enum class Waveform { SINE, TRIANGLE, SQUARE }

/**
 * An oscillator whose frequency may glide exponentially from [frequency] to [glideTo] over [glide] seconds, then hold.
 * Starts at phase 0, as Web Audio's do. [glideTo] 0 means no glide.
 */
class Oscillator(
    private val waveform: Waveform,
    private val frequency: Double,
    glideTo: Double = 0.0,
    private val glide: Double = 0.0,
    private val sampleRate: Double,
) {
    private val end: Double = if (glideTo > 0 && glide > 0) glideTo else frequency

    /** Odd harmonics a triangle can hold below Nyquist at its highest frequency. */
    private val triangleHarmonics: Int = maxOf(1, ((sampleRate / 2 / maxOf(frequency, end) + 1) / 2).toInt())

    private var phase = 0.0
    private var frame = 0

    /** The next sample, from -1 to 1. */
    fun next(): Double {
        val time = frame / sampleRate
        val current = if (time < glide) frequency * (end / frequency).pow(time / glide) else end
        val step = current / sampleRate
        val value = when (waveform) {
            Waveform.SINE -> sin(2 * PI * phase)
            Waveform.TRIANGLE -> triangle()
            Waveform.SQUARE -> square(step)
        }
        phase += step
        phase -= floor(phase)
        frame += 1
        return value
    }

    /** The Fourier series Web Audio uses for "triangle": 8/pi^2 sum (-1)^k sin((2k+1)x) / (2k+1)^2. */
    private fun triangle(): Double {
        var sum = 0.0
        for (k in 0 until triangleHarmonics) {
            val n = (2 * k + 1).toDouble()
            val sign = if (k % 2 == 0) 1.0 else -1.0
            sum += sign * sin(2 * PI * n * phase) / (n * n)
        }
        return sum * 8 / (PI * PI)
    }

    /** A +-1 square with PolyBLEP corrections at its two edges. */
    private fun square(step: Double): Double {
        var value = if (phase < 0.5) 1.0 else -1.0
        value += polyBlep(phase, step)
        value -= polyBlep((phase + 0.5) % 1.0, step)
        return value
    }

    private fun polyBlep(phase: Double, step: Double): Double {
        if (phase < step) {
            val t = phase / step
            return t + t - t * t - 1
        }
        if (phase > 1 - step) {
            val t = (phase - 1) / step
            return t * t + t + t + 1
        }
        return 0.0
    }
}

/** White noise from a fixed seed (xorshift64), so every electronic hit sounds the same (spec §4). */
class SeededNoise(seed: Long = 0x2545F4914F6CDD1DL) {
    private var state: Long = if (seed == 0L) 1L else seed

    /** The next sample, from -1 to 1. */
    fun next(): Double {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        return (state ushr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
    }
}
