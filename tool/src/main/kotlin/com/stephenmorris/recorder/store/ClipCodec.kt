package com.stephenmorris.recorder.store

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A recording's bytes on disk: the sample rate as an 8-byte little-endian `Double`, then the
 * samples as little-endian `Float`s. A measured mix's peaks use the same layout.
 */
object ClipCodec {
    fun encode(samples: FloatArray, sampleRate: Double): ByteArray {
        val buffer = ByteBuffer.allocate(Double.SIZE_BYTES + samples.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putDouble(sampleRate)
        buffer.asFloatBuffer().put(samples)
        return buffer.array()
    }

    /** The rate and samples, or `null` under 8 bytes or for a rate that isn't finite and above 0. */
    fun decode(bytes: ByteArray): Pair<Double, FloatArray>? {
        if (bytes.size < Double.SIZE_BYTES) {
            return null
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rate = buffer.getDouble()
        if (!rate.isFinite() || rate <= 0) {
            return null
        }
        val samples = FloatArray((bytes.size - Double.SIZE_BYTES) / Float.SIZE_BYTES)
        buffer.asFloatBuffer().get(samples)
        return rate to samples
    }
}
