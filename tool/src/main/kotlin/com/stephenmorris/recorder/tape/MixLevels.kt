package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Voice
import java.util.UUID
import kotlin.math.abs

/**
 * The mix's level along the tape, summed before the limiters: every track, its recordings and its hits, at its volume
 * and pan, so it shows where the song is quiet, where it's loud and where it goes over full scale. What the lane draws.
 *
 * It's measured in two steps, so that moving a fader is quick. A **stem** is a track's audio before its volume and
 * pan: a track's recordings and its hits through the kit, rendered once and kept until what they're made from
 * changes. A track with no hits has no stem to render, so its recordings are read as they are. The **sum** adds every
 * stem at its track's volume and pan, which is all a fader move needs.
 */
class MixLevels(
    val sampleRate: Double,
    /** The loudest sample, of either channel, in each [FRAMES_PER_PEAK] frames from 0:00. */
    val peaks: FloatArray,
) {
    /**
     * The loudest level from [from] up to [to] seconds (the stretch [from] is in, when the range is narrower), or
     * `null` past what was measured.
     */
    fun level(from: Double, to: Double): Float? {
        val first = (from * sampleRate).toInt() / FRAMES_PER_PEAK
        if (from < 0 || first >= peaks.size) {
            return null
        }
        val last = minOf(maxOf((to * sampleRate).toInt() / FRAMES_PER_PEAK, first + 1), peaks.size)
        var level = peaks[first]
        for (index in first + 1 until last) {
            level = maxOf(level, peaks[index])
        }
        return level
    }

    override fun equals(other: Any?): Boolean =
        other is MixLevels && other.sampleRate == sampleRate && other.peaks.contentEquals(peaks)

    override fun hashCode(): Int = 31 * sampleRate.hashCode() + peaks.contentHashCode()

    /**
     * What of a song the mix's level depends on: its recordings and hits, and each track's volume and pan. Two songs
     * with equal keys measure the same, so a rename or a pattern edit doesn't measure again.
     */
    @ConsistentCopyVisibility
    data class Key private constructor(private val parts: List<Part>, private val length: Double) {
        private data class Part(
            val id: UUID,
            val spans: List<Span>,
            val hits: List<Hit>,
            val gainLeft: Float,
            val gainRight: Float,
        )

        constructor(song: Song) : this(
            song.tracks.map { track ->
                val gains = track.gains
                Part(track.id, track.spans, track.hits, gains.left, gains.right)
            },
            song.length,
        )
    }

    /** What a rendered stem is made from: when it changes, the stem is rendered again. Volume and pan aren't in it. */
    @ConsistentCopyVisibility
    data class StemKey private constructor(
        private val spans: List<Span>,
        private val hits: List<Hit>,
        /** The stem's length in frames. */
        val frames: Int,
        private val sampleRate: Double,
    ) {
        constructor(track: Track, songLength: Double, sampleRate: Double) : this(
            track.spans,
            track.hits,
            (songLength * sampleRate).toInt(),
            sampleRate,
        )
    }

    companion object {
        /** The level is kept as the loudest sample of every this many frames. */
        const val FRAMES_PER_PEAK = TakeLevels.FRAMES_PER_PEAK

        /** The loudest a sample can be without clipping; the limiters catch anything over it. */
        const val FULL_SCALE = 0.999f

        private const val STEM_BLOCK = 4_096
        private const val SUM_BLOCK = 1 shl 16

        /** Whether [level] is over full scale: where the limiters would be working. */
        fun clips(level: Float): Boolean = level >= FULL_SCALE

        /** Whether [track]'s stem has to be rendered (through the kit), rather than read from its recordings. */
        fun needsStem(track: Track): Boolean = track.hits.isNotEmpty()

        /**
         * [track]'s audio before its volume and pan, mono, from 0:00 for [songLength] seconds: played through a
         * renderer with no limiters, the track's recordings and hits alone at unity (its hits on drum bus 0).
         * [clipIndexes] places each recording in [clips]; [voices] are the drum kit's, indexed as `Sound.entries`.
         * Returns `null` once [isCancelled] says so, checked between blocks; [progress] is told, 0…1, after each block.
         */
        fun renderStem(
            track: Track,
            songLength: Double,
            clips: ClipStore,
            clipIndexes: Map<UUID, Int>,
            voices: List<Voice>,
            sampleRate: Double,
            isCancelled: () -> Boolean = { false },
            progress: (Double) -> Unit = {},
        ): FloatArray? {
            val mix = MixRenderer(sampleRate, voices, STEM_BLOCK, clips, limits = false)
            val slot = 0
            mix.send(MixEvent.SetDrums(bus = 0, gainLeft = 1f, gainRight = 0f))
            mix.send(MixEvent.SetTrack(slot = slot, gainLeft = 1f, gainRight = 0f))

            val alone = Song.new("").copy(tracks = listOf(track))
            val totalFrames = (songLength * sampleRate).toInt()
            val scheduler = TapeScheduler(sampleRate, time = 0.0, startFrame = 0)
            val bounds = PassBounds(listOf(songLength))
            val left = FloatArray(STEM_BLOCK)
            val right = FloatArray(STEM_BLOCK)

            val stem = FloatArray(totalFrames)
            var frame = 0
            while (frame < totalFrames) {
                if (isCancelled()) {
                    return null
                }
                val count = minOf(STEM_BLOCK, totalFrames - frame)
                val events = scheduler.advance(
                    (frame + STEM_BLOCK * 2).toLong(), frame.toLong(), alone, bounds, emptyMap(),
                )
                for (event in events) {
                    when (event) {
                        is ScheduledEvent.Drum -> mix.trigger(0, event.sound.ordinal, event.frame, event.level.gain)
                        is ScheduledEvent.Clip -> clipIndexes[event.clip]?.let { index ->
                            mix.send(
                                MixEvent.PlayClip(
                                    slot = slot,
                                    clip = index,
                                    sourceFrame = roundHalfAway(event.sourceTime * sampleRate).toInt(),
                                    startFrame = event.startFrame,
                                    frameCount = (event.endFrame - event.startFrame).toInt(),
                                ),
                            )
                        }
                        is ScheduledEvent.Pass, is ScheduledEvent.Click -> Unit
                    }
                }
                mix.render(left, right, count)
                System.arraycopy(left, 0, stem, frame, count)
                frame += count
                progress(frame.toDouble() / totalFrames)
            }
            return stem
        }

        /**
         * Adds every track of [song] at its volume and pan, from 0:00 to its end, and measures the sum. A track in
         * [stems] is read from there; one that isn't (a track with no hits) from its recordings in [clipSamples].
         * Returns `null` once [isCancelled] says so, checked between blocks.
         */
        fun sum(
            song: Song,
            stems: Map<UUID, FloatArray>,
            clipSamples: Map<UUID, FloatArray>,
            sampleRate: Double,
            isCancelled: () -> Boolean = { false },
        ): MixLevels? {
            val totalFrames = (song.length * sampleRate).toInt()
            val left = FloatArray(SUM_BLOCK)
            val right = FloatArray(SUM_BLOCK)
            val peaks = FloatArray((totalFrames + FRAMES_PER_PEAK - 1) / FRAMES_PER_PEAK)

            var start = 0
            while (start < totalFrames) {
                if (isCancelled()) {
                    return null
                }
                val count = minOf(SUM_BLOCK, totalFrames - start)
                left.fill(0f)
                right.fill(0f)
                for (track in song.tracks) {
                    val gains = track.gains
                    if (gains.left <= 0 && gains.right <= 0) {
                        continue
                    }
                    val stem = stems[track.id]
                    if (stem != null) {
                        add(stem, start, 0, minOf(count, maxOf(0, stem.size - start)), gains, left, right)
                        continue
                    }
                    for (span in track.spans) {
                        val samples = span.clip?.let { clipSamples[it] } ?: continue
                        val spanStart = roundHalfAway(span.start * sampleRate).toInt()
                        val spanEnd = roundHalfAway(span.end * sampleRate).toInt()
                        val from = maxOf(start, spanStart)
                        val to = minOf(start + count, spanEnd)
                        if (to <= from) {
                            continue
                        }
                        val source = roundHalfAway(span.sourceTime * sampleRate).toInt() + from - spanStart
                        add(samples, source, from - start, minOf(to - from, maxOf(0, samples.size - source)), gains, left, right)
                    }
                }
                // The louder channel at each frame, then the loudest of each FRAMES_PER_PEAK.
                for (offset in 0 until count) {
                    val index = (start + offset) / FRAMES_PER_PEAK
                    peaks[index] = maxOf(peaks[index], abs(left[offset]), abs(right[offset]))
                }
                start += count
            }
            return MixLevels(sampleRate, peaks)
        }

        /** Adds [count] of [source] from [from] into the block from [to], scaled into each channel. */
        private fun add(
            source: FloatArray,
            from: Int,
            to: Int,
            count: Int,
            gains: Gains,
            left: FloatArray,
            right: FloatArray,
        ) {
            if (count <= 0 || from < 0) {
                return
            }
            for (index in 0 until count) {
                val sample = source[from + index]
                left[to + index] += sample * gains.left
                right[to + index] += sample * gains.right
            }
        }

        /**
         * Renders the stems of the tracks that need one and sums them with the others' recordings: the whole
         * measurement from nothing kept. A track without hits is read from [clips], so its audio isn't held twice.
         * [onStem] is told each track whose stem is rendered.
         */
        fun render(
            song: Song,
            clips: ClipStore,
            clipIndexes: Map<UUID, Int>,
            voices: List<Voice>,
            sampleRate: Double,
            isCancelled: () -> Boolean = { false },
            onStem: (UUID) -> Unit = {},
        ): MixLevels? {
            val stems = mutableMapOf<UUID, FloatArray>()
            val clipSamples = mutableMapOf<UUID, FloatArray>()
            for (track in song.tracks) {
                if (!needsStem(track)) {
                    for (span in track.spans) {
                        val clip = span.clip ?: continue
                        clipIndexes[clip]?.let(clips::samples)?.let { clipSamples[clip] = it }
                    }
                    continue
                }
                onStem(track.id)
                stems[track.id] = renderStem(
                    track, song.length, clips, clipIndexes, voices, sampleRate, isCancelled,
                ) ?: return null
            }
            return sum(song, stems, clipSamples, sampleRate, isCancelled)
        }
    }
}
