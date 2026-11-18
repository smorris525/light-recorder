package com.stephenmorris.recorder.session

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How the sound screen's step editor fills its space (spec §9): one beat per row for each bar in view, square steps
 * sized to fit without scrolling, capped at 64 dp ( or 56 when the meter has more than four beats), and
 * never under 24 dp. In dp.
 */
class SoundEditorLayout(width: Float, height: Float, meter: MeterId, span: Int) {
    /** One beat of one bar in view. [firstStep] counts from the window's first step. */
    data class Row(val bar: Int, val beat: Int, val firstStep: Int, val steps: Int)

    val rows: List<Row>
    val stepSize: Float
    val accentDot: Float
    val hitDot: Float
    val restDot = 4f

    /** The space the rows take. */
    val width: Float
    val height: Float

    init {
        val layout = meter.layout
        val longest = layout.groups.max()
        rows = (0 until span).flatMap { bar ->
            layout.groups.mapIndexed { beat, steps ->
                Row(bar, beat, bar * layout.stepsPerBar + layout.starts[beat], steps)
            }
        }
        val isLarge = layout.groups.size <= 4
        val cap = if (isLarge) 64f else 56f
        val widthFit = (width - LABEL_WIDTH - LABEL_SPACING - (longest - 1) * STEP_SPACING) / longest
        val heightFit = (height - (span - 1) * BAR_GAP - (rows.size - span) * STEP_SPACING) / rows.size
        stepSize = floor(minOf(cap, widthFit, heightFit)).coerceAtLeast(MIN_STEP)
        accentDot = (stepSize * if (isLarge) 34f / 64 else 30f / 56).roundToInt().toFloat()
        hitDot = (accentDot * 0.53f).roundToInt().toFloat()
        this.width = LABEL_WIDTH + LABEL_SPACING + longest * stepSize + (longest - 1) * STEP_SPACING
        this.height = rows.size * stepSize + (rows.size - span) * STEP_SPACING + (span - 1) * BAR_GAP
    }

    fun dot(level: Level?): Float = when (level) {
        Level.ACCENT -> accentDot
        Level.HIT -> hitDot
        null -> restDot
    }

    companion object {
        const val LABEL_WIDTH = 24f
        const val LABEL_SPACING = 6f
        const val STEP_SPACING = 6f
        const val BAR_GAP = 20f
        const val MIN_STEP = 24f
    }
}
