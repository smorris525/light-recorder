package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepPaintStrokeTest {
    /** Applies one cell, returning whether it changed the grid. */
    private class Painter(var grid: StepGrid, val stroke: StepPaintStroke) {
        fun apply(sound: Sound, step: Int): Boolean {
            val before = grid
            grid = stroke.apply(sound, step, grid)
            return grid !== before
        }
    }

    @Test
    fun startingOnARestEnablesCellsAndPreservesExistingAccents() {
        val grid = StepGrid.EMPTY.with(Sound.HAT, 1, Level.HIT).with(Sound.HAT, 2, Level.ACCENT)
        val painter = Painter(grid, StepPaintStroke(grid[Sound.HAT, 0]))
        assertTrue(painter.stroke.turnsOn)
        assertTrue(painter.apply(Sound.HAT, 0))
        assertFalse(painter.apply(Sound.HAT, 1))
        assertFalse(painter.apply(Sound.HAT, 2))
        assertTrue(painter.apply(Sound.HAT, 3))
        assertEquals(
            mapOf(0 to Level.HIT, 1 to Level.HIT, 2 to Level.ACCENT, 3 to Level.HIT),
            painter.grid.hits(Sound.HAT),
        )
        assertTrue(painter.stroke.hasChanges)
    }

    @Test
    fun startingOnAnEnabledCellErasesBothLevels() {
        for (first in Level.entries) {
            val grid = StepGrid.EMPTY
                .with(Sound.SNARE, 0, first)
                .with(Sound.SNARE, 2, Level.ACCENT)
                .with(Sound.KICK, 3, Level.HIT)
            val painter = Painter(grid, StepPaintStroke(first))
            assertFalse(painter.stroke.turnsOn)
            assertTrue(painter.apply(Sound.SNARE, 0))
            assertFalse(painter.apply(Sound.SNARE, 1))
            assertTrue(painter.apply(Sound.SNARE, 2))
            assertTrue(painter.apply(Sound.KICK, 3))
            assertTrue(painter.grid.isEmpty, first.name)
        }
    }

    @Test
    fun reversingDirectionDoesNotToggleVisitedCells() {
        val painter = Painter(StepGrid.EMPTY, StepPaintStroke(null))
        for (step in listOf(0, 1, 2, 1, 0)) {
            painter.apply(Sound.HAT, step)
        }
        assertEquals(mapOf(0 to Level.HIT, 1 to Level.HIT, 2 to Level.HIT), painter.grid.hits(Sound.HAT))
        assertTrue(painter.apply(Sound.SNARE, 0))
    }

    @Test
    fun aNewStrokeUsesItsOwnFirstCell() {
        val on = Painter(StepGrid.EMPTY, StepPaintStroke(null))
        on.apply(Sound.HAT, 0)
        val off = Painter(on.grid, StepPaintStroke(on.grid[Sound.HAT, 0]))
        assertFalse(off.stroke.hasChanges)
        off.apply(Sound.HAT, 0)
        assertTrue(off.grid.isEmpty)
    }
}
