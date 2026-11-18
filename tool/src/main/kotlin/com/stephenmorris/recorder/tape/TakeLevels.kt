package com.stephenmorris.recorder.tape

import kotlin.math.abs
import kotlin.math.floor

/**
 * A take's level as it's recorded, measured as `MixLevels` measures the mix: the loudest sample of every
 * [FRAMES_PER_PEAK] frames, so the lane draws the real take while it records and keeps it there, unchanged, until the
 * mix is measured again with it in.
 */
class TakeLevels(
    /** Where on the tape the take begins, in seconds. */
    val start: Double,
    val sampleRate: Double,
) {
    private val measured = mutableListOf<Float>()

    /** The loudest sample of each whole [FRAMES_PER_PEAK] frames so far. */
    val peaks: List<Float>
        get() = measured

    /** The loudest of the samples since the last whole peak, and how many there are. */
    private var pendingPeak = 0f
    private var pendingCount = 0

    /** Adds the first [count] of [samples]: what's been recorded since last time. */
    fun append(samples: FloatArray, count: Int) {
        for (index in 0 until minOf(count, samples.size)) {
            pendingPeak = maxOf(pendingPeak, abs(samples[index]))
            pendingCount += 1
            if (pendingCount == FRAMES_PER_PEAK) {
                measured += pendingPeak
                pendingPeak = 0f
                pendingCount = 0
            }
        }
    }

    /**
     * The loudest level from [from] up to [to] seconds on the tape, or `null` outside what's been recorded. A range
     * that starts before the take and ends in it has the take's level, as the mix measured with the take in does.
     */
    fun level(from: Double, to: Double): Float? {
        val size = FRAMES_PER_PEAK.toDouble()
        if (to <= start) {
            return null
        }
        val first = maxOf(0, floor((from - start) * sampleRate / size).toInt())
        if (first >= measured.size) {
            return null
        }
        val last = minOf(maxOf(floor((to - start) * sampleRate / size).toInt(), first + 1), measured.size)
        return measured.subList(first, last).max()
    }

    companion object {
        /** The frames each peak measures, for takes and the mix alike. */
        const val FRAMES_PER_PEAK = 512
    }
}
