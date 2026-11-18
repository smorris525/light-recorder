package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Compressor
import com.stephenmorris.recorder.audio.Limiter
import com.stephenmorris.recorder.audio.Voice
import com.stephenmorris.recorder.audio.VoiceEvent
import com.stephenmorris.recorder.audio.VoiceRenderer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.abs

/** Something for the mix to do, sent from the main thread. */
sealed interface MixEvent {
    data class PlayClip(val slot: Int, val clip: Int, val sourceFrame: Int, val startFrame: Long, val frameCount: Int) :
        MixEvent

    data object StopClips : MixEvent

    data class SetTrack(val slot: Int, val gainLeft: Float, val gainRight: Float) : MixEvent

    /** A drum bus's vol and pan, applied as its drums go into the left and right channels. */
    data class SetDrums(val bus: Int, val gainLeft: Float, val gainRight: Float) : MixEvent

    /**
     * The frames a take is recording over on [slot]'s track, from [fromFrame] up to [toFrame]: its clips are silent
     * there. Replaces the slot's last range; [toFrame] may be [Long.MAX_VALUE] until the take's end is known.
     */
    data class SilenceTrack(val slot: Int, val fromFrame: Long, val toFrame: Long) : MixEvent
}

/**
 * The whole mix, one render cycle at a time: the drums, each track's clips, and the limiters. Output only: the mic
 * arrives on its own thread (spec §4.2), so nothing here reads input. Frames are counted on one clock.
 *
 * Drums play on **buses**, each with its own voices, compressor and gains: one per track, so a track's hits follow its
 * volume and pan, and the unity bus, never sent gains, for drums that belong to no track (auditions, pads and clicks).
 * Every bus renders every cycle, so their clocks stay in step.
 *
 * [send], [trigger] and [stopVoices] belong to the main thread; [render] to the audio thread. [render] takes no locks
 * and allocates nothing. [clips] can be shared with another renderer: one measuring the mix offline reads the
 * recordings playback does.
 */
