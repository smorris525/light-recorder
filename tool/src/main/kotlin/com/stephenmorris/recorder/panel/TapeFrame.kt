package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.TapeView

/**
 * The ruler and lane run the screen's full [width] (dp), but the tape on show fills the width between the screen's
 * margins; the margins show the tape just before and after it.
 */
data class TapeFrame(val width: Double) {
    /** The width the tape on show fills. */
    val inner: Double
        get() = maxOf(1.0, width - 2 * INSET)

    /** Where [time] falls across the full width. */
    fun x(time: Double, view: TapeView): Double = INSET + view.x(time, inner)

    /** [x] across the full width, measured from where the tape on show starts. */
    fun tapeX(x: Double): Double = x - INSET

    /** The times at the full width's two ends. */
    fun times(view: TapeView): ClosedFloatingPointRange<Double> =
        view.time(-INSET, inner)..view.time(width - INSET, inner)

    companion object {
        /** The screen's margin. */
        const val INSET = 24.0
    }
}
