package com.stephenmorris.recorder.engine

import kotlin.math.roundToLong

/**
 * Places the mic's samples on the render clock (spec §4.3). The output's and input's timestamps both map frames to
 * `System.nanoTime`, so each input frame lines up with the render frame being heard when it was captured. There are no
 * reported latencies to add.
 */
object CaptureAlignment {
    /** A fixed correction, for tuning on hardware (spec §19). */
    const val CORRECTION_FRAMES = 0L

    /** The render frame being heard when [inputFrame] was captured. */
    fun renderFrame(
        inputFrame: Long,
        input: Stamp,
        output: Stamp,
        sampleRate: Int,
        correctionFrames: Long = CORRECTION_FRAMES,
    ): Long {
        val capturedNanos = input.nanos + (inputFrame - input.frame) * 1e9 / sampleRate
        return output.frame + ((capturedNanos - output.nanos) * sampleRate / 1e9).roundToLong() + correctionFrames
    }
}
