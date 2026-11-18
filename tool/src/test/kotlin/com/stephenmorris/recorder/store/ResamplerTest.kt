package com.stephenmorris.recorder.store

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class ResamplerTest {
    @Test
    fun sameRateIsUnchanged() {
        val samples = floatArrayOf(1f, 2f)
        assertSame(samples, Resampler.linear(samples, 48_000.0, 48_000.0))
    }

    @Test
    fun halvingTheRateKeepsEverySecondSample() {
        assertContentEquals(floatArrayOf(0f, 2f), Resampler.linear(floatArrayOf(0f, 1f, 2f, 3f), 2.0, 1.0))
    }

    @Test
    fun doublingInterpolates() {
        assertContentEquals(floatArrayOf(0f, 0.5f, 1f, 1f), Resampler.linear(floatArrayOf(0f, 1f), 1.0, 2.0))
    }

    @Test
    fun aBadRateIsUnchanged() {
        val samples = floatArrayOf(1f, 2f)
        assertSame(samples, Resampler.linear(samples, 0.0, 48_000.0))
        assertSame(samples, Resampler.linear(samples, 48_000.0, Double.NaN))
    }
}
