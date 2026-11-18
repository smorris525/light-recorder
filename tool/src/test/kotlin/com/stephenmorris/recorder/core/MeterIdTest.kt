package com.stephenmorris.recorder.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeterIdTest {
    @Test
    fun rawValuesAreTheSignatures() {
        assertEquals(listOf("3/4", "4/4", "5/4", "6/8", "7/8", "12/8"), MeterId.entries.map { it.raw })
    }

    @Test
    fun unknownMeterDecodesAsFourFour() {
        assertEquals(
            listOf(MeterId.SEVEN_EIGHT, MeterId.FOUR_FOUR, MeterId.FOUR_FOUR, MeterId.FOUR_FOUR),
            listOf("7/8", "9/8", "", null).map { MeterId.fromRaw(it) },
        )
    }

    @Test
    fun layouts() {
        val cases = listOf(
            Triple(MeterId.THREE_FOUR, listOf(4, 4, 4), 12 to true),
            Triple(MeterId.FOUR_FOUR, listOf(4, 4, 4, 4), 16 to true),
            Triple(MeterId.FIVE_FOUR, listOf(4, 4, 4, 4, 4), 20 to true),
            Triple(MeterId.SIX_EIGHT, listOf(3, 3), 6 to false),
            Triple(MeterId.SEVEN_EIGHT, listOf(2, 2, 3), 7 to false),
            Triple(MeterId.TWELVE_EIGHT, listOf(3, 3, 3, 3), 12 to false),
        )
        for ((meter, groups, rest) in cases) {
            val layout = meter.layout
            assertEquals(groups, layout.groups, meter.raw)
            assertEquals(rest.first, layout.stepsPerBar, meter.raw)
            assertEquals(rest.second, layout.swingAllowed, meter.raw)
        }
    }

    @Test
    fun beatStarts() {
        assertEquals(listOf(0, 4, 8, 12), MeterId.FOUR_FOUR.layout.starts)
        assertEquals(listOf(0, 2, 4), MeterId.SEVEN_EIGHT.layout.starts)
        assertEquals(listOf(0, 3), MeterId.SIX_EIGHT.layout.starts)
    }

    // BPM counts quarters in x/4, dotted quarters in 6/8 and 12/8, and eighths in 7/8.
    @Test
    fun stepDurations() {
        assertEquals(0.25, MeterId.FOUR_FOUR.layout.stepDuration(60))
        assertTrue(abs(MeterId.SIX_EIGHT.layout.stepDuration(60) - 1.0 / 3) < 1e-12)
        assertEquals(0.5, MeterId.SEVEN_EIGHT.layout.stepDuration(120))
    }

    @Test
    fun positionsWithinTheBar() {
        val layout = MeterId.SEVEN_EIGHT.layout
        assertEquals(BeatPosition(0, 0), layout.position(0))
        assertEquals(BeatPosition(1, 1), layout.position(3))
        assertEquals(BeatPosition(2, 2), layout.position(6))
    }

    @Test
    fun clampsBars() {
        assertEquals(1, MeterLayout.clampedBars(0))
        assertEquals(5, MeterLayout.clampedBars(5))
        assertEquals(8, MeterLayout.clampedBars(99))
    }

    // A corrupt BPM must not give an infinite or negative step (the scheduler would stall or spin).
    @Test
    fun stepDurationClampsTheTempo() {
        val layout = MeterId.FOUR_FOUR.layout
        assertEquals(layout.stepDuration(30), layout.stepDuration(0))
        assertEquals(layout.stepDuration(30), layout.stepDuration(-60))
        assertEquals(layout.stepDuration(300), layout.stepDuration(9_000))
    }

    @Test
    fun tempoClamps() {
        assertEquals(30, Tempo.clamped(0))
        assertEquals(120, Tempo.clamped(120))
        assertEquals(300, Tempo.clamped(301))
    }
}
