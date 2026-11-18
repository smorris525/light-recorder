package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayheadHistoryTest {
    private fun step(step: Int, frame: Long) = PlaybackStep(step, frame, emptyList(), null)

    private val steps = listOf(step(0, 1_000), step(1, 7_000), step(2, 13_000))

    @Test
    fun findsTheStepSoundingAtAFrame() {
        val history = PlayheadHistory().apply { record(steps) }
        assertNull(history.step(999))
        assertEquals(0, history.step(1_000))
        assertEquals(0, history.step(6_999))
        assertEquals(1, history.step(7_000))
        assertEquals(2, history.step(50_000))
    }

    @Test
    fun resetForgetsEverything() {
        val history = PlayheadHistory().apply { record(steps) }
        history.reset()
        assertNull(history.step(50_000))
    }

    @Test
    fun keepsOnlyTheMostRecentSteps() {
        val history = PlayheadHistory().apply { record((0 until 100).map { step(it % 16, it * 10L) }) }
        assertNull(history.step(0))
        assertEquals(99 % 16, history.step(990))
    }

    @Test
    fun findsStepsRecordedInBatches() {
        val history = PlayheadHistory().apply {
            record(listOf(step(4, 1_000)))
            record(listOf(step(5, 2_000)))
        }
        assertNull(history.step(999))
        assertEquals(4, history.step(1_500))
        assertEquals(5, history.step(2_000))
    }
}
