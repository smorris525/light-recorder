package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingTest {
    @Test
    fun fromStoppedCountsIn() = assertEquals(RecordControl.Action.COUNT_IN, RecordControl.tapped(null, isPlaying = false))

    @Test
    fun whilePlayingRecordsNow() = assertEquals(RecordControl.Action.RECORD_NOW, RecordControl.tapped(null, isPlaying = true))

    @Test
    fun duringACountInCancels() =
        assertEquals(RecordControl.Action.CANCEL_COUNT_IN, RecordControl.tapped(TakePhase.COUNTING_IN, isPlaying = true))

    @Test
    fun whileRecordingStopsRecording() =
        assertEquals(RecordControl.Action.STOP_RECORDING, RecordControl.tapped(TakePhase.RECORDING, isPlaying = true))

    @Test
    fun leavingPads() {
        assertEquals(RecordControl.LeavingPads.END_TAKE, RecordControl.leavingPads(TakePhase.RECORDING))
        assertEquals(RecordControl.LeavingPads.STOP, RecordControl.leavingPads(TakePhase.COUNTING_IN))
        assertEquals(RecordControl.LeavingPads.NOTHING, RecordControl.leavingPads(null))
    }

    // 4/4 at 120 bpm, 44.1 kHz: a step is 5,512.5 frames.
    @Test
    fun countInClicksOnEveryBeatOfTheBar() {
        val schedule = CountInSchedule(MeterId.FOUR_FOUR.layout, 1_000, 5_512.5)
        assertEquals(4, schedule.clicks.size)
        assertEquals(listOf(1, 2, 3, 4), schedule.clicks.map { it.number })
        assertEquals(listOf(ClickKind.ACCENT, ClickKind.BEAT, ClickKind.BEAT, ClickKind.BEAT), schedule.clicks.map { it.kind })
        assertEquals(1_000L + 22_050, schedule.clicks[1].frame)
        assertEquals(1_000L + 88_200, schedule.endFrame)
    }

    @Test
    fun sevenEightCountsInThreeTimes() {
        val schedule = CountInSchedule(MeterId.SEVEN_EIGHT.layout, 0, 1_000.0)
        assertEquals(listOf(0L, 2_000L, 4_000L), schedule.clicks.map { it.frame })
        assertEquals(7_000L, schedule.endFrame)
    }

    @Test
    fun showsTheLastCountHeard() {
        val schedule = CountInSchedule(MeterId.THREE_FOUR.layout, 100, 1_000.0)
        assertNull(schedule.number(99))
        assertEquals(1, schedule.number(100))
        assertEquals(1, schedule.number(4_099))
        assertEquals(3, schedule.number(8_100))
        assertNull(schedule.number(12_100))
    }

    private val late = HitPlacement(4, isScheduled = false)
    private val onTime = HitPlacement(4, isScheduled = true)

    @Test
    fun aHardTapRecordsAnAccent() {
        val (grid, hit) = Take.record(Sound.SNARE, Level.ACCENT, late, StepGrid.EMPTY)
        assertEquals(Level.ACCENT, grid[Sound.SNARE, 4])
        assertTrue(hit.changed)
        assertTrue(hit.suppressesStep)
    }

    @Test
    fun accentOverdubUpgradesAHit() {
        val (upgraded, hit) = Take.record(Sound.SNARE, Level.ACCENT, onTime, StepGrid.EMPTY.with(Sound.SNARE, 4, Level.HIT))
        assertEquals(Level.ACCENT, upgraded[Sound.SNARE, 4])
        assertTrue(hit.changed)
        assertFalse(hit.suppressesStep)
        val (afterSofter, softer) = Take.record(Sound.SNARE, Level.HIT, late, upgraded)
        assertEquals(Level.ACCENT, afterSofter[Sound.SNARE, 4])
        assertFalse(softer.changed)
        assertTrue(softer.suppressesStep)
    }

    @Test
    fun aNewHitIsRecorded() {
        val (grid, hit) = Take.record(Sound.HAT, Level.HIT, onTime, StepGrid.EMPTY)
        assertEquals(Level.HIT, grid[Sound.HAT, 4])
        assertTrue(hit.changed)
    }

    // Playing along with an existing part, a late tap must not be heard again when its step plays.
    @Test
    fun aLateTapOnAnExistingHitStillSuppressesTheStep() {
        val (grid, hit) = Take.record(Sound.HAT, Level.HIT, late, StepGrid.EMPTY.with(Sound.HAT, 4, Level.ACCENT))
        assertFalse(hit.changed)
        assertEquals(Level.ACCENT, grid[Sound.HAT, 4])
        assertTrue(hit.suppressesStep)
    }

    @Test
    fun anOnTimeTapSuppressesNothing() =
        assertFalse(Take.record(Sound.HAT, Level.HIT, onTime, StepGrid.EMPTY).second.suppressesStep)
}