class MixRenderer(
    sampleRate: Double,
    voices: List<Voice>,
    private val maxFrames: Int,
    val clips: ClipStore = ClipStore(),
    /** Whether the limiters run. Off, the mix comes out as summed, over full scale where it would clip: for measuring. */
    private val limits: Boolean = true,
) : MixSink {
    private val buses = List(DRUM_BUSES) {
        VoiceRenderer(voices, gain = 1f, compressor = Compressor(sampleRate))
    }
    private val queue = MixEventQueue(EVENT_CAPACITY)

    /** Each track's loudest output sample since it was last asked, as float bits. */
    private val trackPeakBits = AtomicIntegerArray(SLOTS)

    // Owned by the audio thread.
    private val slot = MixEventQueue.Slot()
    private val drums = FloatArray(maxFrames)
    private val busLeft = FloatArray(DRUM_BUSES) { 1f }
    private val busRight = FloatArray(DRUM_BUSES) { 1f }
    private val slotBuffers = Array(SLOTS) { FloatArray(maxFrames) }
    private val slotLeft = FloatArray(SLOTS) { 1f }
    private val slotRight = FloatArray(SLOTS) { 1f }
    private val slotUsed = BooleanArray(SLOTS)
    private val silenceFrom = LongArray(SLOTS)
    private val silenceTo = LongArray(SLOTS)
    private val playerSlot = IntArray(MAX_PLAYERS)
    private val playerClip = IntArray(MAX_PLAYERS)
    private val playerSource = IntArray(MAX_PLAYERS)
    private val playerStart = LongArray(MAX_PLAYERS)
    private val playerFrames = IntArray(MAX_PLAYERS)
    private var playerCount = 0
    private val limiterLeft = Limiter(sampleRate = sampleRate)
    private val limiterRight = Limiter(sampleRate = sampleRate)

    /** Frames rendered so far: the clock every event's frame refers to. */
    override val renderedFrames: Long
        get() = buses[0].renderedFrames

    /** Main thread only. Returns `false` if the event was dropped because the queue is full. */
    override fun send(event: MixEvent): Boolean = queue.push(event)

    /** Plays [voice] on drum bus [bus]; a bus out of range plays nothing. */
    override fun trigger(bus: Int, voice: Int, frame: Long, gain: Float) {
        if (bus in 0 until DRUM_BUSES) {
            buses[bus].send(VoiceEvent.Trigger(voice, frame, gain))
        }
    }

    override fun stopVoices() {
        for (bus in buses) {
            bus.send(VoiceEvent.StopAll)
        }
    }

    /**
     * The loudest sample the track in [slot] has played, its recordings and its hits at its gains, since this was last
     * asked: what its playback meter shows.
     */
    override fun takeTrackPeak(slot: Int): Float {
        if (slot !in 0 until SLOTS) {
            return 0f
        }
        return Float.fromBits(trackPeakBits.getAndSet(slot, 0))
    }

    /** Audio thread only. Fills `left` and `right` up to [frameCount] (at most `maxFrames`). */
    fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        // Pairs, not one four-argument minOf: that's the vararg overload, which allocates an array.
        val count = minOf(minOf(frameCount, maxFrames), minOf(left.size, right.size))
        val start = renderedFrames
        drainEvents()

        left.fill(0f, 0, count)
        right.fill(0f, 0, count)
        for (bus in 0 until DRUM_BUSES) {
            buses[bus].render(count, drums)
            val gainLeft = busLeft[bus]
            val gainRight = busRight[bus]
            var peak = 0f
            for (index in 0 until count) {
                left[index] += drums[index] * gainLeft
                right[index] += drums[index] * gainRight
                peak = maxOf(peak, abs(drums[index]))
            }
            // The unity bus is no track's.
            if (bus < SLOTS) {
                reportTrackPeak(bus, peak * maxOf(abs(gainLeft), abs(gainRight)))
            }
        }

        renderPlayers(start, count)
        mixSlots(count, left, right)

        if (limits) {
            limiterLeft.process(left, count)
            limiterRight.process(right, count)
        }
    }

    private fun drainEvents() {
        while (queue.popInto(slot)) {
            when (slot.kind) {
                MixEventQueue.PLAY_CLIP -> if (playerCount < MAX_PLAYERS && slot.slot in 0 until SLOTS) {
                    playerSlot[playerCount] = slot.slot
                    playerClip[playerCount] = slot.clip
                    playerSource[playerCount] = slot.sourceFrame
                    playerStart[playerCount] = slot.fromFrame
                    playerFrames[playerCount] = slot.frameCount
                    playerCount += 1
                }
                MixEventQueue.STOP_CLIPS -> playerCount = 0
                MixEventQueue.SET_TRACK -> if (slot.slot in 0 until SLOTS) {
                    slotLeft[slot.slot] = slot.gainLeft
                    slotRight[slot.slot] = slot.gainRight
                }
                MixEventQueue.SET_DRUMS -> if (slot.slot in 0 until DRUM_BUSES) {
                    busLeft[slot.slot] = slot.gainLeft
                    busRight[slot.slot] = slot.gainRight
                }
                MixEventQueue.SILENCE_TRACK -> if (slot.slot in 0 until SLOTS) {
                    silenceFrom[slot.slot] = slot.fromFrame
                    silenceTo[slot.slot] = slot.toFrame
                }
            }
        }
    }

    private fun renderPlayers(start: Long, count: Int) {
        slotUsed.fill(false)
        val end = start + count
        var index = 0
        while (index < playerCount) {
            val playerEnd = playerStart[index] + playerFrames[index]
            if (playerStart[index] < end && playerEnd > start) {
                play(index, maxOf(start, playerStart[index]), minOf(end, playerEnd), start, count)
            }
            if (playerEnd <= end) {
                playerCount -= 1
                playerSlot[index] = playerSlot[playerCount]
                playerClip[index] = playerClip[playerCount]
                playerSource[index] = playerSource[playerCount]
                playerStart[index] = playerStart[playerCount]
                playerFrames[index] = playerFrames[playerCount]
            } else {
                index += 1
            }
        }
    }

    private fun play(player: Int, from: Long, to: Long, blockStart: Long, count: Int) {
        val samples = clips.samples(playerClip[player]) ?: return
        val slot = playerSlot[player]
        val buffer = slotBuffers[slot]
        if (!slotUsed[slot]) {
            slotUsed[slot] = true
            buffer.fill(0f, 0, count)
        }
        var frame = from
        while (frame < to) {
            val source = playerSource[player] + (frame - playerStart[player])
            val isRecordedOver = frame >= silenceFrom[slot] && frame < silenceTo[slot]
            if (source >= 0 && source < samples.size && !isRecordedOver) {
                buffer[(frame - blockStart).toInt()] += samples[source.toInt()]
            }
            frame += 1
        }
    }

    private fun mixSlots(count: Int, left: FloatArray, right: FloatArray) {
        for (slot in 0 until SLOTS) {
            if (!slotUsed[slot]) {
                continue
            }
            val buffer = slotBuffers[slot]
            val gainLeft = slotLeft[slot]
            val gainRight = slotRight[slot]
            var peak = 0f
            for (index in 0 until count) {
                left[index] += buffer[index] * gainLeft
                right[index] += buffer[index] * gainRight
                peak = maxOf(peak, abs(buffer[index]))
            }
            reportTrackPeak(slot, peak * maxOf(abs(gainLeft), abs(gainRight)))
        }
    }

    private fun reportTrackPeak(slot: Int, peak: Float) {
        // A non-negative float's bits order as the float does, so the larger bits are the louder peak.
        val bits = peak.toRawBits()
        while (true) {
            val current = trackPeakBits.get(slot)
            if (current >= bits || trackPeakBits.compareAndSet(slot, current, bits)) {
                return
            }
        }
    }

    companion object {
        const val SLOTS = Song.TRACK_COUNT

        /** A drum bus for each track, then the unity bus. */
        const val DRUM_BUSES = Song.TRACK_COUNT + 1
        const val UNITY_BUS = Song.TRACK_COUNT

        private const val MAX_PLAYERS = 256
        private const val EVENT_CAPACITY = 1_024
    }
}

