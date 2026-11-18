package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// At 120 bpm in 4/4 a step is 0.125 s: 6,000 frames at 48 kHz.
class PlaybackSchedulerTest {
    private fun scheduler(startStep: Int = 0, startFrame: Long = 0) = PlaybackScheduler(48_000.0, startStep, startFrame)

    private fun kicks(steps: List<Int>, level: Level = Level.ACCENT): StepGrid =
        steps.fold(StepGrid.EMPTY) { grid, step -> grid.with(Sound.KICK, step, level) }

    @Test
    fun schedulesStepsBeforeTheHorizon() {
        val steps = scheduler(startFrame = 1_000).advance(13_001, 1_000, testPlayback(steps = kicks(listOf(0))))
        assertEquals(
            listOf(
                PlaybackStep(0, 1_000, listOf(Trigger(Sound.KICK, 1f)), ClickKind.ACCENT),
                PlaybackStep(1, 7_000, emptyList(), null),
                PlaybackStep(2, 13_000, emptyList(), null),
            ),
            steps,
        )
    }

    @Test
    fun neverSchedulesAStepTwice() {
        val scheduler = scheduler()
        scheduler.advance(12_000, 0, testPlayback())
        assertTrue(scheduler.advance(12_000, 0, testPlayback()).isEmpty())
        assertEquals(listOf(2), scheduler.advance(12_001, 0, testPlayback()).map { it.step })
    }

    @Test
    fun loopsThePattern() {
        val steps = scheduler().advance(6_000L * 17, 0, testPlayback())
        assertEquals((0 until 16).toList() + 0, steps.map { it.step })
    }

    @Test
    fun clicksOnBeatsEveryBar() {
        val steps = scheduler().advance(6_000L * 32, 0, testPlayback(bars = 2))
        assertEquals(listOf(0, 4, 8, 12, 16, 20, 24, 28), steps.filter { it.click != null }.map { it.step })
        assertEquals(listOf(0, 16), steps.filter { it.click == ClickKind.ACCENT }.map { it.step })
    }

    // Heavy 1/8 swing pushes the off-beat sixteenths late: 0.32, 0.64 and 0.32 of a step.
    @Test
    fun swingDelaysOffBeatSteps() {
        val steps = scheduler().advance(24_000, 0, testPlayback(swing = Swing.EIGHTH_HEAVY))
        assertEquals(listOf(0L, 7_920L, 15_840L, 19_920L), steps.map { it.frame })
    }

    @Test
    fun sevenEightStepsAreEighths() {
        val steps = scheduler().advance(48_000, 0, testPlayback(meter = MeterId.SEVEN_EIGHT))
        assertEquals(listOf(0L, 24_000L), steps.map { it.frame })
    }

    @Test
    fun tempoChangesApplyFromTheNextStep() {
        val scheduler = scheduler()
        scheduler.advance(12_000, 0, testPlayback())
        val steps = scheduler.advance(36_000, 6_000, testPlayback(bpm = 60))
        assertEquals(listOf(2, 3), steps.map { it.step })
        assertEquals(listOf(12_000L, 24_000L), steps.map { it.frame })
    }

    @Test
    fun editsApplyFromTheNextStep() {
        val scheduler = scheduler()
        scheduler.advance(12_000, 0, testPlayback())
        val steps = scheduler.advance(18_000, 6_000, testPlayback(steps = kicks(listOf(2), Level.HIT)))
        assertEquals(listOf(PlaybackStep(2, 12_000, listOf(Trigger(Sound.KICK, 0.6f)), null)), steps)
    }

    // A change of meter or bar count carries on from the same step number, wrapped into the new
    // length when the pattern got shorter.
    @Test
    fun longerPatternCarriesOnFromTheSameStep() {
        val scheduler = scheduler()
        scheduler.advance(6_000L * 10, 0, testPlayback(bars = 2))
        val steps = scheduler.advance(6_000L * 12, 6_000L * 9, testPlayback(bars = 4))
        assertEquals(listOf(10, 11), steps.map { it.step })
    }

    @Test
    fun shorterPatternWrapsTheNextStep() {
        val scheduler = scheduler()
        scheduler.advance(6_000L * 20, 0, testPlayback(bars = 2))
        val steps = scheduler.advance(6_000L * 22, 6_000L * 19, testPlayback(bars = 1))
        assertEquals(listOf(4, 5), steps.map { it.step })
        assertEquals(listOf(120_000L, 126_000L), steps.map { it.frame })
    }

    @Test
    fun shorteningFromStepThirtyCarriesOnAtFourteen() {
        val scheduler = scheduler()
        scheduler.advance(6_000L * 30, 0, testPlayback(bars = 2))
        val steps = scheduler.advance(6_000L * 30 + 1, 6_000L * 29, testPlayback(bars = 1))
        assertEquals(listOf(14), steps.map { it.step })
    }

    @Test
    fun shorterMeterWrapsTheNextStep() {
        val scheduler = scheduler()
        scheduler.advance(6_000L * 9, 0, testPlayback())
        val steps = scheduler.advance(6_000L * 9 + 1, 6_000L * 8, testPlayback(meter = MeterId.SIX_EIGHT))
        assertEquals(listOf(3), steps.map { it.step })
    }

    // After a long stall, playback resumes from now instead of firing every missed step.
    @Test
    fun reanchorsAfterALongStall() {
        val steps = scheduler().advance(66_000, 60_000, testPlayback())
        assertEquals(listOf(0), steps.map { it.step })
        assertEquals(listOf(60_000L), steps.map { it.frame })
    }

    @Test
    fun playsAStepThatIsBarelyLate() {
        assertEquals(listOf(0L), scheduler().advance(6_000, 480, testPlayback()).map { it.frame })
    }

    @Test
    fun skipsStepsMissedDuringAShortStall() {
        val scheduler = scheduler()
        scheduler.advance(6_000, 0, testPlayback())
        assertEquals(listOf(2, 3, 4), scheduler.advance(24_001, 7_000, testPlayback()).map { it.step })
    }

    @Test
    fun startsMidPattern() {
        assertEquals(listOf(5), scheduler(startStep = 5).advance(1, 0, testPlayback()).map { it.step })
    }
}
