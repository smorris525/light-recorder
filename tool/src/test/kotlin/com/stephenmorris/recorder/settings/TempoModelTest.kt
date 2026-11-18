package com.stephenmorris.recorder.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TempoModelTest {
    private val sets = mutableListOf<Int>()
    private var now = 0.0
    private val model = TempoModel(100, { sets += it }, { now })

    @Test
    fun opensShowingTheTempo() = assertEquals("100", model.digits.value)

    // "12" on the way to "120" isn't a tempo, so only 120 is set.
    @Test
    fun typingAppliesLiveTempos() {
        model.press(1)
        model.press(2)
        model.press(0)
        assertEquals(listOf(120), sets)
        assertEquals("120", model.digits.value)
    }

    @Test
    fun arrowsStepFromWhatIsShown() {
        model.step(1)
        assertEquals(listOf(101), sets)
        assertEquals("101", model.digits.value)
        model.press(9)
        assertEquals("9", model.digits.value)
    }

    @Test
    fun tapSetsFromTheSecondTap() {
        model.tap()
        assertTrue(sets.isEmpty())
        now = 0.5
        model.tap()
        assertEquals(listOf(120), sets)
        assertEquals("120", model.digits.value)
    }

    // Leaving with digits that aren't a tempo: "5" sets the clamped 30; nothing leaves the tempo alone.
    @Test
    fun leavingAppliesTheClampedDigits() {
        model.press(5)
        model.leave()
        assertEquals(listOf(30), sets)
        val empty = mutableListOf<Int>()
        val other = TempoModel(120, { empty += it }, { 0.0 })
        repeat(3) { other.delete() }
        other.leave()
        assertTrue(empty.isEmpty())
    }
}
