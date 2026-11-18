package com.stephenmorris.recorder.tape

import kotlin.math.pow

/** Fades a take's first and last [frames] samples, in place, so that replacing part of a track never clicks. */
fun fadeEdges(samples: FloatArray, frames: Int) {
    val length = minOf(frames, samples.size / 2)
    if (length <= 0) {
        return
    }
    for (index in 0 until length) {
        val gain = index.toFloat() / length
        samples[index] *= gain
        samples[samples.size - 1 - index] *= gain
    }
}

/** A level for each channel. */
data class Gains(val left: Float, val right: Float)

/**
 * The track's level in each channel, as it's heard and as the mix's waveform measures it. Volume in dB, then panned by
 * turning the far side down.
 */
val Track.gains: Gains
    get() {
        val level = volumeGain
        val position = pan / 50f
        return Gains(level * minOf(1f, 1 - position), level * minOf(1f, 1 + position))
    }

/** The track's volume alone, as [gains] makes its level: 0 at [Track.SILENT], whatever its pan. */
val Track.volumeGain: Float
    get() = if (volume > Track.SILENT) 10.0.pow(volume / 20.0).toFloat() else 0f
