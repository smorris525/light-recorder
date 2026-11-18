package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Span
import com.stephenmorris.recorder.tape.TapeView
import kotlin.test.Test
import kotlin.test.assertTrue

class WaveformLinesTest {
    private val width = 348.0
    private val height = 30.0

    private fun lines(
        tracks: List<Pair<List<Span>, Float>> = emptyList(),
        live: WaveformLines.Source? = null,
        liveHits: WaveformLines.Source? = null,
    ) = WaveformLines.lines(width, height, TapeView(), null, tracks, live, liveHits)

    @Test
    fun nothingRecordedDrawsNothing() {
        assertTrue(lines(tracks = listOf(emptyList<Span>() to 1f)).isEmpty())
    }

    @Test
    fun aSpanWithNoRecordingDrawsItsStandIn() {
        val drawn = lines(tracks = listOf(listOf(Span(10.0, 20.0, 1.0)) to 1f))
        assertTrue(drawn.isNotEmpty())
        assertTrue(drawn.all { it.x in 126.0..226.0 }, drawn.map { it.x }.toString())
    }

    @Test
    fun overFullScaleIsClipped() {
        val drawn = lines(live = WaveformLines.Source(gain = 1f) { _, _ -> 2f })
        assertTrue(drawn.isNotEmpty())
        assertTrue(drawn.all { it is WaveformLines.Line.Clipped })
    }

    @Test
    fun aPatternTakeIsDrawnOnlyUpToThePlayhead() {
        val drawn = lines(liveHits = WaveformLines.Source(gain = 1f, until = 10.0) { _, _ -> 0.5f })
        assertTrue(drawn.isNotEmpty())
        assertTrue(drawn.all { it.x < 126.0 }, drawn.map { it.x }.toString())
        assertTrue(drawn.all { it is WaveformLines.Line.Bar && it.height == 15.0 })
    }
}
