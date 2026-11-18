package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepGridReshapeTest {
    private fun grid(sound: Sound, vararg hits: Pair<Int, Level>): StepGrid =
        hits.fold(StepGrid.EMPTY) { grid, (step, level) -> grid.with(sound, step, level) }

    @Test
    fun sameMeterIsUnchanged() {
        val grid = StepGrid.EMPTY
            .with(Sound.HAT, 0, Level.ACCENT)
            .with(Sound.HAT, 2, Level.HIT)
            .with(Sound.SNARE, 20, Level.ACCENT)
        assertEquals(grid, grid.remapped(MeterId.FOUR_FOUR, MeterId.FOUR_FOUR, 2))
    }

    // Each hit keeps its bar and beat; offsets scale by round(o x nb / na).
    @Test
    fun fourFourToTwelveEight() {
        val grid = grid(Sound.HAT, *(0 until 16).map { it to Level.HIT }.toTypedArray())
        val remapped = grid.remapped(MeterId.FOUR_FOUR, MeterId.TWELVE_EIGHT, 1)
        // Offsets 0,1,2,3 -> round(0, 0.75, 1.5, 2.25) = 0,1,2,2 in each 3-step beat.
        assertEquals((0 until 12).toList(), remapped.hits(Sound.HAT).keys.sorted())
    }

    // 7/8 has three beats: beat 4 of 4/4 has nowhere to go. Offset 3 of a 4-step beat rounds to 2 (= round(1.5)),
    // which is past the end of a 2-step beat, so it's dropped rather than spilling into the next beat.
    @Test
    fun fourFourToSevenEightDropsWhatDoesntFit() {
        val grid = grid(Sound.KICK, 0 to Level.ACCENT, 3 to Level.HIT, 9 to Level.HIT, 12 to Level.ACCENT)
        val remapped = grid.remapped(MeterId.FOUR_FOUR, MeterId.SEVEN_EIGHT, 1)
        assertEquals(mapOf(0 to Level.ACCENT, 5 to Level.HIT), remapped.hits(Sound.KICK))
    }

    @Test
    fun collisionsKeepTheLouderLevel() {
        val grid = grid(Sound.HAT, 2 to Level.ACCENT, 3 to Level.HIT, 6 to Level.HIT, 7 to Level.ACCENT)
        val remapped = grid.remapped(MeterId.FOUR_FOUR, MeterId.TWELVE_EIGHT, 1)
        assertEquals(mapOf(2 to Level.ACCENT, 5 to Level.ACCENT), remapped.hits(Sound.HAT))
    }

    @Test
    fun remapsEveryBar() {
        val grid = grid(Sound.SNARE, 4 to Level.ACCENT, 20 to Level.ACCENT)
        val remapped = grid.remapped(MeterId.FOUR_FOUR, MeterId.THREE_FOUR, 2)
        assertEquals(mapOf(4 to Level.ACCENT, 16 to Level.ACCENT), remapped.hits(Sound.SNARE))
    }

    @Test
    fun remapDropsHitsOutsideThePattern() {
        val grid = grid(Sound.KICK, -3 to Level.HIT, 16 to Level.HIT)
        assertTrue(grid.remapped(MeterId.FOUR_FOUR, MeterId.FIVE_FOUR, 1).isEmpty)
    }

    @Test
    fun growingRepeatsBarsInOrder() {
        val grid = grid(Sound.KICK, 0 to Level.ACCENT, 20 to Level.HIT, 40 to Level.HIT)
        val resized = grid.resized(16, 3, 8)
        // Bars 4 and 5 copy bars 1 and 2; then 6, 7, 8 copy 3, 1, 2.
        assertEquals(listOf(0, 20, 40, 48, 68, 88, 96, 116), resized.hits(Sound.KICK).keys.sorted())
        assertEquals(Level.ACCENT, resized[Sound.KICK, 48])
    }

    @Test
    fun shrinkingDropsBarsFromTheEnd() {
        val grid = grid(Sound.SNARE, 4 to Level.ACCENT, 20 to Level.HIT, 52 to Level.HIT)
        assertEquals(mapOf(4 to Level.ACCENT), grid.resized(16, 4, 1).hits(Sound.SNARE))
    }

    @Test
    fun resizeIgnoresHitsAlreadyOutsideThePattern() {
        val grid = grid(Sound.TOM, 16 to Level.HIT, -1 to Level.HIT)
        assertTrue(grid.resized(16, 1, 2).isEmpty)
    }

    // Corrupt bar counts and lengths must never break building a range.
    private val small = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT).with(Sound.SNARE, 4, Level.HIT)

    @Test
    fun remapClampsNegativeBars() = assertEquals(small, small.remapped(MeterId.FOUR_FOUR, MeterId.FOUR_FOUR, -1))

    @Test
    fun remapClampsZeroBars() = assertEquals(small, small.remapped(MeterId.FOUR_FOUR, MeterId.FOUR_FOUR, 0))

    @Test
    fun resizeClampsNegativeBars() = assertEquals(small, small.resized(16, -1, -5))

    @Test
    fun trimToANegativeLengthEmpties() = assertTrue(small.trimmed(-1).isEmpty)
}
