package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import com.stephenmorris.recorder.core.Sound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternEditingTest {
    private val fixture = TransportFixture()
    private val model = fixture.model
    private val transport = fixture.transport
    private val editing = PatternEditing(model, transport)
    private val a = model.song.patterns[0].id

    private fun pattern(id: java.util.UUID = a) = model.pattern(id)!!

    @Test
    fun tappingTogglesAStep() {
        editing.tap(a, Sound.SHAKER, 1)
        assertEquals(Level.HIT, pattern().steps[Sound.SHAKER, 1])
        editing.tap(a, Sound.SHAKER, 1)
        assertNull(pattern().steps[Sound.SHAKER, 1])
    }

    @Test
    fun holdingSetsAnAccent() {
        editing.accent(a, Sound.SHAKER, 3)
        assertEquals(Level.ACCENT, pattern().steps[Sound.SHAKER, 3])
    }

    @Test
    fun aPaintStrokeSetsEveryStepItCrosses() {
        editing.beginPaint(a, Sound.SHAKER, 1)
        editing.paint(a, Sound.SHAKER, 1)
        editing.paint(a, Sound.SHAKER, 2)
        editing.paint(a, Sound.SHAKER, 3)
        editing.endPaint()

        assertEquals(listOf(Level.HIT, Level.HIT, Level.HIT), (1..3).map { pattern().steps[Sound.SHAKER, it] })
    }

    @Test
    fun tempoIsClamped() {
        editing.setBpm(a, 400)
        assertEquals(300, pattern().bpm)
        editing.setBpm(a, 10)
        assertEquals(30, pattern().bpm)
    }

    @Test
    fun timeSignatureKeepsTheSteps() {
        val steps = pattern().steps
        editing.setMeter(a, MeterId.THREE_FOUR)
        assertEquals(MeterId.THREE_FOUR, pattern().meter)
        assertEquals(steps, pattern().steps)
    }

    @Test
    fun lengthResizesThePattern() {
        val before = pattern()
        editing.setBars(a, 2)
        assertEquals(before.resized(2), pattern())
        assertEquals(before.steps[Sound.KICK, 0], pattern().steps[Sound.KICK, 16])
    }

    @Test
    fun swingIsSet() {
        editing.setSwing(a, com.stephenmorris.recorder.core.Swing.entries.last())
        assertEquals(com.stephenmorris.recorder.core.Swing.entries.last(), pattern().swing)
    }

    @Test
    fun renamingTrims() {
        editing.rename(a, " Verse ")
        assertEquals("Verse", pattern().name)
    }

    @Test
    fun duplicatingSelectsACopyNamedA2() {
        val copy = editing.duplicate(a)
        assertEquals("A 2", pattern(copy).name)
        assertEquals(copy, model.song.selectedPattern)
        assertEquals(pattern(a).steps, pattern(copy).steps)
    }

    @Test
    fun deletingTheSelectedPatternSelectsItsNeighbour() {
        val copy = editing.duplicate(a)
        editing.delete(copy)
        assertEquals(listOf(a), model.song.patterns.map { it.id })
        assertEquals(a, model.song.selectedPattern)
    }

    @Test
    fun theLastPatternCantBeDeleted() {
        editing.delete(a)
        assertEquals(listOf(a), model.song.patterns.map { it.id })
    }

    @Test
    fun deletingAnAuditionedPatternStopsIt() {
        val copy = editing.duplicate(a)
        editing.audition(copy)
        transport.togglePlay()
        fixture.advance(500)
        assertTrue(transport.isPlaying.value)

        editing.delete(copy)

        assertFalse(transport.isPlaying.value)
    }

    @Test
    fun leavingStopsTheAuditionAndClearsIt() {
        editing.audition(a)
        transport.togglePlay()
        fixture.advance(500)

        editing.leave()

        assertFalse(transport.isPlaying.value)
        assertNull(model.auditionPattern)
    }

    @Test
    fun leavingKeepsAPadsOverdub() {
        editing.audition(a)
        transport.recordIntoPattern(a)
        // Past the count-in: a bar of 4/4 at the default tempo, and then some.
        fixture.advance(3_000)
        assertTrue(transport.isPadRecording.value)
        transport.hit(Sound.SHAKER, Level.HIT)

        editing.leave()

        assertTrue(Sound.SHAKER in pattern().steps.let { steps -> Sound.entries.filter { s -> (0 until 16).any { steps[s, it] != null } } })
        assertFalse(transport.isPadRecording.value)
    }

    @Test
    fun theClickIsOnlyOnPads() {
        editing.padsShown(clickOn = true)
        assertTrue(model.clicks)
        editing.padsHidden()
        assertFalse(model.clicks)
        editing.padsShown(clickOn = true)
        editing.leave()
        assertFalse(model.clicks)
    }

    @Test
    fun leavingDoesntStopTheTape() {
        transport.togglePlay()
        fixture.advance(500)

        editing.leave()

        assertTrue(transport.isPlaying.value)
    }
}
