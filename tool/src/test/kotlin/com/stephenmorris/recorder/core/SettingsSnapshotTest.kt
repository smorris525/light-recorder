package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSnapshotTest {
    /** Snare on beats 2 and 4 of four bars of 4/4, accents on bar 1. */
    private val backbeat: StepGrid = (0 until 4).fold(StepGrid.EMPTY) { grid, bar ->
        grid.with(Sound.SNARE, bar * 16 + 4, if (bar == 0) Level.ACCENT else Level.HIT)
            .with(Sound.SNARE, bar * 16 + 12, Level.HIT)
    }

    @Test
    fun unchangedChoicesGiveTheSameSteps() {
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 4, backbeat)
        assertEquals(backbeat, snapshot.steps(MeterId.FOUR_FOUR, 4))
    }

    // 4/4 -> 7/8 drops beat 4; going back within the same visit restores it.
    @Test
    fun meterRoundTripIsLossless() {
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 4, backbeat)
        assertEquals(4, snapshot.steps(MeterId.SEVEN_EIGHT, 4).hits(Sound.SNARE).size)
        assertEquals(backbeat, snapshot.steps(MeterId.FOUR_FOUR, 4))
    }

    @Test
    fun barRoundTripIsLossless() {
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 4, backbeat)
        assertEquals(listOf(4, 12, 20, 28), snapshot.steps(MeterId.FOUR_FOUR, 2).hits(Sound.SNARE).keys.sorted())
        assertEquals(backbeat, snapshot.steps(MeterId.FOUR_FOUR, 4))
    }

    // 2 -> 3 -> 4 gives the same as 2 -> 4: copies come from the bars there were on opening.
    @Test
    fun steppingUpGivesTheSameAsJumping() {
        val twoBars = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT).with(Sound.KICK, 20, Level.HIT)
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 2, twoBars)
        snapshot.steps(MeterId.FOUR_FOUR, 3)
        assertEquals(
            mapOf(0 to Level.ACCENT, 20 to Level.HIT, 32 to Level.ACCENT, 52 to Level.HIT),
            snapshot.steps(MeterId.FOUR_FOUR, 4).hits(Sound.KICK),
        )
    }

    @Test
    fun meterAndBarsTogether() {
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 4, backbeat)
        // 3/4, 1 bar: beat 2 (step 4) survives, beat 4 has nowhere to go.
        assertEquals(mapOf(4 to Level.ACCENT), snapshot.steps(MeterId.THREE_FOUR, 1).hits(Sound.SNARE))
    }

    // Hits beyond the pattern are trimmed on commit.
    @Test
    fun dropsHitsOutsideThePattern() {
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 4, backbeat.with(Sound.RIDE, 64, Level.HIT))
        assertEquals(backbeat, snapshot.steps(MeterId.FOUR_FOUR, 4))
    }

    @Test
    fun clampsCorruptBarCounts() {
        val snapshot = SettingsSnapshot(MeterId.FOUR_FOUR, 0, backbeat)
        assertEquals(1, snapshot.bars)
        assertEquals(16, snapshot.steps(MeterId.FOUR_FOUR, 99).hits(Sound.SNARE).size)
    }
}
