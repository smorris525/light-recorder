package com.stephenmorris.recorder.store

/** Changes a recording's sample rate: straight lines between samples. */
object Resampler {
    /** [samples] at [to] rather than [from] samples a second; unchanged for equal or bad rates, or no samples. */
    fun linear(samples: FloatArray, from: Double, to: Double): FloatArray {
        if (from == to || !(from > 0) || !(to > 0) || !from.isFinite() || !to.isFinite() || samples.isEmpty()) {
            return samples
        }
        val step = from / to
        val count = (samples.size / step).toInt()
        val last = samples.size - 1
        return FloatArray(count) { index ->
            val position = index * step
            val lower = minOf(position.toInt(), last)
            val upper = minOf(lower + 1, last)
            val fraction = (position - lower).toFloat()
            samples[lower] + (samples[upper] - samples[lower]) * fraction
        }
    }
}
