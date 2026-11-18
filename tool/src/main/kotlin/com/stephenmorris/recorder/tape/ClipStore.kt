package com.stephenmorris.recorder.tape

import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Recorded audio the renderer can read. A clip never moves or goes away while the tool runs, so the render thread
 * reads it without locks: [add] publishes it before the event that plays it is sent. The samples added are kept, not
 * copied, so they must not change afterwards.
 */
class ClipStore {
    private val clips = AtomicReferenceArray<FloatArray?>(CAPACITY)
    private var next = 0

    /** Main thread only. Returns the clip's index, or `null` when the store is full. */
    fun add(samples: FloatArray): Int? {
        if (next >= CAPACITY) {
            return null
        }
        clips.set(next, samples)
        next += 1
        return next - 1
    }

    /** Main thread only. Lets a clip go: it plays silence from now on. Its index isn't used again. */
    fun remove(clip: Int) {
        if (clip in 0 until CAPACITY) {
            clips.set(clip, null)
        }
    }

    /** The clip's samples, or `null` for a clip that isn't in the store. Any thread. */
    internal fun samples(clip: Int): FloatArray? = if (clip in 0 until CAPACITY) clips.get(clip) else null

    companion object {
        const val CAPACITY = 4_096
    }
}
