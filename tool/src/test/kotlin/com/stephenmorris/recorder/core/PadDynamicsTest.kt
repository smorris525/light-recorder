package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PadDynamicsTest {
    @Test
    fun thresholds() {
        assertEquals(25f, PadDynamics.ACCENT_RADIUS_DP)
        assertEquals(0.5f, PadDynamics.ACCENT_PRESSURE)
    }

    @Test
    fun freshProbeRecordsHits() = assertEquals(Level.HIT, PadTouchProbe().level(80f, 0.9f))

    @Test
    fun constantPressureIsIgnored() {
        val probe = PadTouchProbe()
        repeat(5) { probe.observe(10f, 1f) }
        assertEquals(Level.HIT, probe.level(10f, 1f))
    }

    @Test
    fun varyingPressureDecides() {
        val probe = PadTouchProbe().apply {
            observe(10f, 0.3f)
            observe(10f, 0.7f)
        }
        assertEquals(Level.ACCENT, probe.level(10f, 0.6f))
        assertEquals(Level.HIT, probe.level(10f, 0.4f))
    }

    @Test
    fun varyingSizeDecidesWithoutPressure() {
        val probe = PadTouchProbe().apply {
            observe(30f, 1f)
            observe(60f, 1f)
        }
        assertEquals(Level.ACCENT, probe.level(52f, 1f))
        assertEquals(Level.HIT, probe.level(48f, 1f))
    }

    // Pressures of exactly 0 or 1 are what screens without sensing report; they never count as real.
    @Test
    fun pressureAtItsLimitsIsNotEvidence() {
        val probe = PadTouchProbe().apply {
            observe(10f, 0f)
            observe(10f, 1f)
        }
        assertEquals(Level.HIT, probe.level(10f, 0.9f))
    }

    // A size of 0 means the screen doesn't report one.
    @Test
    fun zeroSizeIsNotEvidence() {
        val probe = PadTouchProbe().apply {
            observe(0f, 1f)
            observe(60f, 1f)
        }
        assertEquals(Level.HIT, probe.level(60f, 1f))
    }
}
