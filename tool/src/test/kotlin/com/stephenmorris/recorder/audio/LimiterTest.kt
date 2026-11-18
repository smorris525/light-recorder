package com.stephenmorris.recorder.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class LimiterTest {
    private val sampleRate = 44_100.0

    private fun process(input: FloatArray, limiter: Limiter = Limiter(sampleRate = sampleRate)): FloatArray =
        input.copyOf().also { limiter.process(it, it.size) }

    @Test
    fun passesASignalUnderTheCeilingUnchanged() {
        val input = floatArrayOf(0f, 0.5f, -0.9f, 0.99f, -0.99f, 0.3f)
        assertContentEquals(input, process(input))
    }

    @Test
    fun neverPassesTheCeiling() {
        val input = FloatArray(4_410) { if (it % 3 == 0) 2.5f else -1.7f }
        assertTrue(process(input).all { abs(it) <= KitMix.CEILING })
    }

    // An over is turned down to the ceiling at once, not after an attack.
    @Test
    fun catchesTheFirstSampleOver() = assertContentEquals(floatArrayOf(0.5f, KitMix.CEILING), process(floatArrayOf(0.5f, 1.98f)))

    // After an over, the samples that follow stay turned down a while, so the limiter doesn't distort the waveform.
    @Test
    fun holdsTheGainDownAfterAnOver() {
        val output = process(floatArrayOf(1.98f, 0.5f, 0.5f))
        assertTrue(output[1] < 0.5f)
        assertTrue(output[1] > 0.25f)
    }

    // Back to within 1% of unity five release times after the over.
    @Test
    fun recoversOverItsRelease() {
        val limiter = Limiter(sampleRate = sampleRate)
        process(floatArrayOf(4f), limiter)
        val quiet = process(FloatArray((5 * Limiter.RELEASE * sampleRate).toInt()) { 0.5f }, limiter)
        assertTrue(quiet.last() > 0.495f)
        assertTrue(quiet.last() <= 0.5f)
    }
}
