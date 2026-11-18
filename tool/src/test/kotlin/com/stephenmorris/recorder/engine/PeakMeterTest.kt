package com.stephenmorris.recorder.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeakMeterTest {
    private val meter = PeakMeter()
    private val second = 1_000_000_000L

    @Test
    fun jumpsToAPeak() {
        assertEquals(1.0, meter.level(1f, 0))
    }

    @Test
    fun fallsThirtyDecibelsASecond() {
        meter.level(1f, 0)
        assertTrue(abs(meter.level(0f, second / 2) - 0.75) < 1e-9)
    }

    @Test
    fun silenceIsZero() {
        assertEquals(0.0, meter.level(0f, 0))
    }

    @Test
    fun aLongGapFallsAtMostOneSecondsWorth() {
        meter.level(1f, 0)
        assertTrue(abs(meter.level(0f, 10 * second) - 0.5) < 1e-9)
    }
}
