package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeatClicksTest {
    @Test
    fun fourFourClicksEachQuarter() {
        assertEquals(
            listOf(
                BeatClick(0, ClickKind.ACCENT, 1),
                BeatClick(4, ClickKind.BEAT, 2),
                BeatClick(8, ClickKind.BEAT, 3),
                BeatClick(12, ClickKind.BEAT, 4),
            ),
            MeterId.FOUR_FOUR.layout.beatClicks,
        )
    }

    // 7/8 counts in groups, 2 + 2 + 3: three clicks a bar, no eighth ticks.
    @Test
    fun sevenEightClicksEachGroup() {
        assertEquals(listOf(0, 2, 4), MeterId.SEVEN_EIGHT.layout.beatClicks.map { it.step })
        assertEquals(listOf(1, 2, 3), MeterId.SEVEN_EIGHT.layout.beatClicks.map { it.number })
    }

    @Test
    fun sixEightClicksTwice() = assertEquals(listOf(0, 3), MeterId.SIX_EIGHT.layout.beatClicks.map { it.step })

    @Test
    fun oneBarCountInClicksEachBeat() {
        val clicks = MeterId.THREE_FOUR.layout.countInClicks()
        assertEquals(listOf(0, 4, 8), clicks.map { it.step })
        assertEquals(listOf(1, 2, 3), clicks.map { it.number })
        assertEquals(listOf(ClickKind.ACCENT, ClickKind.BEAT, ClickKind.BEAT), clicks.map { it.kind })
    }

    @Test
    fun offNeverClicks() {
        assertFalse(ClickMode.OFF.clicks(onPads = false))
        assertFalse(ClickMode.OFF.clicks(onPads = true))
    }

    @Test
    fun recClicksOnlyOnPads() {
        assertFalse(ClickMode.REC.clicks(onPads = false))
        assertTrue(ClickMode.REC.clicks(onPads = true))
    }

    @Test
    fun storesStableRawValues() = assertEquals(listOf("off", "rec"), ClickMode.entries.map { it.raw })

    // Anything unknown, including the retired "on", reads as rec, the default.
    @Test
    fun unknownReadsAsRec() {
        assertEquals(ClickMode.REC, ClickMode.fromStored("on"))
        assertEquals(ClickMode.OFF, ClickMode.fromStored("off"))
        assertEquals(ClickMode.REC, ClickMode.fromStored("rec"))
        assertEquals(ClickMode.REC, ClickMode.fromStored("junk"))
        assertEquals(ClickMode.REC, ClickMode.fromStored(null))
    }
}
