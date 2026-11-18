package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.LaneDetail
import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.Span
import com.stephenmorris.recorder.tape.TapeView

/**
 * The lane's lines, without the drawing: 1 dp lines 2 dp apart, each standing for the loudest of
 * the 2 dp leading up to it, so a sound's first line is never before where it starts. A line reaches the lane's full
 * height at full scale; over it, it's [Line.Clipped].
 */
object WaveformLines {
    sealed interface Line {
        val x: Double

        /** A line [height] tall, centred on the lane. */
        data class Bar(override val x: Double, val height: Double) : Line

        /** A line over full scale: full height, with a gap through its middle. */
        data class Clipped(override val x: Double) : Line
    }

    /** A take's level from one time to another, at its track's [gain]; drawn only before [until], if set. */
    class Source(val gain: Float, val until: Double? = null, val level: (Double, Double) -> Float?)

    /**
     * The lines across [width] × [height]: the measured mix [levels], each track's spans with no recording as a stand-in
     * at its gain, and the take under way or just recorded ([live], audio; [liveHits], a pattern take).
     */
    fun lines(
        width: Double,
        height: Double,
        view: TapeView,
        levels: MixLevels?,
        tracks: List<Pair<List<Span>, Float>>,
        live: Source?,
        liveHits: Source?,
    ): List<Line> {
        val tape = TapeFrame(width)
        val inner = tape.inner
        val secondsPerLine = 2 / inner * view.secondsAcross
        val lines = mutableListOf<Line>()
        var x = 0.0
        while (x < width) {
            val lineStart = view.time(tape.tapeX(x) - 2, inner)
            if (lineStart + secondsPerLine <= 0) {
                x += 2
                continue
            }
            val time = maxOf(0.0, lineStart)
            val end = lineStart + secondsPerLine
            var level = levels?.level(time, end) ?: 0f
            for ((spans, gain) in tracks) {
                LaneDetail.standIn(time, spans)?.let { level += it.toFloat() * gain }
            }
            live?.level?.invoke(time, end)?.let { level += it * live.gain }
            if (liveHits != null) {
                val until = liveHits.until ?: Double.POSITIVE_INFINITY
                if (time < until) {
                    liveHits.level(time, minOf(end, until))?.let { level += it * liveHits.gain }
                }
            }
            if (MixLevels.clips(level)) {
                lines += Line.Clipped(x)
            } else if (level > 0.001f) {
                lines += Line.Bar(x, maxOf(1.0, level * height))
            }
            x += 2
        }
        return lines
    }
}
