package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulerMarksTest {
    private val song = Song.new("s")
    private val width = 324.0
    private val textWidth = { text: String, _: Boolean -> text.length * 5.0 }

    private fun layout(song: Song = this.song, playhead: Double = 0.0, start: Double = 0.0) =
        RulerMarks.layout(song, TapeView(start = start), playhead, width, textWidth = textWidth)

    @Test
    fun noTicksBeforeZeroOrPastTheEnd() {
        val (ticks, _) = layout()
        assertEquals(31, ticks.size)
        assertTrue(ticks.all { it.x in 12.0..312.0 })
    }

    @Test
    fun aTakePastTheEndStillHasARuler() {
        val view = TapeView(start = 40.0)
        val (ticks, labels) = RulerMarks.layout(song, view, 55.0, width, song.lengthRecordingAt(55.0), textWidth)
        // Scrolled 40 s on, past the blank tape's 0:30 end: 0:40 to 1:10 across, as the tape will be once it's kept.
        assertEquals(listOf(12.0, 112.0, 212.0, 312.0), ticks.filter { it.isMajor }.map { it.x })
        assertTrue(labels.any { it.text == "1:00" })
    }

    @Test
    fun aTakeLengthensTheTapeAsItRuns() {
        assertEquals(40.0, song.lengthRecordingAt(10.0))
        assertEquals(85.0, song.lengthRecordingAt(55.0))
    }

    @Test
    fun aMajorTickEveryTenSeconds() {
        val (ticks, _) = layout()
        assertEquals(listOf(12.0, 112.0, 212.0, 312.0), ticks.filter { it.isMajor }.map { it.x })
    }

    @Test
    fun ticksTouchingABracketAreLeftOut() {
        val (ticks, _) = layout(song.copy(inPoint = 10.0))
        assertTrue(ticks.none { abs(it.x - 112.0) < 1e-9 })
        assertTrue(ticks.any { abs(it.x - 122.0) < 1e-9 })
    }

    @Test
    fun thePlayheadLabelStaysWithinTheTape() {
        val (_, labels) = layout()
        val label = labels.single { it.isPlayhead }
        val half = textWidth(label.text, true) / 2
        assertTrue(label.x >= 12.0)
        assertTrue(label.x + half >= 12.0 + half)
    }

    @Test
    fun aMajorLabelGivesWayToThePlayheads() {
        val (_, labels) = layout(playhead = 10.2)
        assertTrue(labels.none { it.text == "0:10" })
        assertTrue(labels.any { it.text == "0:00" })
    }

    @Test
    fun theLastLabelFlipsLeftAtTheTapesEnd() {
        val (_, labels) = layout(playhead = 15.0)
        assertEquals(289.0, labels.single { it.text == "0:30" }.x)
    }

    // Scrolled 1.5 s on, 0:00 is at x -3, off the left edge.

    @Test
    fun thePlayheadLabelScrollsOffWithTheTape() {
        val (_, labels) = layout(playhead = 0.0, start = 1.5)
        assertEquals(-3.0, labels.single { it.isPlayhead }.x)
        assertTrue(labels.none { it.text == "0:00" })
    }

    @Test
    fun aLabelStaysWhileItsTickIsJustOffTheLeft() {
        val (_, labels) = layout(playhead = 20.0, start = 1.5)
        assertEquals(0.0, labels.single { it.text == "0:00" }.x)
    }

    @Test
    fun aLabelStaysWhileItsTickIsJustOffTheRight() {
        // Scrolled 1.5 s back, 0:30 is at x 327, its label flipped left of it.
        val (_, labels) = layout(playhead = 15.0, start = -1.5)
        assertEquals(304.0, labels.single { it.text == "0:30" }.x)
    }

    @Test
    fun aLabelWhollyOffScreenIsLeftOut() {
        // Scrolled 5 s on, 0:00 is at x -38: its time, and the playhead's there, end before the left edge.
        val (_, labels) = layout(playhead = 0.0, start = 5.0)
        assertTrue(labels.none { it.text == "0:00" })
        assertTrue(labels.none { it.isPlayhead })
    }

    @Test
    fun aLabelStillGivesWayToThePlayheadsOnceThatsOffScreen() {
        // Scrolled 13 s on, the playhead at 9 s is at x -28, its time wholly off the left edge; 0:10, at x -18, still
        // has its time on screen, but it gave way to the playhead's and stays gone with it.
        val (_, labels) = layout(playhead = 9.0, start = 13.0)
        assertTrue(labels.none { it.isPlayhead })
        assertTrue(labels.none { it.text == "0:10" })
    }
}
