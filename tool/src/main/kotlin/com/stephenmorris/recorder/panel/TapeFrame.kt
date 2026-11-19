package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.TapeView

/**
 * The ruler and lane run the screen's full [width] (dp), but the tape on show fills the width between the margins; the
 * margins show the tape just before and after it, at LightOS's margin.
 */
data class TapeFrame(val width: Double) {
    /** The margin each side: one grid unit, in line with the top bar's back button, as the screens' lists. */
    val inset: Double
        get() = width / GRID_COLUMNS

    /** The width the tape on show fills. */
    val inner: Double
        get() = maxOf(1.0, width - 2 * inset)

    /** Where [time] falls across the full width. */
    fun x(time: Double, view: TapeView): Double = inset + view.x(time, inner)

    /** [x] across the full width, measured from where the tape on show starts. */
    fun tapeX(x: Double): Double = x - inset

    /** The times at the full width's two ends. */
    fun times(view: TapeView): ClosedFloatingPointRange<Double> =
        view.time(-inset, inner)..view.time(width - inset, inner)

    private companion object {
        /** LightOS's grid is 27 units across the screen (the SDK's `LightGrid.WIDTH`); the panel spans the screen. */
        const val GRID_COLUMNS = 27.0
    }
}
