package com.stephenmorris.recorder.audio

import java.util.concurrent.atomic.AtomicInteger

/**
 * A fixed-size queue of mixer events for one producer thread and one consumer thread, with no locks and no allocation
 * after construction. Events live in parallel primitive arrays. Holds up to `capacity - 1` events.
 */
class VoiceEventQueue(private val capacity: Int) {
    /** A reusable place for [popInto] to put one event, so the consumer allocates nothing. */
    class Slot {
        var kind = TRIGGER
        var voice = 0
        var frame = 0L
        var gain = 1f
    }

    private val kind = IntArray(capacity)
    private val voice = IntArray(capacity)
    private val frame = LongArray(capacity)
    private val gain = FloatArray(capacity)

    /** The next slot to write; only the producer stores it. */
    private val head = AtomicInteger(0)

    /** The next slot to read; only the consumer stores it. */
    private val tail = AtomicInteger(0)

    init {
        require(capacity > 1) { "VoiceEventQueue needs a capacity of at least 2" }
    }

    /** Producer only. Returns `false`, dropping the event, when the queue is full. */
    fun pushTrigger(voice: Int, frame: Long, gain: Float): Boolean = push(TRIGGER, voice, frame, gain)

    /** Producer only. Returns `false`, dropping the event, when the queue is full. */
    fun pushStopAll(): Boolean = push(STOP_ALL, 0, 0, 1f)

    /** Consumer only. Copies the next event into [out]; `false` when the queue is empty. */
    fun popInto(out: Slot): Boolean {
        val index = tail.get()
        if (index == head.get()) {
            return false
        }
        out.kind = kind[index]
        out.voice = voice[index]
        out.frame = frame[index]
        out.gain = gain[index]
        tail.set((index + 1) % capacity)
        return true
    }

    private fun push(kind: Int, voice: Int, frame: Long, gain: Float): Boolean {
        val index = head.get()
        val next = (index + 1) % capacity
        if (next == tail.get()) {
            return false
        }
        this.kind[index] = kind
        this.voice[index] = voice
        this.frame[index] = frame
        this.gain[index] = gain
        head.set(next)
        return true
    }

    companion object {
        const val TRIGGER = 0
        const val STOP_ALL = 1
    }
}
