package com.stephenmorris.recorder.tape

import java.util.UUID
import kotlin.math.floor

/** Which tracks are armed to record: one Mic/Line track and one Drums track at most. */
data class Arming(val audio: UUID? = null, val drums: UUID? = null) {
    val isEmpty: Boolean
        get() = audio == null && drums == null

    fun isArmed(id: UUID): Boolean = audio == id || drums == id

    /** With [track] armed, or disarmed if it is armed. Arming a Mic/Line track disarms the other one. */
    fun toggling(track: Track): Arming {
        if (isArmed(track.id)) {
            return removing(track.id)
        }
        return when (track.input) {
            TrackInput.DRUMS -> copy(drums = track.id)
            TrackInput.MIC -> copy(audio = track.id)
        }
    }

    /** With the track with [id] disarmed, as when it is deleted. */
    fun removing(id: UUID): Arming = Arming(
        audio = audio.takeUnless { it == id },
        drums = drums.takeUnless { it == id },
    )
}

/**
 * Where playing and recording start and end. The in and out points are for recording only: ● punches in at the in
 * point and out at the out point, and ▶ plays from the playhead, as a tape deck's play does.
 */
object Punch {
    /** How much tape plays before a take starts, in seconds. */
    const val LEAD_IN = 3.0

    /** The tape to play before a take, from [tapeFrom], and the [silence] ahead of it. Together they last [LEAD_IN]. */
    data class LeadIn(val tapeFrom: Double, val silence: Double)

    /**
     * Where playing starts: the playhead, or the beginning when the playhead is at the end. The points don't come
     * into it.
     */
    fun playStart(song: Song, playhead: Double): Double = if (playhead >= song.length) 0.0 else playhead

    /**
     * Where a take starts: the in point, else `null` while playing, which punches in at once at the time heard, else
     * where playing would start.
     */
    fun recordStart(song: Song, playhead: Double, isPlaying: Boolean): Double? {
        song.inPoint?.let { return it }
        return if (isPlaying) null else playStart(song, playhead)
    }

    /** Where a take that started at [start] ends: the out point, if it's after [start]. */
    fun recordEnd(song: Song, start: Double): Double? = song.outPoint?.takeIf { it > start }

    /**
     * The tape to play before a take at [start] and the silence ahead of it, when the tape is too short to fill the
     * lead-in.
     */
    fun leadIn(start: Double): LeadIn {
        val tapeFrom = maxOf(0.0, start - LEAD_IN)
        return LeadIn(tapeFrom, LEAD_IN - (start - tapeFrom))
    }

    /**
     * Whether playback at [time], in a pass that started at [passStart], has run past the song's end and should stop
     * there, as when the song got shorter while playing. A pass that started at or beyond the end, from a take's in
     * point out there, isn't stopped by it.
     */
    fun hasRunPastEnd(song: Song, passStart: Double, time: Double): Boolean =
        passStart < song.length && time >= song.length
}

/** Times as text, cut to the unit shown rather than rounded. */
object TimeText {
    /** The transport's readout, to the hundredth: `0:12.73`, `12:03.40` from ten minutes, `1:02:03.40` from an hour. */
    fun readout(seconds: Double): String {
        val hundredths = hundredths(seconds)
        val total = hundredths / 100
        val hours = total / 3600
        val minutes = total / 60 % 60
        val rest = total % 60
        val tail = "${two(rest)}.${two(hundredths % 100)}"
        return if (hours > 0) "$hours:${two(minutes)}:$tail" else "$minutes:$tail"
    }

    /** An in or out point: minutes, seconds and hundredths, `01:21.96`. */
    fun point(seconds: Double): String {
        val hundredths = hundredths(seconds)
        return "${two(hundredths / 6000)}:${two(hundredths / 100 % 60)}.${two(hundredths % 100)}"
    }

    private fun two(value: Long): String = value.toString().padStart(2, '0')

    /**
     * Whole hundredths of a second, rounded down, with a hair's tolerance so a time scrubbed to 12.73 (which a
     * `Double` holds as 12.7299…) reads as 12.73.
     */
    private fun hundredths(seconds: Double): Long = floor(maxOf(seconds, 0.0) * 100 + 1e-6).toLong()
}
