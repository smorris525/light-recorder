package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Limiter
import kotlin.math.abs

/**
 * Export's mixdown: the song as it's heard, in stereo. Every track's stem at its volume and pan, through a limiter on
 * each channel as playback has, without the silence after the last sound. It's made a block at a time, as the file is
 * written, so a long song is never held mixed in full and every step of it can count towards the export's progress.
 */
class Mixdown(private val stems: List<Stem>, sampleRate: Double) {
    /** A track's audio before its volume and pan, with the gains they make. Stems may differ in length. */
    class Stem(val samples: FloatArray, val gains: Gains)

    private val limiterLeft = Limiter(sampleRate = sampleRate)
    private val limiterRight = Limiter(sampleRate = sampleRate)

    /** The next frame to mix. */
    var position = 0
        private set

    /** The mixdown's length: up to the last sound in either channel. Zero when all is quiet. */
    val frameCount: Int = stems.maxOfOrNull { stem ->
        val gain = maxOf(abs(stem.gains.left), abs(stem.gains.right))
        if (gain <= 0) 0 else stem.samples.indexOfLast { abs(it) * gain >= SILENCE } + 1
    } ?: 0

    /** Whether every frame has been mixed. */
    val isDone: Boolean
        get() = position >= frameCount

    /**
     * Mixes the next [count] frames (fewer at the end) into the start of [left] and [right], through the limiters,
     * returning how many it wrote.
     */
    fun render(left: FloatArray, right: FloatArray, count: Int): Int {
        val frames = minOf(count, frameCount - position, left.size, right.size)
        if (frames <= 0) {
            return 0
        }
        left.fill(0f, 0, frames)
        right.fill(0f, 0, frames)
        for (stem in stems) {
            if (position >= stem.samples.size) {
                continue
            }
            val length = minOf(frames, stem.samples.size - position)
            for (index in 0 until length) {
                val sample = stem.samples[position + index]
                left[index] += sample * stem.gains.left
                right[index] += sample * stem.gains.right
            }
        }
        limiterLeft.process(left, frames)
        limiterRight.process(right, frames)
        position += frames
        return frames
    }

    companion object {
        /** A sample this quiet or quieter is silence, as [Bounce.trimmedSilence] judges it. */
        private const val SILENCE = 1e-6f

        /** The whole mixdown at once, left and right, in blocks of [block] frames. */
        fun mix(stems: List<Stem>, sampleRate: Double, block: Int = 4_096): Pair<FloatArray, FloatArray> {
            val mixdown = Mixdown(stems, sampleRate)
            val left = FloatArray(mixdown.frameCount)
            val right = FloatArray(mixdown.frameCount)
            val blockLeft = FloatArray(block)
            val blockRight = FloatArray(block)
            while (!mixdown.isDone) {
                val at = mixdown.position
                val count = mixdown.render(blockLeft, blockRight, block)
                System.arraycopy(blockLeft, 0, left, at, count)
                System.arraycopy(blockRight, 0, right, at, count)
            }
            return left to right
        }
    }
}