/**
 * A fixed-size queue of [MixEvent]s for one producer thread and one consumer thread, with no locks and no allocation
 * on the consumer's side. Events live in parallel primitive arrays. Holds up to `capacity - 1` events.
 */
private class MixEventQueue(private val capacity: Int) {
    /** A reusable place for [popInto] to put one event. */
    class Slot {
        var kind = 0
        var slot = 0
        var clip = 0
        var sourceFrame = 0
        var fromFrame = 0L
        var toFrame = 0L
        var frameCount = 0
        var gainLeft = 1f
        var gainRight = 1f
    }

    private val kind = IntArray(capacity)
    private val slot = IntArray(capacity)
    private val clip = IntArray(capacity)
    private val sourceFrame = IntArray(capacity)
    private val fromFrame = LongArray(capacity)
    private val toFrame = LongArray(capacity)
    private val frameCount = IntArray(capacity)
    private val gainLeft = FloatArray(capacity)
    private val gainRight = FloatArray(capacity)
    private val head = AtomicInteger(0)
    private val tail = AtomicInteger(0)

    /** Producer only. Returns `false`, dropping the event, when the queue is full. */
    fun push(event: MixEvent): Boolean {
        val index = head.get()
        val next = (index + 1) % capacity
        if (next == tail.get()) {
            return false
        }
        when (event) {
            is MixEvent.PlayClip -> {
                kind[index] = PLAY_CLIP
                slot[index] = event.slot
                clip[index] = event.clip
                sourceFrame[index] = event.sourceFrame
                fromFrame[index] = event.startFrame
                frameCount[index] = event.frameCount
            }
            MixEvent.StopClips -> kind[index] = STOP_CLIPS
            is MixEvent.SetTrack -> {
                kind[index] = SET_TRACK
                slot[index] = event.slot
                gainLeft[index] = event.gainLeft
                gainRight[index] = event.gainRight
            }
            is MixEvent.SetDrums -> {
                kind[index] = SET_DRUMS
                slot[index] = event.bus
                gainLeft[index] = event.gainLeft
                gainRight[index] = event.gainRight
            }
            is MixEvent.SilenceTrack -> {
                kind[index] = SILENCE_TRACK
                slot[index] = event.slot
                fromFrame[index] = event.fromFrame
                toFrame[index] = event.toFrame
            }
        }
        head.set(next)
        return true
    }

    /** Consumer only. Copies the next event into [out]; `false` when the queue is empty. */
    fun popInto(out: Slot): Boolean {
        val index = tail.get()
        if (index == head.get()) {
            return false
        }
        out.kind = kind[index]
        out.slot = slot[index]
        out.clip = clip[index]
        out.sourceFrame = sourceFrame[index]
        out.fromFrame = fromFrame[index]
        out.toFrame = toFrame[index]
        out.frameCount = frameCount[index]
        out.gainLeft = gainLeft[index]
        out.gainRight = gainRight[index]
        tail.set((index + 1) % capacity)
        return true
    }

    companion object {
        const val PLAY_CLIP = 0
        const val STOP_CLIPS = 1
        const val SET_TRACK = 2
        const val SET_DRUMS = 3
        const val SILENCE_TRACK = 4
    }
}
