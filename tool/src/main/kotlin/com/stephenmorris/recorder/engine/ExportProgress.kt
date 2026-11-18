package com.stephenmorris.recorder.engine

import kotlin.math.floor

/** The percentage Export shows (spec §13). Reports may arrive out of order, so it never goes backwards. */
object ExportProgress {
    /** [fraction] of the export as a whole percent, never below [previous] and never past 100. */
    fun shown(previous: Int, fraction: Double): Int = maxOf(previous, minOf(100, floor(fraction * 100).toInt()))
}
