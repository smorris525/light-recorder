package com.stephenmorris.recorder.session

import com.stephenmorris.recorder.core.BarWindow
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How the steps in view fit a row of the pattern screen's overview (spec §8): equal
 * cells with gaps between beats and a wider one between bars, spread to fill the row exactly. In dp.
 */
data class OverviewLayout(
    /** Each step's width. */
    val cell: Float,
    val beatGap: Float,
    val barGap: Float,
    /** The gap after each step in view, by its index in the window: 0 within a beat. */
    val gaps: List<Float>,
) {
    /**
     * A dot's diameter in a row [rowHeight] tall (spec §8): 10 : 6 : 2 proportions, grown to fit the cell or
     * the row, whichever is smaller, so dots stay circular and never touch.
     */
    fun dot(level: Level?, rowHeight: Float): Float {
        val accent = floor(0.7f * minOf(cell, rowHeight))
        return when (level) {
            Level.ACCENT -> accent
            Level.HIT -> (accent * 0.6f).roundToInt().toFloat()
            null -> maxOf(2f, (accent * 0.2f).roundToInt().toFloat())
        }
    }

    companion object {
        /** [rowWidth] is the row's width after the name column. */
        operator fun invoke(rowWidth: Float, window: BarWindow, meter: MeterId): OverviewLayout {
            val layout = meter.layout
            val span = window.span
            val count = layout.stepsPerBar * span
            val gapCount = layout.groups.size * span - 1
            var cell: Float
            var beatGap = 8f
            var barGap = 8f
            if (span > 1) {
                barGap = 20f
                cell = floor((rowWidth - (gapCount - (span - 1)) * beatGap - (span - 1) * barGap) / count)
            } else {
                cell = floor((rowWidth - gapCount * 8f) / count)
                if (cell / 2 > 8) {
                    cell = floor(rowWidth / (count + gapCount / 2f))
                    beatGap = floor(cell / 2)
                }
            }
            // Spread the rounding leftovers into the gaps so the grid reaches the row's right edge.
            val used = count * cell + (gapCount - (span - 1)) * beatGap + (span - 1) * barGap
            if (gapCount > 0 && rowWidth - used > 0) {
                val extra = floor((rowWidth - used) / gapCount)
                beatGap += extra
                barGap += extra
            }
            val starts = layout.starts.toSet()
            val gaps = (0 until count).map { index ->
                val next = index + 1
                when {
                    next >= count || (next % layout.stepsPerBar) !in starts -> 0f
                    next % layout.stepsPerBar == 0 -> barGap
                    else -> beatGap
                }
            }
            return OverviewLayout(maxOf(1f, cell), beatGap, barGap, gaps)
        }
    }
}
