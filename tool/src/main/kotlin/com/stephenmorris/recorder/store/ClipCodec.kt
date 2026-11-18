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

    /** Writes what [encode] makes to [out], a block at a time. */
    fun write(out: java.io.OutputStream, samples: FloatArray, sampleRate: Double) {
        val block = ByteBuffer.allocate(BLOCK_FLOATS * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        block.putDouble(sampleRate)
        out.write(block.array(), 0, Double.SIZE_BYTES)
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(BLOCK_FLOATS, samples.size - offset)
            block.clear()
            block.asFloatBuffer().put(samples, offset, count)
            out.write(block.array(), 0, count * Float.SIZE_BYTES)
            offset += count
        }
    }

    private const val BLOCK_FLOATS = 16_384

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
