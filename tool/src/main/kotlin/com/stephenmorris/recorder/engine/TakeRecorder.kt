package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.TakeLevels

/**
 * The samples of one audio take: the input frames that fall from [startFrame] up to its end on the render clock.
 * A gap in the input is kept as silence, so what follows stays where it belongs.
 * Samples are held in one-second chunks, joined only by [samples]. Main thread only.
 */
class TakeRecorder(
    val startFrame: Long,
    endFrame: Long = Long.MAX_VALUE,
    /** The take's level as it records, fed what's kept. */
    val levels: TakeLevels,
) {
    var endFrame = endFrame
        private set

    private val chunks = mutableListOf<FloatArray>()
    private var length = 0L
    private var seenUntil = Long.MIN_VALUE
    private val one = FloatArray(1)

    /** Input has been seen at or past the end. */
    val isComplete: Boolean
        get() = seenUntil >= endFrame

    /** Ends the take at [atFrame], unless it already ends sooner. */
    fun end(atFrame: Long) {
        endFrame = minOf(endFrame, atFrame)
    }

    /** Adds the first [count] of [samples], the first heard at render frame [firstFrame]. */
    fun append(samples: FloatArray, count: Int, firstFrame: Long) {
        for (index in 0 until minOf(count, samples.size)) {
            val frame = firstFrame + index
            seenUntil = maxOf(seenUntil, frame)
            if (frame < startFrame || frame >= endFrame) {
                continue
            }
            val at = frame - startFrame
            if (at < length) {
                continue
            }
            while (length < at) {
                put(0f)
            }
            put(samples[index])
        }
    }

    /** Everything kept, in one array. */
    fun samples(): FloatArray {
        val result = FloatArray(length.toInt())
        var offset = 0
        for (chunk in chunks) {
            val count = minOf(CHUNK_FRAMES, result.size - offset)
            if (count <= 0) {
                break
            }
            System.arraycopy(chunk, 0, result, offset, count)
            offset += count
        }
        return result
    }

    private fun put(sample: Float) {
        val index = (length % CHUNK_FRAMES).toInt()
        if (index == 0) {
            chunks += FloatArray(CHUNK_FRAMES)
        }
        chunks.last()[index] = sample
        length += 1
        one[0] = sample
        levels.append(one, 1)
    }

    private companion object {
        const val CHUNK_FRAMES = 48_000
    }
}
