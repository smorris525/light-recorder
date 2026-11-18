package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HitPlacementTest {
    private fun history(vararg steps: Pair<Int, Long>) =
        PlayheadHistory().apply { record(steps.map { (step, frame) -> PlaybackStep(step, frame, emptyList(), null) }) }

    private val three = history(0 to 0L, 1 to 1_000L, 2 to 2_000L)

    @Test
    fun theFirstHalfOfAStepLandsOnIt() = assertEquals(HitPlacement(0, true), three.placement(400, 1_000.0, 16))

    @Test
    fun theSecondHalfLandsOnTheNextStep() = assertEquals(HitPlacement(1, true), three.placement(600, 1_000.0, 16))

    // At slow tempos the next step may not be scheduled yet; it must not then sound twice.
    @Test
    fun aLateTapWithNothingScheduledLandsOnAnUnscheduledStep() =
        assertEquals(HitPlacement(3, false), three.placement(2_600, 1_000.0, 16))

    @Test
    fun wrapsAtTheEndOfThePattern() =
        assertEquals(HitPlacement(0, false), history(15 to 15_000L).placement(15_600, 1_000.0, 16))

    // Coming in a hair early on the downbeat after the count-in still records on it.
    @Test
    fun aTapJustBeforeTheFirstStepLandsOnIt() {
        val history = history(0 to 1_000L)
        assertEquals(HitPlacement(0, true), history.placement(600, 1_000.0, 16))
        assertNull(history.placement(400, 1_000.0, 16))
    }

    // Swung offbeats start late, so the halfway point between two steps moves with them.
    @Test
    fun followsSwungSteps() {
        val history = history(0 to 0L, 1 to 1_333L, 2 to 2_000L)
        assertEquals(0, history.placement(600, 1_000.0, 16)?.step)
        assertEquals(1, history.placement(700, 1_000.0, 16)?.step)
    }

    @Test
    fun nothingPlayedPlacesNothing() = assertNull(PlayheadHistory().placement(0, 1_000.0, 16))

    // At slow tempos the first step isn't scheduled yet when a hair-early downbeat tap comes in. The take's start step
    // and frame still place it, as not yet scheduled (so it won't sound twice).
    @Test
    fun anEarlyDownbeatBeforeAnythingIsScheduledLandsOnTheStartStep() {
        val upcoming = HitPlacement.Upcoming(0, 1_000)
        assertEquals(HitPlacement(0, false), PlayheadHistory().placement(600, 1_000.0, 16, upcoming))
        assertNull(PlayheadHistory().placement(400, 1_000.0, 16, upcoming))
    }
}
