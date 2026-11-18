package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulerMarksTest {
    private val song = Song.new("s")
    private val width = 348.0
    private val textWidth = { text: String, _: Boolean -> text.length * 5.0 }

    private fun layout(song: Song = this.song, playhead: Double = 0.0) =
        RulerMarks.layout(song, TapeView(), playhead, width, textWidth)

    @Test
    fun noTicksBeforeZeroOrPastTheEnd() {
        val (ticks, _) = layout()
        assertEquals(31, ticks.size)
        assertTrue(ticks.all { it.x in 24.0..324.0 })
    }

    @Test
    fun aMajorTickEveryTenSeconds() {
        val (ticks, _) = layout()
        assertEquals(listOf(24.0, 124.0, 224.0, 324.0), ticks.filter { it.isMajor }.map { it.x })
    }

    @Test
    fun ticksTouchingABracketAreLeftOut() {
        val (ticks, _) = layout(song.copy(inPoint = 10.0))
        assertTrue(ticks.none { it.x == 124.0 })
        assertTrue(ticks.any { it.x == 134.0 })
    }

    @Test
    fun thePlayheadLabelStaysInsideTheMargins() {
        val (_, labels) = layout()
        val label = labels.single { it.isPlayhead }
        val half = textWidth(label.text, true) / 2
        assertTrue(label.x >= 24.0)
        assertTrue(label.x + half >= 24.0 + half)
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
        assertEquals(301.0, labels.single { it.text == "0:30" }.x)
    }
}
