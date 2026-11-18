package com.stephenmorris.recorder.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwingTest {
    @Test
    fun rawValuesAreStable() {
        assertEquals(
            listOf("off", "eighthLight", "eighthHeavy", "sixteenthLight", "sixteenthHeavy"),
            Swing.entries.map { it.raw },
        )
    }

    @Test
    fun unknownSwingDecodesAsOff() {
        assertEquals(
            listOf(Swing.EIGHTH_HEAVY, Swing.OFF, Swing.OFF),
            listOf("eighthHeavy", "triplet", null).map { Swing.fromRaw(it) },
        )
    }

    @Test
    fun optionNames() {
        assertEquals(
            listOf("Off", "1/8 light", "1/8 heavy", "1/16 light", "1/16 heavy"),
            Swing.entries.map { it.optionName },
        )
    }

    @Test
    fun offIsStraight() {
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), (0 until 4).map { Swing.OFF.delay(it) })
    }

    // r = 0.58: positions [0, 0.29, 0.58, 0.79] against straight [0, 0.25, 0.5, 0.75], x 4.
    @Test
    fun eighthLight() = expectDelays(Swing.EIGHTH_LIGHT, listOf(0.0, 0.16, 0.32, 0.16))

    // r = 0.66: positions [0, 0.33, 0.66, 0.83].
    @Test
    fun eighthHeavy() = expectDelays(Swing.EIGHTH_HEAVY, listOf(0.0, 0.32, 0.64, 0.32))

    // 1/16 swing keeps the off-beat eighth straight: positions [0, r/2, 0.5, 0.5 + r/2].
    @Test
    fun sixteenthLight() = expectDelays(Swing.SIXTEENTH_LIGHT, listOf(0.0, 0.16, 0.0, 0.16))

    @Test
    fun sixteenthHeavy() = expectDelays(Swing.SIXTEENTH_HEAVY, listOf(0.0, 0.32, 0.0, 0.32))

    @Test
    fun stepsOutsideAFourStepBeatAreStraight() {
        assertEquals(0.0, Swing.EIGHTH_HEAVY.delay(4))
        assertEquals(0.0, Swing.EIGHTH_HEAVY.delay(-1))
    }

    @Test
    fun layoutAppliesSwingInFourFour() {
        // Step 6 is the third step of beat 2.
        assertTrue(abs(MeterId.FOUR_FOUR.layout.swingDelay(Swing.EIGHTH_HEAVY, 6) - 0.64) < 1e-9)
    }

    // A stored swing survives a switch to a meter that can't swing; it just doesn't play.
    @Test
    fun layoutIgnoresSwingInEighthMeters() {
        for (meter in listOf(MeterId.SIX_EIGHT, MeterId.SEVEN_EIGHT, MeterId.TWELVE_EIGHT)) {
            val layout = meter.layout
            assertTrue((0 until layout.stepsPerBar).all { layout.swingDelay(Swing.SIXTEENTH_HEAVY, it) == 0.0 })
        }
    }

    private fun expectDelays(swing: Swing, expected: List<Double>) {
        expected.forEachIndexed { step, value ->
            assertTrue(abs(swing.delay(step) - value) < 1e-9, "step $step")
        }
    }
}
