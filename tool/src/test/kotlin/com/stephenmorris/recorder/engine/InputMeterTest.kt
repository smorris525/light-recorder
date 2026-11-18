package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputMeterTest {
    private val meter = InputMeter()

    @Test
    fun inputLevelIsReported() {
        meter.record(FloatArray(256) { 0.25f }, 256)

        assertEquals(0.25f, meter.takePeak())
        assertEquals(0f, meter.takePeak())
        assertFalse(meter.takeClipped())
    }

    @Test
    fun inputPeakHoldsTheLoudestCycleUntilAsked() {
        meter.record(FloatArray(256) { -0.5f }, 256)
        meter.record(FloatArray(256) { 0.125f }, 256)

        assertEquals(0.5f, meter.takePeak())
    }

    @Test
    fun clippingIsReportedOnce() {
        meter.record(FloatArray(256) { 1f }, 256)

        assertTrue(meter.takeClipped())
        assertFalse(meter.takeClipped())
    }

    @Test
    fun onlyTheCountedSamplesAreMeasured() {
        val buffer = FloatArray(8) { 1f }
        buffer.fill(0.25f, 0, 4)
        meter.record(buffer, 4)

        assertEquals(0.25f, meter.takePeak())
        assertFalse(meter.takeClipped())
    }
}
