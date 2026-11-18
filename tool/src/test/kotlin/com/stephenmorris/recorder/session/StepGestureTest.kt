package com.stephenmorris.recorder.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepGestureTest {
    private val calls = mutableListOf<String>()
    private val actions = object : StepActions {
        override fun tap(step: Int) { calls += "tap $step" }
        override fun accent(step: Int) { calls += "accent $step" }
        override fun beginPaint(step: Int) { calls += "begin $step" }
        override fun paint(step: Int) { calls += "paint $step" }
        override fun endPaint() { calls += "end" }
    }

    /** 40 dp columns, one row 40 dp tall. */
    private val gesture = StepGesture({ x, y -> if (y in 0f..40f && x >= 0) (x / 40).toInt() else null }, actions)

    @Test
    fun aTapToggles() {
        gesture.down(5f, 5f)
        gesture.up(6f, 6f)
        assertEquals(listOf("begin 0", "tap 0", "end"), calls)
    }

    @Test
    fun aHoldAccentsAndIsNotATap() {
        gesture.down(5f, 5f)
        assertTrue(gesture.holdPending)
        gesture.holdElapsed()
        gesture.up(5f, 5f)
        assertEquals(listOf("begin 0", "accent 0", "end"), calls)
    }

    @Test
    fun movingEightStartsPaintingFromTheFirstCell() {
        gesture.down(5f, 5f)
        gesture.move(125f, 5f)
        gesture.up(125f, 5f)
        assertEquals(listOf("begin 0", "paint 0", "paint 1", "paint 2", "paint 3", "end"), calls)
    }

    @Test
    fun movingLessThanEightIsStillATap() {
        gesture.down(5f, 5f)
        gesture.move(10f, 8f)
        assertTrue(gesture.holdPending)
        gesture.up(10f, 8f)
        assertEquals(listOf("begin 0", "tap 0", "end"), calls)
    }

    @Test
    fun aHoldAfterPaintingStartsDoesNothing() {
        gesture.down(5f, 5f)
        gesture.move(50f, 5f)
        assertFalse(gesture.holdPending)
        gesture.holdElapsed()
        gesture.up(50f, 5f)
        assertEquals(listOf("begin 0", "paint 0", "paint 1", "end"), calls)
    }

    @Test
    fun reversingPaintsEachCellOnce() {
        gesture.down(5f, 5f)
        gesture.move(125f, 5f)
        gesture.move(5f, 5f)
        gesture.up(5f, 5f)
        assertEquals(listOf(0, 1, 2, 3), calls.filter { it.startsWith("paint") }.map { it.removePrefix("paint ").toInt() })
    }

    // A drag that leaves the grid and comes back keeps its stroke.
    @Test
    fun leavingTheGridKeepsTheStroke() {
        gesture.down(5f, 5f)
        gesture.move(20f, 80f)
        gesture.move(165f, 20f)
        gesture.up(165f, 20f)
        assertTrue("paint 4" in calls)
        assertEquals("end", calls.last())
    }

    @Test
    fun startingOffACellIgnoresTheGesture() {
        assertFalse(gesture.down(5f, 80f))
        gesture.move(100f, 5f)
        gesture.holdElapsed()
        gesture.up(100f, 5f)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun cancelEndsTheStrokeWithoutATap() {
        gesture.down(5f, 5f)
        gesture.cancel()
        assertEquals(listOf("begin 0", "end"), calls)
    }
}
