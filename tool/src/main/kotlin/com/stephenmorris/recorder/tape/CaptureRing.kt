package com.stephenmorris.recorder.tape

import java.util.concurrent.atomic.AtomicInteger

/**
 * Audio samples on their way from the input thread to the writer: one producer, one consumer, no locks, and no
 * allocation after construction. Holds up to `capacity - 1` samples. A writer that falls a whole ring behind loses
 * samples rather than stalling the audio.
 */
class CaptureRing(private val capacity: Int) {
    private val buffer = FloatArray(capacity)

    /** The next index to write; only the producer stores it. */
    private val head = AtomicInteger(0)

    /** The next index to read; only the consumer stores it. */
    private val tail = AtomicInteger(0)

    init {
        require(capacity > 1) { "CaptureRing needs a capacity of at least 2" }
    }

    /** Producer only. Writes the first [count] of [samples], dropping what doesn't fit. */
    fun write(samples: FloatArray, count: Int) {
        var index = head.get()
        val end = tail.get()
        for (sample in 0 until minOf(count, samples.size)) {
            val next = (index + 1) % capacity
            if (next == end) {
                break
            }
            buffer[index] = samples[sample]
            index = next
        }
        head.set(index)
    }

    /** Consumer only. Copies what's waiting into [into], as much as fits, and returns how many samples it copied. */
    fun read(into: FloatArray): Int {
        var index = tail.get()
        val end = head.get()
        var count = 0
        while (index != end && count < into.size) {
            into[count] = buffer[index]
            index = (index + 1) % capacity
            count += 1
        }
        tail.set(index)
        return count
    }
}
