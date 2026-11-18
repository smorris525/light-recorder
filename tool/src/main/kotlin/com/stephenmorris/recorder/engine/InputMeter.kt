package com.stephenmorris.recorder.engine

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * The mic's level, written on the input's thread and read on the main thread without locks: the loudest sample since
 * it was last asked, so a reader slower than the input misses no peak, and whether it has clipped.
 */
class InputMeter {
    private val peakBits = AtomicInteger(0)
    private val clipped = AtomicBoolean(false)

    /** Input thread. Measures the first [count] of [samples]. */
    fun record(samples: FloatArray, count: Int) {
        var peak = 0f
        for (index in 0 until minOf(count, samples.size)) {
            peak = maxOf(peak, abs(samples[index]))
        }
        // A non-negative float's bits order as the float does, so the larger bits are the louder peak.
        val bits = peak.toRawBits()
        while (true) {
            val current = peakBits.get()
            if (current >= bits || peakBits.compareAndSet(current, bits)) {
                break
            }
        }
        if (peak >= CLIP_LEVEL) {
            clipped.set(true)
        }
    }

    /** The loudest sample since this was last asked. */
    fun takePeak(): Float = Float.fromBits(peakBits.getAndSet(0))

    /** Whether the input has clipped since this was last asked. */
    fun takeClipped(): Boolean = clipped.getAndSet(false)

    companion object {
        /** A sample this close to full scale counts as clipping. */
        const val CLIP_LEVEL = 0.999f
    }
}
