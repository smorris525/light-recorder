package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Compressor
import com.stephenmorris.recorder.audio.Voice
import com.stephenmorris.recorder.audio.VoiceEvent
import com.stephenmorris.recorder.audio.VoiceRenderer
import com.stephenmorris.recorder.core.Sound
import kotlin.math.abs

/**
 * A pattern take's level as it's played, for the lane: its hits played through the kit as the mix plays them (each
 * sound cutting off its last, chokes, the drum bus's compressor) and measured as [MixLevels] measures the mix (the
 * loudest of every [MixLevels.FRAMES_PER_PEAK] frames from 0:00), so the take is drawn the same height while it records
 * as once the mix is measured with it in. The lane keeps it, unchanged, until then, as it does an audio take's
 * [TakeLevels].
 *
 * While a take records, [play] carries one renderer on from where it got to, so each tick plays only the moment since
 * the last: hits only ever arrive after what's been scheduled. Any other change plays again from the first hit that
 * changed, from a little before it. Not thread-safe: one caller at a time.
 */
class HitLevels(
    /** The drum kit's voices, indexed as `Sound.entries`. */
    private val voices: List<Voice>,
    val sampleRate: Double,
) {
    /** How long the longest sound lasts, in seconds: no hit earlier than this before a time is still heard there. */
    private val longest = (voices.take(Sound.entries.size).maxOfOrNull { it.samples.size } ?: 0) / sampleRate
    private var played: List<Hit> = emptyList()

    /** The loudest sample in each [MixLevels.FRAMES_PER_PEAK] frames from 0:00. */
    private val peaks = mutableListOf<Float>()

    /** The renderer playing the hits, its clock's 0 at [origin] on the tape's. */
    private var renderer: VoiceRenderer? = null
    private var origin = 0

    /** The tape's frame the renderer has played up to. */
    private var rendered = 0

    /** The next hit for the renderer to start. */
    private var next = 0

    /** The first peak the renderer measures; those before it were kept from before it started. */
    private var firstPeak = 0
    private val buffer = FloatArray(BLOCK)

    /** The take's hits, in time order. Setting them plays every sound out to its end. */
    var hits: List<Hit>
        get() = played
        set(value) = play(value, Double.POSITIVE_INFINITY)

    /**
     * The level from [from] up to [to] seconds on the tape (the peak [from] is in, when the range is narrower), or
     * `null` where no hit is heard.
     */
    fun level(from: Double, to: Double): Float? {
        val size = MixLevels.FRAMES_PER_PEAK
        val first = (from * sampleRate).toInt() / size
        if (from < 0 || first >= peaks.size) {
            return null
        }
        val last = minOf(maxOf((to * sampleRate).toInt() / size, first + 1), peaks.size)
        val level = peaks.subList(first, last).max()
        return if (level > 0) level else null
    }

    /**
     * [hits], played up to [through] seconds on the tape, or to where the last sound ends if that's sooner. While a
     * take records, [through] is how far it's been scheduled: no hit will come before it.
     */
    fun play(hits: List<Hit>, through: Double) {
        val last = hits.lastOrNull()
        if (last == null) {
            played = emptyList()
            peaks.clear()
            renderer = null
            return
        }
        val same = played.zip(hits).takeWhile { (old, new) -> old == new }.size
        var changed: Double? = null
        if (same < played.size) {
            // A hit changed, or went.
            changed = if (same < hits.size) minOf(played[same].time, hits[same].time) else played[same].time
        } else if (same < hits.size && (!isPlaying || frame(hits[same].time) < rendered)) {
            // A hit came where the renderer has already played, or there's no renderer to carry on with.
            changed = hits[same].time
        } else if (!isPlaying) {
            changed = rendered / sampleRate
        }
        played = hits
        changed?.let(::start)
        val end = frame(last.time + longest).toInt() + 1
        val target = if (through.isFinite()) minOf(frame(through).toInt(), end) else end
        if (target > rendered) {
            render(target)
        }
    }

    /** Whether the renderer is where it was left, so it can carry on. */
    private val isPlaying: Boolean
        get() = renderer?.let { it.renderedFrames + origin == rendered.toLong() } ?: false

    /**
     * A new renderer, to play again from [time]: from the hits still heard a little before it, so each sound is as it
     * was there, and the compressor has had four of its release times to settle to where it was.
     */
    private fun start(time: Double) {
        val size = MixLevels.FRAMES_PER_PEAK
        val changedPeak = maxOf(0, frame(time).toInt()) / size
        firstPeak = minOf(peaks.size, changedPeak)
        peaks.subList(firstPeak, peaks.size).clear()
        next = firstHeard(time - Compressor.RELEASE * 4)
        val leadInPeak = if (next < played.size) maxOf(0, frame(played[next].time).toInt()) / size else changedPeak
        origin = minOf(changedPeak, leadInPeak) * size
        rendered = origin
        renderer = VoiceRenderer(voices, gain = 1f, compressor = Compressor(sampleRate))
    }

    /** Plays on from [rendered] to [target], measuring the peaks from [firstPeak] on. */
    private fun render(target: Int) {
        val renderer = renderer ?: return
        val size = MixLevels.FRAMES_PER_PEAK
        while (rendered < target) {
            val count = minOf(BLOCK, target - rendered)
            while (next < played.size && frame(played[next].time) < rendered + count) {
                val hit = played[next]
                renderer.send(VoiceEvent.Trigger(hit.sound.ordinal, frame(hit.time) - origin, hit.level.gain))
                next += 1
            }
            renderer.render(count, buffer)
            var offset = 0
            while (offset < count) {
                val index = (rendered + offset) / size
                val length = minOf(size - (rendered + offset) % size, count - offset)
                var peak = 0f
                for (sample in offset until offset + length) {
                    peak = maxOf(peak, abs(buffer[sample]))
                }
                if (index >= firstPeak) {
                    while (peaks.size <= index) {
                        peaks += 0f
                    }
                    peaks[index] = maxOf(peaks[index], peak)
                }
                offset += length
            }
            rendered += count
        }
    }

    /** Where [time] falls on the render clock, as the tape's scheduler places a hit. */
    private fun frame(time: Double): Long = roundHalfAway(time * sampleRate).toLong()

    /** The first hit that may still be heard at [time]. */
    private fun firstHeard(time: Double): Int {
        val earliest = time - longest
        var low = 0
        var high = played.size
        while (low < high) {
            val middle = (low + high) / 2
            if (played[middle].time < earliest) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }

    private companion object {
        /** Frames rendered at a time. */
        const val BLOCK = 4_096
    }
}
