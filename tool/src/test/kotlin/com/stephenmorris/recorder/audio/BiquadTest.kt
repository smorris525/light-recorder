package com.stephenmorris.recorder.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class BiquadTest {
    private val sampleRate = 44_100.0

    /** Steady-state peak of a unit sine at [frequency] through the filter, after a second to settle. */
    private fun response(filter: Biquad, frequency: Double): Double {
        var peak = 0.0
        for (index in 0 until (sampleRate * 1.1).toInt()) {
            val output = filter.process(sin(2 * PI * frequency * index / sampleRate))
            if (index >= sampleRate.toInt()) {
                peak = maxOf(peak, abs(output))
            }
        }
        return peak
    }

    @Test
    fun lowpassPassesLowsAndCutsHighs() {
        assertTrue(abs(response(Biquad(Biquad.Kind.LOWPASS, 1_000.0, 0.7, sampleRate), 100.0) - 1) < 0.02)
        assertTrue(response(Biquad(Biquad.Kind.LOWPASS, 1_000.0, 0.7, sampleRate), 10_000.0) < 0.02)
    }

    @Test
    fun highpassCutsLowsAndPassesHighs() {
        assertTrue(response(Biquad(Biquad.Kind.HIGHPASS, 1_000.0, 0.7, sampleRate), 100.0) < 0.02)
        assertTrue(abs(response(Biquad(Biquad.Kind.HIGHPASS, 1_000.0, 0.7, sampleRate), 10_000.0) - 1) < 0.02)
    }

    // Web Audio's band-pass peaks at 0 dB on its centre frequency.
    @Test
    fun bandpassPeaksAtUnityOnItsCentre() {
        fun filter() = Biquad(Biquad.Kind.BANDPASS, 2_000.0, 2.0, sampleRate)
        assertTrue(abs(response(filter(), 2_000.0) - 1) < 0.02)
        assertTrue(response(filter(), 200.0) < 0.1)
        assertTrue(response(filter(), 15_000.0) < 0.15)
    }

    // Web Audio reads low-pass Q in dB: 0.7 dB is a gentle bump at the corner (about 1.08x).
    @Test
    fun lowpassQIsInDecibels() =
        assertTrue(abs(response(Biquad(Biquad.Kind.LOWPASS, 1_000.0, 0.7, sampleRate), 1_000.0) - 1.084) < 0.02)
}
