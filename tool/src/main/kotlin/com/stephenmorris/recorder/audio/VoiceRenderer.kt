package com.stephenmorris.recorder.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Something for the mixer to do, sent from the main thread. */
sealed interface VoiceEvent {
    /** Start [voice] at [frame] on the render clock, at [gain]. A frame already rendered starts at the next render. */
    data class Trigger(val voice: Int, val frame: Long, val gain: Float = 1f) : VoiceEvent

    /** Fade out every voice and drop every trigger sent before this event. */
    data object StopAll : VoiceEvent
}

/**
 * Mixes monophonic sample voices into a mono buffer on the audio thread, then through the master compressor and the
 * limiter, where there are ones. Restarting a voice, or choking it, fades the old sound out over `fadeFrames` instead
 * of cutting it, so there are no clicks; the fading sound keeps the gain it started with.
 *
 * [send] must be called from one thread (the main thread); [render] from the audio thread. [render] takes no locks
 * and allocates nothing.
 */
class VoiceRenderer(
    voices: List<Voice>,
    fadeFrames: Int = 128,
    private val gain: Float = 0.8f,
    private val compressor: Compressor? = null,
    private val limiter: Limiter? = null,
) {
    private val samples: Array<FloatArray> = Array(voices.size) { voices[it].samples }
    private val chokes: Array<IntArray> = Array(voices.size) { voices[it].chokes }
    private val voiceCount = voices.size
    private val fadeFrames = maxOf(1, fadeFrames)
    private val queue = VoiceEventQueue(PENDING_CAPACITY)
    private val clock = AtomicLong(0)

    /** Set when a stop couldn't be queued; [render] stops at once, and sends wait until it has. */
    private val overflowStop = AtomicBoolean(false)

    // Owned by the audio thread.
    private val slot = VoiceEventQueue.Slot()
    private val pendingVoice = IntArray(PENDING_CAPACITY)
    private val pendingFrame = LongArray(PENDING_CAPACITY)
    private val pendingGain = FloatArray(PENDING_CAPACITY)
    private var pendingCount = 0

    /** The read position of each voice's current sound, or -1 when silent. */
    private val position = IntArray(voiceCount) { -1 }

    /** The gain of each voice's current sound. */
    private val voiceGain = FloatArray(voiceCount) { 1f }

    // Two fade slots per voice (`voice * FADE_SLOTS + slot`), so a sound restarted while its last fade is still running
    // doesn't cut that fade off.
    private val fadePosition = IntArray(voiceCount * FADE_SLOTS)
    private val fadeRemaining = IntArray(voiceCount * FADE_SLOTS)
    private val fadeGain = FloatArray(voiceCount * FADE_SLOTS) { 1f }

    /** Frames rendered so far: the render clock that trigger frames refer to. */
    val renderedFrames: Long
        get() = clock.get()

    /**
     * Main thread only. Returns `false` if the event was dropped. A stop is never dropped: if the queue is full, the
     * next [render] stops everything instead, and triggers sent before it has are dropped.
     */
    fun send(event: VoiceEvent): Boolean = when (event) {
        is VoiceEvent.Trigger -> !overflowStop.get() && queue.pushTrigger(event.voice, event.frame, event.gain)
        VoiceEvent.StopAll -> queue.pushStopAll() || true.also { overflowStop.set(true) }
    }

    /** Audio thread only. Fills `output[0 until frameCount]`. */
    fun render(frameCount: Int, output: FloatArray) {
        val start = clock.get()
        val end = start + frameCount
        if (overflowStop.get()) {
            // Everything queued was sent before the stop.
            while (queue.popInto(slot)) Unit
            stopAll()
            overflowStop.set(false)
        }
        drainQueue()
        output.fill(0f, 0, frameCount)

        var cursor = 0
        while (pendingCount > 0 && pendingFrame[0] < end) {
            val voice = pendingVoice[0]
            val frame = pendingFrame[0]
            val voiceGain = pendingGain[0]
            removeFirstPending()
            val offset = maxOf(cursor, maxOf(0L, frame - start).toInt())
            mix(output, cursor, offset)
            cursor = offset
            startVoice(voice, voiceGain)
        }
        mix(output, cursor, frameCount)
        compressor?.process(output, frameCount)
        limiter?.process(output, frameCount)
        clock.set(end)
    }

    private fun drainQueue() {
        while (queue.popInto(slot)) {
            if (slot.kind == VoiceEventQueue.STOP_ALL) {
                stopAll()
            } else if (slot.voice in 0 until voiceCount) {
                insertPending(slot.voice, slot.frame, slot.gain)
            }
        }
    }

    private fun stopAll() {
        pendingCount = 0
        for (voice in 0 until voiceCount) {
            fadeOut(voice)
        }
    }

    /** Keeps the pending triggers sorted by frame; equal frames stay in the order they were sent. */
    private fun insertPending(voice: Int, frame: Long, gain: Float) {
        if (pendingCount >= PENDING_CAPACITY) {
            return
        }
        var index = pendingCount
        while (index > 0 && pendingFrame[index - 1] > frame) {
            pendingVoice[index] = pendingVoice[index - 1]
            pendingFrame[index] = pendingFrame[index - 1]
            pendingGain[index] = pendingGain[index - 1]
            index -= 1
        }
        pendingVoice[index] = voice
        pendingFrame[index] = frame
        pendingGain[index] = gain
        pendingCount += 1
    }

    private fun removeFirstPending() {
        pendingCount -= 1
        System.arraycopy(pendingVoice, 1, pendingVoice, 0, pendingCount)
        System.arraycopy(pendingFrame, 1, pendingFrame, 0, pendingCount)
        System.arraycopy(pendingGain, 1, pendingGain, 0, pendingCount)
    }

    private fun startVoice(voice: Int, gain: Float) {
        for (choked in chokes[voice]) {
            if (choked in 0 until voiceCount) {
                fadeOut(choked)
            }
        }
        fadeOut(voice)
        position[voice] = 0
        voiceGain[voice] = gain
    }

    /** Moves the voice's sound into a fade slot: an idle one, or else the quieter of the two. */
    private fun fadeOut(voice: Int) {
        if (position[voice] < 0) {
            return
        }
        val first = voice * FADE_SLOTS
        var slot = first
        for (candidate in first until first + FADE_SLOTS) {
            if (fadeRemaining[candidate] < fadeRemaining[slot]) {
                slot = candidate
            }
        }
        fadePosition[slot] = position[voice]
        fadeRemaining[slot] = fadeFrames
        fadeGain[slot] = voiceGain[voice]
        position[voice] = -1
    }

    private fun mix(output: FloatArray, start: Int, end: Int) {
        if (end <= start) {
            return
        }
        for (voice in 0 until voiceCount) {
            val sound = samples[voice]
            if (position[voice] >= 0) {
                val level = gain * voiceGain[voice]
                var read = position[voice]
                var write = start
                while (write < end && read < sound.size) {
                    output[write] += sound[read] * level
                    read += 1
                    write += 1
                }
                position[voice] = if (read < sound.size) read else -1
            }
            for (slot in voice * FADE_SLOTS until (voice + 1) * FADE_SLOTS) {
                if (fadeRemaining[slot] <= 0) continue
                var read = fadePosition[slot]
                var remaining = fadeRemaining[slot]
                val level = gain * fadeGain[slot]
                var write = start
                while (write < end && remaining > 0 && read < sound.size) {
                    output[write] += sound[read] * level * remaining / fadeFrames
                    read += 1
                    remaining -= 1
                    write += 1
                }
                fadePosition[slot] = read
                fadeRemaining[slot] = if (read < sound.size) remaining else 0
            }
        }
    }

    companion object {
        const val PENDING_CAPACITY = 1_024
        private const val FADE_SLOTS = 2
    }
}
