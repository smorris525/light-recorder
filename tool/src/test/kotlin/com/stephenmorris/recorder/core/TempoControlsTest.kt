package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TempoControlsTest {
    @Test
    fun tapTempoNeedsTwoTaps() {
        val tapTempo = TapTempo()
        assertNull(tapTempo.tap(0.0))
        assertEquals(120, tapTempo.tap(0.5))
    }

    // Spec §13: the average interval of up to the last five taps.
    @Test
    fun tapTempoAveragesUpToTheLastFiveTaps() {
        val tapTempo = TapTempo()
        tapTempo.tap(0.0)
        assertEquals(120, tapTempo.tap(0.5))
        assertEquals(120, tapTempo.tap(1.0))
        assertEquals(120, tapTempo.tap(1.5))
        assertEquals(120, tapTempo.tap(2.0))
        // Five taps from 0.5: (2.1 - 0.5) / 4 = 0.4 s.
        assertEquals(150, tapTempo.tap(2.1))
    }

    // Only taps within 2.5 seconds count.
    @Test
    fun tapTempoForgetsTapsOlderThanTheWindow() {
        val tapTempo = TapTempo()
        tapTempo.tap(0.0)
        tapTempo.tap(0.5)
        assertNull(tapTempo.tap(5.0))
        assertEquals(150, tapTempo.tap(5.4))
    }

    @Test
    fun tapTempoClampsToTheTempoRange() {
        val fast = TapTempo()
        fast.tap(0.0)
        assertEquals(300, fast.tap(0.1))
        val slow = TapTempo()
        slow.tap(0.0)
        assertEquals(30, slow.tap(2.4))
    }

    // The same tap twice starts over rather than dividing by zero.
    @Test
    fun tapTempoStartsOverOnARepeatedTime() {
        val tapTempo = TapTempo()
        tapTempo.tap(1.0)
        assertNull(tapTempo.tap(1.0))
    }

    // A clock that went back starts over too.
    @Test
    fun tapTempoStartsOverWhenTheClockGoesBack() {
        val tapTempo = TapTempo()
        tapTempo.tap(5.0)
        assertNull(tapTempo.tap(4.0))
        assertEquals(120, tapTempo.tap(4.5))
    }

    private fun typed(keys: String, from: Int = 150): TempoEntry =
        keys.fold(TempoEntry.showing(from)) { entry, key ->
            if (key == '<') entry.deleted() else entry.pressed(key.digitToInt())
        }

    @Test
    fun opensShowingTheTempo() {
        val entry = TempoEntry.showing(150)
        assertEquals("150", entry.digits)
        assertEquals(150, entry.liveTempo)
    }

    @Test
    fun showsAClampedTempo() = assertEquals("300", TempoEntry.showing(999).digits)

    @Test
    fun firstDigitReplacesTheShownTempo() {
        assertEquals("8", typed("8").digits)
        assertEquals("80", typed("80").digits)
    }

    @Test
    fun deleteTakesDigitsOffTheShownTempo() {
        assertEquals("15", typed("<").digits)
        // After a delete, digits add on.
        assertEquals("155", typed("<5").digits)
    }

    // 80 is a tempo, and no digit after it makes one: the next digit starts a new number.
    @Test
    fun aDigitThatCantExtendATempoStartsAfresh() {
        assertEquals("100", typed("80100").digits)
        assertEquals("1", typed("801").digits)
        assertEquals("9", typed("999").digits)
    }

    @Test
    fun aDigitThatExtendsATempoAddsOn() {
        assertEquals("150", typed("150").digits)
        assertEquals("300", typed("300").digits)
        assertEquals("250", typed("250").digits)
    }

    // A full three-digit tempo can't take another digit, so the next starts afresh.
    @Test
    fun aFullTempoStartsAfresh() = assertEquals("1", typed("1201").digits)

    // Digits that aren't a tempo yet keep adding on.
    @Test
    fun digitsBelowTheRangeAddOn() {
        assertEquals("12", typed("12").digits)
        assertEquals("120", typed("120").digits)
    }

    @Test
    fun noLeadingZero() {
        assertEquals("", typed("0").digits)
        assertEquals("5", typed("05").digits)
    }

    @Test
    fun liveTempoOnlyInRange() {
        assertNull(typed("1").liveTempo)
        assertNull(typed("12").liveTempo)
        assertEquals(120, typed("120").liveTempo)
        assertEquals(80, typed("80").liveTempo)
        assertNull(typed("<<<").liveTempo)
    }

    @Test
    fun finalTempoIsClamped() {
        assertEquals(30, typed("5").finalTempo)
        assertEquals(120, typed("120").finalTempo)
        assertNull(typed("<<<").finalTempo)
    }

    @Test
    fun outOfRangeDigitsAreIgnored() {
        assertEquals(TempoEntry.showing(150), TempoEntry.showing(150).pressed(10))
    }
}
