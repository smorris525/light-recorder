package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.RulerScale
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView
import com.stephenmorris.recorder.tape.TimeText
import com.stephenmorris.recorder.tape.roundHalfAway
import kotlin.math.ceil
import kotlin.math.floor

/**
 * What the ruler draws, without the drawing: a tick every minor step and a taller one
 * every major, none before 0:00 or past the song's end; each major tick's time just right of it, giving way to the
 * playhead's time; and the playhead's time over its marker, kept inside the margins.
 */
object RulerMarks {
    /** How near the playhead's time a major tick's time may come before it gives way. */
    const val LABEL_CLEARANCE = 8.0

    data class Tick(val x: Double, val isMajor: Boolean)

    /** A time printed on the ruler, its left edge at [x]. */
    data class Label(val text: String, val x: Double, val isPlayhead: Boolean)

    /** The ticks and labels across [width]; [textWidth] measures a label, `true` for the playhead's (medium) one. */
    fun layout(
        song: Song,
        view: TapeView,
        playhead: Double,
        width: Double,
        textWidth: (String, Boolean) -> Double,
    ): Pair<List<Tick>, List<Label>> {
        val tape = TapeFrame(width)
        val scale = RulerScale.forView(view.secondsAcross, tape.inner)
        val playheadX = tape.x(playhead, view)
        val playheadLabel = if (playheadX in 0.0..width) {
            val text = TimeText.readout(playhead)
            val half = textWidth(text, true) / 2
            val centre = playheadX.coerceIn(TapeFrame.INSET + half, maxOf(TapeFrame.INSET + half, width - TapeFrame.INSET - half))
            Label(text, centre - half, isPlayhead = true)
        } else {
            null
        }
        val clear = playheadLabel?.let { label ->
            (label.x - LABEL_CLEARANCE)..(label.x + textWidth(label.text, true) + LABEL_CLEARANCE)
        }

        val brackets = PointBrackets.spans(PointBrackets.uprights(song, view, width))
        val shown = tape.times(view)
        val first = floor(shown.start / scale.minor).toInt()
        val last = minOf(ceil(shown.endInclusive / scale.minor).toInt(), floor(song.length / scale.minor).toInt())
        val minorsPerMajor = roundHalfAway(scale.major / scale.minor).toInt()
        val onTape = tape.x(0.0, view)..tape.x(song.length, view)
        val ticks = mutableListOf<Tick>()
        val labels = mutableListOf<Label>()
        for (index in maxOf(0, first)..maxOf(0, last)) {
            val time = index * scale.minor
            val x = tape.x(time, view)
            if (x < -1 || x > width + 1) {
                continue
            }
            val tickX = x.coerceIn(0.75, width - 0.75)
            val isMajor = index % minorsPerMajor == 0
            if (isMajor) {
                val text = RulerScale.label(time, scale)
                val labelWidth = textWidth(text, false)
                // A time never runs out over the margin before 0:00 or past the song's end; one that would run off the
                // tape's right end goes left of its tick instead.
                var left = x + 3
                if (left + labelWidth > onTape.endInclusive) {
                    left = x - 3 - labelWidth
                }
                val right = left + labelWidth
                val clears = clear == null || right < clear.start || left > clear.endInclusive
                if (left in onTape && right in onTape && clears) {
                    labels += Label(text, left, isPlayhead = false)
                }
            }
            if (brackets.none { tickX in it }) {
                ticks += Tick(tickX, isMajor)
            }
        }
        playheadLabel?.let { labels += it }
        return ticks to labels
    }
}
