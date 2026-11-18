package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepGridJsonTest {
    @Test
    fun roundTripsThroughStoredData() {
        val grid = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT).with(Sound.OPEN_HAT, 13, Level.HIT)
        assertEquals(grid, StepGrid.fromJson(grid.toJson()))
    }

    @Test
    fun emptyDataReadsAsAnEmptyGrid() {
        assertEquals(StepGrid.EMPTY, StepGrid.fromJson(""))
    }

    @Test
    fun unreadableDataReadsAsAnEmptyGrid() {
        assertEquals(StepGrid.EMPTY, StepGrid.fromJson("not json"))
        assertEquals(StepGrid.EMPTY, StepGrid.fromJson("{}"))
        assertEquals(StepGrid.EMPTY, StepGrid.fromJson("[1, 2]"))
        assertEquals(StepGrid.EMPTY, StepGrid.fromJson("""{"hitsBySound":7}"""))
    }

    @Test
    fun equalGridsStoreEqualBytes() {
        val first = StepGrid.EMPTY
            .with(Sound.KICK, 0, Level.HIT)
            .with(Sound.SNARE, 4, Level.ACCENT)
            .with(Sound.HAT, 2, Level.HIT)
        val second = StepGrid.EMPTY
            .with(Sound.HAT, 2, Level.HIT)
            .with(Sound.SNARE, 4, Level.ACCENT)
            .with(Sound.KICK, 0, Level.HIT)
        assertEquals(first.toJson(), second.toJson())
    }

    // The stored shape, with keys sorted.
    @Test
    fun keepsTheStoredShape() {
        assertEquals(
            """{"hitsBySound":{"kick":{"0":2}}}""",
            StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT).toJson(),
        )
    }

    @Test
    fun badKeysAndLevelsAreForgiven() {
        val grid = StepGrid.fromJson("""{"hitsBySound":{"kick":{"x":2,"3":7,"4":2}}}""")
        assertEquals(mapOf(3 to Level.HIT, 4 to Level.ACCENT), grid.hits(Sound.KICK))
    }

    @Test
    fun unknownSoundsSurviveASave() {
        assertTrue(StepGrid.fromJson("""{"hitsBySound":{"cowbell":{"1":1}}}""").toJson().contains("\"cowbell\""))
    }
}
