package com.stephenmorris.recorder.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressorTest {
    private val sampleRate = 44_100.0

    private fun process(input: FloatArray): FloatArray =
        input.copyOf().also { Compressor(sampleRate).process(it, it.size) }

    private fun sine(amplitude: Float, seconds: Double) =
        FloatArray((seconds * sampleRate).toInt()) { amplitude * sin(2 * PI * 441 * it / sampleRate).toFloat() }

    @Test
    fun leavesLevelsBelowTheThresholdAlone() {
        assertEquals(0.0, Compressor.gainChange(-40.0))
        assertEquals(0.0, Compressor.gainChange(-12.0))
    }

    // The knee eases in over 30 dB above the threshold: at 0 dBFS (12 dB over) it's turned down 1.8 dB.
    @Test
    fun easesInThroughTheKnee() = assertTrue(abs(Compressor.gainChange(0.0) - -1.8) < 1e-9)

    // Past the knee, every extra dB in gives a quarter of a dB out.
    @Test
    fun reachesTheFullRatioPastTheKnee() =
        assertTrue(abs(Compressor.gainChange(28.0) - Compressor.gainChange(24.0) - -3) < 1e-9)

    @Test
    fun passesAQuietSignalUnchanged() {
        val input = sine(0.1f, 0.5)
        assertContentEquals(input, process(input))
    }

    @Test
    fun turnsAFullScaleSignalDown() {
        val output = process(sine(1f, 0.5))
        val settled = output.copyOfRange(output.size - 4_410, output.size).maxOf { abs(it) }
        assertTrue(abs(settled - 0.813f) < 0.02f)
    }

    // Silence after a loud passage stays finite and settles back to zero.
    @Test
    fun recoversToSilence() {
        val output = process(sine(1f, 0.2) + FloatArray(441_000))
        assertTrue(output.all { it.isFinite() })
        assertTrue(output.copyOfRange(output.size - 1_000, output.size).all { it == 0f })
    }
}
