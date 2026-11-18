package com.stephenmorris.recorder.pads

import com.stephenmorris.recorder.core.PadLayout
import com.stephenmorris.recorder.core.Sound

/** The Pads screen's 2 x 4 grid (spec §10): which pad a point is on, and one text size that fits every name. */
object PadGrid {
    private const val COLUMNS = 2
    private const val ROWS = 4

    /** Inside a pad, either side of its name. */
    private const val TEXT_INSET = 16f

    /** The pad under ([x], [y]) in a grid [width] x [height] with [gap] between pads, or `null` in a gap or outside. */
    fun padAt(x: Float, y: Float, width: Float, height: Float, gap: Float): Sound? {
        val cellWidth = (width - gap * (COLUMNS - 1)) / COLUMNS
        val cellHeight = (height - gap * (ROWS - 1)) / ROWS
        val column = cellIndex(x, cellWidth, gap, COLUMNS) ?: return null
        val row = cellIndex(y, cellHeight, gap, ROWS) ?: return null
        return PadLayout.ORDER[row][column]
    }

    /**
     * The largest text size no bigger than [start], stepping down by 1, at which the widest name ([widths] gives its
     * measured width at a size) fits inside a pad [padWidth] wide. Never below [min].
     */
    fun fittingTextSize(widths: (Float) -> Float, padWidth: Float, start: Float, min: Float = 8f): Float {
        var size = start
        while (size > min && widths(size) > padWidth - TEXT_INSET) {
            size -= 1f
        }
        return maxOf(size, min)
    }

    private fun cellIndex(position: Float, cell: Float, gap: Float, count: Int): Int? {
        if (position < 0) return null
        val index = (position / (cell + gap)).toInt()
        if (index >= count) return null
        return if (position - index * (cell + gap) < cell) index else null
    }
}

/**
 * Which pad each touch plays, as the pads screen asks it for every touch-down and move: only a touch's down plays a
 * pad; sliding onto another pad plays nothing (spec §10).
 */
class PadTouches(private val padAt: (x: Float, y: Float) -> Sound?) {
    fun down(pointer: Int, x: Float, y: Float): Sound? = padAt(x, y)

    @Suppress("UNUSED_PARAMETER")
    fun move(pointer: Int, x: Float, y: Float): Sound? = null
}
