package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView
import kotlin.test.Test
import kotlin.test.assertEquals

class TapeFrameTest {
    private val tape = TapeFrame(324.0)

    @Test
    fun zeroSitsOneGridUnitIn() {
        // A grid unit is a 27th of the width, as LightOS's grid: in line with the top bar's back button.
        assertEquals(300.0, tape.inner)
        assertEquals(12.0, tape.x(0.0, TapeView()))
    }

    @Test
    fun timesAtTheEdges() {
        val times = tape.times(TapeView())
        assertEquals(-1.2, times.start, 1e-9)
        assertEquals(31.2, times.endInclusive, 1e-9)
    }
}

class PointBracketsTest {
    private val song = Song.new("s")

    @Test
    fun anInPointOpensRight() {
        val uprights = PointBrackets.uprights(song.copy(inPoint = 10.0), TapeView(), 324.0)

        assertEquals(listOf(PointBrackets.Upright(112.0, true)), uprights)
        val span = PointBrackets.spans(uprights).single()
        assertEquals(110.5, span.start, 1e-9)
        assertEquals(119.5, span.endInclusive, 1e-9)
    }

    @Test
    fun pointsOffScreenAreLeftOut() {
        val uprights = PointBrackets.uprights(song.copy(inPoint = 1.0, outPoint = 60.0), TapeView(start = 20.0), 324.0)
        assertEquals(emptyList(), uprights)
    }
}
