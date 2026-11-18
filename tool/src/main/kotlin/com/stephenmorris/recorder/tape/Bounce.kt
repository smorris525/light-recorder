package com.stephenmorris.recorder.tape

import java.util.UUID
import kotlin.math.abs

/** Bouncing sums several tracks into one, at their volumes but not their pans. */
object Bounce {
    /** The picked tracks, as a phrase: "Track 1", "Tracks 1 and 2", "Tracks 1, 2, and 3". */
    fun summary(numbers: List<Int>): String {
        val sorted = numbers.sorted().map { it.toString() }
        return when (sorted.size) {
            0 -> "None"
            1 -> "Track ${sorted[0]}"
            2 -> "Tracks ${sorted[0]} and ${sorted[1]}"
            else -> "Tracks ${sorted.dropLast(1).joinToString(", ")}, and ${sorted.last()}"
        }
    }

    /**
     * [into] with [stem] × [gain] added. Grows the mix with silence to fit, as a new array; otherwise adds in place and
     * returns [into]. Never clamps: the bounce keeps what the mix would clip.
     */
    fun accumulate(stem: FloatArray, gain: Float, into: FloatArray): FloatArray {
        val mix = if (stem.size > into.size) into.copyOf(stem.size) else into
        for (index in stem.indices) {
            mix[index] += stem[index] * gain
        }
        return mix
    }

    /**
     * [samples] cut after the last one at or above [below] in magnitude (−120 dB by default): the bounce rendered over
     * the blank tape, without the silence after its last sound. All quiet, it's empty. Returns [samples] itself when
     * there's nothing to cut, so a song-length bounce is copied only when it's trimmed.
     */
    fun trimmedSilence(samples: FloatArray, below: Float = 1e-6f): FloatArray {
        val end = samples.indexOfLast { abs(it) >= below } + 1
        return if (end == samples.size) samples else samples.copyOf(end)
    }
}

/** Whether anything is recorded to bounce. */
val Song.canBounce: Boolean
    get() = contentEnd > 0

/**
 * [sources] (track numbers from 1) replaced by one recording, [clip], on [destination]: the destination plays it from
 * the start at 0 dB, centred; the other sources are emptied but keep their settings. Numbers outside the tracks are
 * ignored.
 */
fun Song.applyingBounce(sources: Set<Int>, destination: Int, clip: UUID, duration: Double): Song {
    val list = tracks.toMutableList()
    for (number in sources) {
        if (number - 1 in list.indices && number != destination) {
            list[number - 1] = list[number - 1].copy(spans = emptyList(), hits = emptyList())
        }
    }
    if (destination - 1 in list.indices) {
        list[destination - 1] = list[destination - 1].copy(
            spans = listOf(Span(0.0, duration, 0.0, clip)),
            hits = emptyList(),
            volume = 0,
            pan = 0,
        )
    }
    return copy(tracks = list)
}
