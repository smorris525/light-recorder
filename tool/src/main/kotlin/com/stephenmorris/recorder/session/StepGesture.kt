package com.stephenmorris.recorder.session

import kotlin.math.ceil
import kotlin.math.hypot

/** What a gesture on the step editor does, by absolute step. */
interface StepActions {
    fun tap(step: Int)

    fun accent(step: Int)

    fun beginPaint(step: Int)

    fun paint(step: Int)

    fun endPaint()
}

/**
 * One touch on the step editor (spec §9): a tap toggles, a hold of [HOLD_MS]
 * sets an accent, and moving [PAINT_DISTANCE] dp paints every cell the touch crosses, starting with the first. The
 * first cell decides whether the stroke turns cells on or off; each cell is painted once, even if the stroke reverses
 * or leaves the grid and comes back. Positions are in dp; [cellAt] maps one to a step, or `null` off the grid.
 */
class StepGesture(private val cellAt: (x: Float, y: Float) -> Int?, private val actions: StepActions) {
    private var first: Int? = null
    private var startX = 0f
    private var startY = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var isPainting = false
    private var didHold = false
    private val painted = mutableSetOf<Int>()

    /** Whether the screen should still call [holdElapsed]: the touch is down and hasn't moved [PAINT_DISTANCE]. */
    val holdPending: Boolean
        get() = first != null && !isPainting && !didHold

    /** A touch starts. `false` if it isn't on a cell; the whole gesture is then ignored. */
    fun down(x: Float, y: Float): Boolean {
        reset()
        val cell = cellAt(x, y) ?: return false
        first = cell
        startX = x
        startY = y
        previousX = x
        previousY = y
        actions.beginPaint(cell)
        return true
    }

    fun move(x: Float, y: Float) {
        if (first == null) {
            return
        }
        moveTo(x, y)
    }

    /** The hold time passed with the touch still down and still: an accent, unless painting has started. */
    fun holdElapsed() {
        val cell = first ?: return
        if (isPainting || didHold) {
            return
        }
        didHold = true
        actions.accent(cell)
    }

    fun up(x: Float, y: Float) {
        val cell = first ?: return
        moveTo(x, y)
        if (!isPainting && !didHold) {
            actions.tap(cell)
        }
        finish()
    }

    fun cancel() {
        if (first != null) {
            finish()
        }
    }

    private fun moveTo(x: Float, y: Float) {
        if (!isPainting) {
            if (hypot(x - startX, y - startY) < PAINT_DISTANCE) {
                return
            }
            isPainting = true
            first?.let(::paint)
        }
        // Samples every half dp along the move, so no cell is skipped however fast the finger goes.
        val count = maxOf(1, ceil(hypot(x - previousX, y - previousY) * 2).toInt())
        for (index in 0..count) {
            val fraction = index.toFloat() / count
            cellAt(previousX + (x - previousX) * fraction, previousY + (y - previousY) * fraction)?.let(::paint)
        }
        previousX = x
        previousY = y
    }

    private fun paint(cell: Int) {
        if (painted.add(cell)) {
            actions.paint(cell)
        }
    }

    private fun finish() {
        actions.endPaint()
        reset()
    }

    private fun reset() {
        first = null
        isPainting = false
        didHold = false
        painted.clear()
    }

    companion object {
        /** How long a still touch takes to set an accent. */
        const val HOLD_MS = 250L

        /** How far a touch moves, in dp, before it paints. */
        const val PAINT_DISTANCE = 8f
    }
}
