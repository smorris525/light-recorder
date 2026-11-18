package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView

/**
 * The in and out points' brackets, `[` and `]`, among the ruler's ticks: each upright stands at its point, its feet
 * reaching [FOOT] dp into the stretch between them. Ticks that would touch one are left out.
 */
object PointBrackets {
    const val FOOT = 6.0

    /** How far either side of a bracket ticks are left out: as far as their strokes would touch. */
    const val CLEARANCE = 1.5

    /** Where an upright stands across the full width, and whether its feet reach right ([opens]). */
    data class Upright(val x: Double, val opens: Boolean)

    /** The song's points across a full-width strip, those in view. */
    fun uprights(song: Song, view: TapeView, width: Double): List<Upright> {
        val tape = TapeFrame(width)
        return listOf(song.inPoint to true, song.outPoint to false).mapNotNull { (point, opens) ->
            val x = point?.let { tape.x(it, view) } ?: return@mapNotNull null
            if (x < -1 || x > width + 1) null else Upright(x.coerceIn(0.75, width - 0.75), opens)
        }
    }

    /** Where the brackets stand, upright to feet, with the clearance either side. */
    fun spans(uprights: List<Upright>): List<ClosedFloatingPointRange<Double>> = uprights.map { upright ->
        val foot = upright.x + if (upright.opens) FOOT else -FOOT
        (minOf(upright.x, foot) - CLEARANCE)..(maxOf(upright.x, foot) + CLEARANCE)
    }
}
