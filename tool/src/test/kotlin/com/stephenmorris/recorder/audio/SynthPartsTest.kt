package com.stephenmorris.recorder.audio

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthPartsTest {
    private val envelope = Envelope(peak = 0.5, attack = 0.002, decay = 0.1)

    @Test
    fun startsSilent() = assertEquals(Envelope.FLOOR, envelope.gain(0.0))

    @Test
    fun reachesItsPeakAfterTheAttack() = assertTrue(abs(envelope.gain(0.002) - 0.5) < 1e-9)

    // Halfway through the decay: the geometric mean of the peak and the floor.
    @Test
    fun decaysExponentially() = assertTrue(abs(envelope.gain(0.052) - sqrt(0.5 * Envelope.FLOOR)) < 1e-9)

    @Test
    fun endsAtTheFloor() {
        assertTrue(abs(envelope.gain(0.102) - Envelope.FLOOR) < 1e-12)
        assertEquals(Envelope.FLOOR, envelope.gain(5.0))
    }

    // Floored at 0.0002: an exponential ramp can't reach zero.
    @Test
    fun keepsATinyPeak() = assertEquals(0.0002, Envelope(peak = 0.0, attack = 0.002, decay = 0.1).peak)

    private val sampleRate = 44_100.0

    private fun samples(oscillator: Oscillator, count: Int) = DoubleArray(count) { oscillator.next() }

    private fun upwardCrossings(samples: DoubleArray) = (1 until samples.size).count { samples[it - 1] < 0 && samples[it] >= 0 }

    @Test
    fun sineStartsAtZeroAndRuns() {
        val output = samples(Oscillator(Waveform.SINE, 441.0, sampleRate = sampleRate), 100)
        assertEquals(0.0, output[0])
        assertTrue(abs(output[25] - 1) < 1e-9)
    }

    @Test
    fun eachShapeRunsAtItsFrequency() {
        for (waveform in Waveform.entries) {
            val output = samples(Oscillator(waveform, 441.0, sampleRate = sampleRate), 44_100)
            assertTrue(abs(upwardCrossings(output) - 441) <= 1, waveform.name)
        }
    }

    @Test
    fun shapesSwingFullScale() {
        for (waveform in listOf(Waveform.TRIANGLE, Waveform.SQUARE)) {
            val peak = samples(Oscillator(waveform, 441.0, sampleRate = sampleRate), 4_410).maxOf { abs(it) }
            assertTrue(peak > 0.95, waveform.name)
            assertTrue(peak < 1.15, waveform.name)
        }
    }

    @Test
    fun glidesThenHolds() {
        val output = samples(Oscillator(Waveform.SINE, 400.0, glideTo = 100.0, glide = 0.5, sampleRate = sampleRate), 88_200)
        assertTrue(upwardCrossings(output.copyOfRange(0, 22_050)) > 75)
        assertTrue(abs(upwardCrossings(output.copyOfRange(44_100, 88_200)) - 100) <= 1)
    }

    // A glide target of 0 means no sweep.
    @Test
    fun zeroTargetMeansNoGlide() {
        val output = samples(Oscillator(Waveform.SINE, 441.0, glideTo = 0.0, glide = 0.1, sampleRate = sampleRate), 44_100)
        assertTrue(abs(upwardCrossings(output) - 441) <= 1)
    }

    @Test
    fun repeatsFromTheSameSeed() {
        val first = SeededNoise()
        val second = SeededNoise()
        assertEquals(List(100) { first.next() }, List(100) { second.next() })
    }

    @Test
    fun staysInRangeAndCentred() {
        val noise = SeededNoise()
        val samples = DoubleArray(44_100) { noise.next() }
        assertTrue(samples.all { it >= -1 && it < 1 })
        assertTrue(abs(samples.average()) < 0.02)
    }

    // Fixed reference values, so the kit sounds the same every time.
    @Test
    fun noiseMatchesReferenceValues() {
        val noise = SeededNoise()
        assertEquals(-0.004511827641800359, noise.next())
        assertEquals(0.7847015294773432, noise.next())
        assertEquals(-0.5852267437863317, noise.next())
    }
}
