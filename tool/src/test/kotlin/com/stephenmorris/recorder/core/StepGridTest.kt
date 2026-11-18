package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepGridTest {
    @Test
    fun startsEmpty() {
        assertTrue(StepGrid.EMPTY.isEmpty)
        assertNull(StepGrid.EMPTY[Sound.KICK, 0])
    }

    @Test
    fun setsAndClearsLevels() {
        val grid = StepGrid.EMPTY.with(Sound.SNARE, 4, Level.ACCENT).with(Sound.SNARE, 12, Level.HIT)
        assertEquals(mapOf(4 to Level.ACCENT, 12 to Level.HIT), grid.hits(Sound.SNARE))
        val cleared = grid.with(Sound.SNARE, 4, null).with(Sound.SNARE, 12, null)
        assertTrue(cleared.isEmpty)
        assertEquals(StepGrid.EMPTY, cleared)
    }

    @Test
    fun tappingTogglesRestAndHit() {
        val first = StepGrid.EMPTY.toggled(Sound.HAT, 2)
        val second = first.toggled(Sound.HAT, 2)
        assertEquals(Level.HIT, first[Sound.HAT, 2])
        assertNull(second[Sound.HAT, 2])
        assertEquals(StepGrid.EMPTY, second)
    }

    @Test
    fun tappingAnAccentTurnsItOff() {
        val grid = StepGrid.EMPTY.with(Sound.HAT, 2, Level.ACCENT).toggled(Sound.HAT, 2)
        assertNull(grid[Sound.HAT, 2])
        assertTrue(grid.isEmpty)
    }

    // No hi-hat exclusivity: both may share a step (the hat's choke decides what's heard).
    @Test
    fun hatAndOpenHatCanShareAStep() {
        val grid = StepGrid.EMPTY.toggled(Sound.HAT, 0).toggled(Sound.OPEN_HAT, 0)
        assertEquals(Level.HIT, grid[Sound.HAT, 0])
        assertEquals(Level.HIT, grid[Sound.OPEN_HAT, 0])
    }

    @Test
    fun recordingAddsAHitOnARest() {
        val grid = StepGrid.EMPTY.recordingHit(Sound.KICK, 8)
        assertEquals(Level.HIT, grid?.get(Sound.KICK, 8))
    }

    // Overdub only adds: an existing hit or accent is left alone.
    @Test
    fun recordingLeavesExistingStepsAlone() {
        val grid = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT).with(Sound.KICK, 4, Level.HIT)
        assertNull(grid.recordingHit(Sound.KICK, 0))
        assertNull(grid.recordingHit(Sound.KICK, 4))
    }

    @Test
    fun recordingAnAccentUpgradesAHit() {
        val grid = StepGrid.EMPTY.with(Sound.KICK, 4, Level.HIT)
        assertEquals(Level.ACCENT, grid.recordingHit(Sound.KICK, 4, Level.ACCENT)?.get(Sound.KICK, 4))
    }

    @Test
    fun trimDropsStepsOutsideThePattern() {
        val grid = StepGrid.EMPTY
            .with(Sound.KICK, -1, Level.HIT)
            .with(Sound.KICK, 0, Level.ACCENT)
            .with(Sound.KICK, 15, Level.HIT)
            .with(Sound.KICK, 16, Level.HIT)
            .with(Sound.RIDE, 20, Level.HIT)
        val trimmed = grid.trimmed(16)
        assertEquals(mapOf(0 to Level.ACCENT, 15 to Level.HIT), trimmed.hits(Sound.KICK))
        assertTrue(trimmed.hits(Sound.RIDE).isEmpty())
    }

    @Test
    fun roundTripsThroughJson() {
        val grid = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT).with(Sound.OPEN_HAT, 14, Level.HIT)
        assertEquals(grid, StepGrid.fromJson(grid.toJson()))
    }

    // Data from a newer version: an unknown sound survives a save; an unknown level reads as a hit.
    @Test
    fun decodesDataFromANewerVersion() {
        val grid = StepGrid.fromJson("""{"hitsBySound":{"kick":{"0":2,"4":9},"cowbell":{"2":1}}}""")
        assertEquals(mapOf(0 to Level.ACCENT, 4 to Level.HIT), grid.hits(Sound.KICK))
        val resaved = StepGrid.fromJson(grid.toJson())
        assertEquals(mapOf(2 to Level.HIT), resaved.hitsBySound["cowbell"])
    }

    // Empty maps from storage are dropped, so the grid compares equal to an empty one.
    @Test
    fun emptyMapsDecodeAsEmpty() {
        val grid = StepGrid.fromJson("""{"hitsBySound":{"hat":{}}}""")
        assertTrue(grid.isEmpty)
        assertEquals(StepGrid.EMPTY, grid)
    }

    // One malformed entry doesn't lose the pattern: bad step keys are dropped, bad levels read as hits.
    @Test
    fun malformedEntriesAreSkipped() {
        val grid = StepGrid.fromJson("""{"hitsBySound":{"kick":{"x":1,"0":"a","4":2},"snare":{"8":1}}}""")
        assertEquals(mapOf(0 to Level.HIT, 4 to Level.ACCENT), grid.hits(Sound.KICK))
        assertEquals(mapOf(8 to Level.HIT), grid.hits(Sound.SNARE))
    }
}
