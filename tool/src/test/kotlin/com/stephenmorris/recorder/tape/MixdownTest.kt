package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.KitMix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Export's mixdown: every track's stem at its volume and pan, in stereo, through the limiters, without the silence
 * after the last sound.
 */
class MixdownTest {
    private val sampleRate = 48_000.0

    private fun stem(vararg samples: Float, left: Float, right: Float) = Mixdown.Stem(samples, Gains(left, right))

    @Test
    fun sumsEachStemAtItsGainsInEachChannel() {
        val (left, right) = Mixdown.mix(
            listOf(stem(0.5f, 0.5f, left = 1f, right = 0f), stem(0.2f, 0.2f, left = 0.5f, right = 1f)),
            sampleRate,
        )

        assertEquals(2, left.size)
        assertEquals(2, right.size)
        assertTrue(abs(left[0] - 0.6f) < 1e-6f)
        assertTrue(abs(right[0] - 0.2f) < 1e-6f)
    }

    @Test
    fun limitsOverFullScaleAsPlaybackDoes() {
        val loud = FloatArray(100) { 0.9f }
        val (left, right) = Mixdown.mix(
            listOf(Mixdown.Stem(loud, Gains(1f, 1f)), Mixdown.Stem(loud, Gains(1f, 1f))),
            sampleRate,
        )

        assertTrue(left.all { abs(it) <= KitMix.CEILING })
        assertTrue(right.all { abs(it) <= KitMix.CEILING })
    }

    @Test
    fun trimsTheSilenceAfterTheLastSoundInEitherChannel() {
        val (left, right) = Mixdown.mix(
            listOf(stem(0.5f, 0f, 0f, 0f, left = 1f, right = 0f), stem(0f, 0f, 0.5f, 0f, left = 0f, right = 1f)),
            sampleRate,
        )

        assertEquals(3, left.size)
        assertEquals(3, right.size)
        assertEquals(0.5f, right[2])
    }

    @Test
    fun stemsOfDifferentLengthsRunToTheLongest() {
        val (left, _) = Mixdown.mix(
            listOf(stem(0.1f, left = 1f, right = 1f), stem(0f, 0f, 0.3f, left = 1f, right = 1f)),
            sampleRate,
        )

        assertEquals(3, left.size)
        assertTrue(abs(left[2] - 0.3f) < 1e-6f)
    }

    @Test
    fun mixingInSmallBlocksMatchesMixingAtOnce() {
        val stems = listOf(
            Mixdown.Stem(FloatArray(9) { it / 10f }, Gains(1f, 0.5f)),
            Mixdown.Stem(floatArrayOf(0.3f, -0.2f, 0.1f, 0.4f), Gains(0.25f, 1f)),
        )
        val whole = Mixdown.mix(stems, sampleRate)
        val blocks = Mixdown.mix(stems, sampleRate, block = 2)

        assertContentEquals(whole.first, blocks.first)
        assertContentEquals(whole.second, blocks.second)
        assertEquals(9, whole.first.size)
    }

    @Test
    fun nothingToHearIsEmpty() {
        val (left, right) = Mixdown.mix(listOf(stem(0f, 0f, 0f, left = 1f, right = 1f)), sampleRate)
        assertTrue(left.isEmpty() && right.isEmpty())
        assertTrue(Mixdown.mix(emptyList(), sampleRate).first.isEmpty())
    }

    // New: render writes no more than it's asked for, and reports the end.
    @Test
    fun renderStopsAtTheEnd() {
        val mixdown = Mixdown(listOf(stem(0.1f, 0.2f, 0.3f, left = 1f, right = 1f)), sampleRate)
        val left = FloatArray(8)
        val right = FloatArray(8)

        assertEquals(3, mixdown.frameCount)
        assertEquals(2, mixdown.render(left, right, 2))
        assertEquals(1, mixdown.render(left, right, 8))
        assertTrue(mixdown.isDone)
        assertEquals(0, mixdown.render(left, right, 8))
    }
}
