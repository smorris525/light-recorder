package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarWindowTest {
    @Test
    fun showsOneBarInMostMeters() {
        for (meter in listOf(MeterId.THREE_FOUR, MeterId.FOUR_FOUR, MeterId.FIVE_FOUR, MeterId.SEVEN_EIGHT, MeterId.TWELVE_EIGHT)) {
            assertEquals(1, BarWindow.of(meter, 4, 0).span, meter.raw)
        }
    }

    @Test
    fun showsTwoBarsInSixEightOnceThereAreTwo() {
        assertEquals(2, BarWindow.of(MeterId.SIX_EIGHT, 2, 0).span)
        assertEquals(1, BarWindow.of(MeterId.SIX_EIGHT, 1, 0).span)
    }

    @Test
    fun staysFull() {
        assertEquals(1, BarWindow.of(MeterId.SIX_EIGHT, 3, 2).firstBar)
        assertEquals(3, BarWindow.of(MeterId.FOUR_FOUR, 4, 7).firstBar)
        assertEquals(0, BarWindow.of(MeterId.FOUR_FOUR, 4, -2).firstBar)
    }

    @Test
    fun stepsInView() {
        val window = BarWindow.of(MeterId.FOUR_FOUR, 4, 2)
        assertEquals(32 until 48, window.stepRange)
        assertEquals(32, window.firstStep)
        val pair = BarWindow.of(MeterId.SIX_EIGHT, 4, 1)
        assertEquals(6 until 18, pair.stepRange)
        assertEquals(1 until 3, pair.barRange)
    }

    @Test
    fun movesAndClamps() {
        val window = BarWindow.of(MeterId.SIX_EIGHT, 4, 0)
        assertEquals(2, window.startingAt(3).firstBar)
        assertEquals(1, window.startingAt(1).firstBar)
    }

    // A corrupt bar count can't make an empty or negative window.
    @Test
    fun corruptBarCountsAreClamped() {
        assertEquals(0 until 16, BarWindow.of(MeterId.FOUR_FOUR, 0, 0).stepRange)
        assertEquals(7, BarWindow.of(MeterId.FOUR_FOUR, 40, 20).firstBar)
    }

    private val window = BarWindow.of(MeterId.FOUR_FOUR, 4, 0)

    @Test
    fun followStartsOff() = assertFalse(Follow().isOn)

    @Test
    fun followingJumpsToThePlayingBarWhenItLeavesTheWindow() {
        val follow = Follow().played()
        assertEquals(0, follow.window(window, 15).firstBar)
        assertEquals(1, follow.window(window, 16).firstBar)
    }

    @Test
    fun notFollowingStaysPut() = assertEquals(0, Follow().window(window, 40).firstBar)

    @Test
    fun editingTurnsFollowOff() = assertFalse(Follow().played().edited().isOn)

    @Test
    fun tappingAnotherBarTurnsFollowOff() = assertFalse(Follow().played().tappedBar(2, 0).isOn)

    @Test
    fun tappingThePlayingBarTurnsFollowBackOn() = assertTrue(Follow().edited().tappedBar(1, 1).isOn)

    @Test
    fun tappingABarWhileStoppedLeavesFollowOff() = assertFalse(Follow().tappedBar(1, null).isOn)

    // The pair window in 6/8 jumps to the playing bar, clamped to stay full.
    @Test
    fun followingAPairClampsToStayFull() {
        val pair = BarWindow.of(MeterId.SIX_EIGHT, 3, 0)
        assertEquals(1, Follow().played().window(pair, 12).firstBar)
    }
}
