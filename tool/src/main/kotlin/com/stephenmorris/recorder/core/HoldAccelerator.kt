package com.stephenmorris.recorder.core

/** One repeat of a held arrow: how far it steps, and how long until the next repeat. */
data class HoldStep(val size: Int, val delayMs: Long)

/**
 * Hold-to-repeat on the Tempo page's arrows (spec §13): one step on press, repeats after 0.42 s, steps of 1 every
 * 90 ms, then of 5 every 140 ms after eight repeats.
 */
object HoldAccelerator {
    const val INITIAL_DELAY_MS = 420L

    /** The [count]th repeat, from 1. */
    fun repeatStep(count: Int): HoldStep = if (count <= 8) HoldStep(1, 90) else HoldStep(5, 140)
}
