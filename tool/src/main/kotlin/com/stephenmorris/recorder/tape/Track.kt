package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import java.util.UUID
import kotlin.math.abs

/** One of a song's four tracks: what it records from, how it sits in the mix, and what's recorded on it. */
data class Track(
    val id: UUID = UUID.randomUUID(),
    /** From 1 to [Song.TRACK_COUNT]. */
    val number: Int,
    val input: TrackInput,
    /** Which of an interface's inputs a Mic/Line track records from, from 1. */
    val channel: Int = 1,
    /** In dB, within [VOLUMES], or [SILENT]. */
    val volume: Int = 0,
    /** −50…50, in steps of 5. */
    val pan: Int = 0,
    /** Recorded audio, in seconds on the tape. */
    val spans: List<Span> = emptyList(),
    /** Recorded drum hits, in time order. */
    val hits: List<Hit> = emptyList(),
) {
    val name: String
        get() = "Track $number"

    val volumeText: String
        get() {
            if (volume <= SILENT) {
                return "−∞"
            }
            val sign = if (volume > 0) "+" else if (volume < 0) "−" else ""
            return "$sign${abs(volume)} dB"
        }

    val panText: String
        get() = if (pan == 0) "C" else (if (pan < 0) "L" else "R") + abs(pan)

    /**
     * Records [span], replacing only what it covers: audio and hits alike, so a take on the track clears what's under
     * it. An empty or reversed span changes nothing.
     */
    fun recording(span: Span): Track = if (span.end <= span.start) this else copy(
        spans = Span.replacing(spans, span),
        hits = hits.filterNot { it.time >= span.start && it.time < span.end },
    )

    /**
     * Records [hits] over [over], dropping the hits already there and the audio under them. An empty or reversed range
     * changes nothing.
     */
    fun recording(hits: List<Hit>, over: OpenEndRange<Double>): Track = if (over.isEmpty()) this else copy(
        spans = Span.clearing(spans, over),
        hits = (this.hits.filterNot { it.time in over } + hits).sortedBy { it.time },
    )

    /** The latest the track has anything on the tape, 0 when empty. */
    val contentEnd: Double
        get() = maxOf(spans.maxOfOrNull { it.end } ?: 0.0, hits.maxOfOrNull { it.time } ?: 0.0)

    companion object {
        /** The value of [volume] that means silent. */
        const val SILENT = -60

        /** The volumes between silent and loudest, in dB. */
        val VOLUMES = -48..6
    }
}

/** A drum hit on the tape, in seconds. */
data class Hit(val time: Double, val sound: Sound, val level: Level)

/** A stretch of recorded audio on the tape, in seconds. [seed] picks the fake waveform. */
data class Span(
    val start: Double,
    val end: Double,
    val seed: Double,
    /** The recording this plays; `null` draws a fake waveform and plays nothing. */
    val clip: UUID? = null,
    /** Where in the recording [start] falls, in seconds. */
    val sourceTime: Double = 0.0,
) {
    companion object {
        /** [spans] with [new] recorded over them. What's left of a span past [new] starts later in its recording. */
        fun replacing(spans: List<Span>, new: Span): List<Span> = clearing(spans, new.start..<new.end) + new

        /**
         * [spans] with [range] cut out. What's left of a span past the cut starts later in its recording. An empty or
         * reversed range cuts nothing.
         */
        fun clearing(spans: List<Span>, range: OpenEndRange<Double>): List<Span> = if (range.isEmpty()) spans else buildList {
            val from = range.start
            val to = range.endExclusive
            for (span in spans) {
                if (span.end <= from || span.start >= to) {
                    add(span)
                    continue
                }
                if (span.start < from) {
                    add(span.copy(end = from))
                }
                if (span.end > to) {
                    add(span.copy(start = to, sourceTime = span.sourceTime + to - span.start))
                }
            }
        }
    }
}
