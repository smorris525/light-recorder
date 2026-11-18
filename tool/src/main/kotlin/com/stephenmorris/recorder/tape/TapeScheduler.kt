package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import java.util.UUID

/** Something to place on the render clock. */
sealed interface ScheduledEvent {
    /** A hit of [track]'s, played at its volume and pan; `null` for drums that belong to no track (an audition). */
    data class Drum(val track: UUID?, val sound: Sound, val level: Level, val frame: Long) : ScheduledEvent

    /** The click on a beat, beat 1 accented: the pattern screens' metronome, never on the tape. */
    data class Click(val kind: ClickKind, val frame: Long) : ScheduledEvent

    /** Plays [clip] from [sourceTime] seconds in, from [startFrame] until [endFrame]. */
    data class Clip(
        val track: UUID,
        val clip: UUID,
        val sourceTime: Double,
        val startFrame: Long,
        val endFrame: Long,
    ) : ScheduledEvent

    /** Playback reaches [time] on the tape at [frame]: where it starts. */
    data class Pass(val time: Double, val frame: Long) : ScheduledEvent
}

/** Where a pass may end: the times on the tape where playback may stop. */
data class PassBounds(val stops: List<Double> = emptyList())

/**
 * Walks the tape ahead of playback, a pass at a time. The caller gives each call the pass's [PassBounds], and the
 * scheduler never reads them from the song. A pass ends at the nearest stop ahead, which finishes playback; with none
 * it runs on. Call [advance] regularly with a horizon a little ahead of the clock; each event comes back once. The
 * song and bounds are read on every call, so edits apply from the next one. Not thread-safe: one caller at a time.
 */
class TapeScheduler(val sampleRate: Double, time: Double, startFrame: Long) {
    /** Where playback ends, once it has been reached. */
    var finishFrame: Long? = null
        private set

    private var passTime = time
    private var passFrame = startFrame.toDouble()
    private var isPassStarted = false

    /** The furthest horizon an earlier call looked to: the pass has been scheduled up to there. */
    private var reachedFrame = startFrame

    /** Hits earlier than this have been scheduled. */
    private var hitCursor = time

    private constructor(other: TapeScheduler) : this(other.sampleRate, other.passTime, 0) {
        finishFrame = other.finishFrame
        passFrame = other.passFrame
        isPassStarted = other.isPassStarted
        reachedFrame = other.reachedFrame
        hitCursor = other.hitCursor
    }

    /**
     * The events up to [horizon]: the clips and hits of every track. [silence] maps a track to the stretch of tape it
     * isn't heard over (the take recording onto it): its hits there are dropped and its clips leave it out.
     */
    fun advance(
        horizon: Long,
        now: Long,
        song: Song,
        bounds: PassBounds,
        silence: Map<UUID, OpenEndRange<Double>>,
    ): List<ScheduledEvent> {
        val earliest = roundHalfAway(now - LATE_TOLERANCE * sampleRate).toLong()
        val events = mutableListOf<ScheduledEvent>()

        while (finishFrame == null) {
            if (!isPassStarted) {
                if (roundHalfAway(passFrame).toLong() >= horizon) {
                    break
                }
                isPassStarted = true
                hitCursor = passTime
                events += ScheduledEvent.Pass(passTime, roundHalfAway(passFrame).toLong())
                appendClips(song, passEnd(bounds), silence, events)
            }
            val passEnd = passEnd(bounds)
            appendHits(song, passEnd, horizon, earliest, silence, events)

            if (!passEnd.isFinite() || frame(passEnd) > horizon) {
                break
            }
            passFrame = frame(passEnd)
            isPassStarted = false
            finishFrame = roundHalfAway(passFrame).toLong()
        }
        reachedFrame = maxOf(reachedFrame, horizon)
        return events
    }

    /**
     * The current pass's clips again, from [from] on: for audio that changed after the pass was scheduled, as when a
     * take ends. [silence] is as [advance] takes it.
     */
    fun clipsAgain(
        song: Song,
        from: Long,
        bounds: PassBounds,
        silence: Map<UUID, OpenEndRange<Double>>,
    ): List<ScheduledEvent> {
        val again = TapeScheduler(this)
        val passEnd = passEnd(bounds)
        val skipped = maxOf(0.0, from - passFrame)
        again.passTime += skipped / sampleRate
        again.passFrame += skipped
        val events = mutableListOf<ScheduledEvent>()
        again.appendClips(song, passEnd, silence, events)
        return events
    }

    /** The tape time the current pass has been scheduled up to. */
    private val scheduledTime: Double
        get() = passTime + maxOf(0.0, reachedFrame - passFrame) / sampleRate

    /** Where the pass ends on the tape: infinite when nothing ends it. */
    private fun passEnd(bounds: PassBounds): Double {
        val scheduled = scheduledTime
        return bounds.stops.filter { it > scheduled }.minOrNull() ?: Double.POSITIVE_INFINITY
    }

    private fun frame(time: Double): Double = passFrame + (time - passTime) * sampleRate

    private fun appendClips(
        song: Song,
        passEnd: Double,
        silence: Map<UUID, OpenEndRange<Double>>,
        events: MutableList<ScheduledEvent>,
    ) {
        for (track in song.tracks) {
            for (span in track.spans) {
                val clip = span.clip ?: continue
                val start = maxOf(span.start, passTime)
                val end = minOf(span.end, passEnd)
                for ((from, to) in pieces(start, end, silence[track.id])) {
                    events += ScheduledEvent.Clip(
                        track = track.id,
                        clip = clip,
                        sourceTime = span.sourceTime + (from - span.start),
                        startFrame = roundHalfAway(frame(from)).toLong(),
                        endFrame = roundHalfAway(frame(to)).toLong(),
                    )
                }
            }
        }
    }

    private fun appendHits(
        song: Song,
        passEnd: Double,
        horizon: Long,
        earliest: Long,
        silence: Map<UUID, OpenEndRange<Double>>,
        events: MutableList<ScheduledEvent>,
    ) {
        // Picked before the cursor moves, so hits at the same time all play.
        val hits = song.tracks
            .flatMap { track -> track.hits.map { track.id to it } }
            .filter { (_, hit) -> hit.time >= hitCursor && hit.time < passEnd }
            .sortedBy { (_, hit) -> hit.time }
        for ((track, hit) in hits) {
            val hitFrame = roundHalfAway(frame(hit.time)).toLong()
            if (hitFrame >= horizon) {
                return
            }
            hitCursor = Math.nextUp(hit.time)
            if (hitFrame >= earliest && silence[track]?.contains(hit.time) != true) {
                events += ScheduledEvent.Drum(track, hit.sound, hit.level, hitFrame)
            }
        }
        if (passEnd.isFinite()) {
            hitCursor = maxOf(hitCursor, passEnd)
        }
    }

    companion object {
        /**
         * How late a hit may be, in seconds, and still play. Later ones are dropped, so a stall doesn't stack them
         * into one burst.
         */
        const val LATE_TOLERANCE = 0.02

        /** What's left of `start until end` with [hole] taken out: none, one or two stretches, none of them empty. */
        private fun pieces(start: Double, end: Double, hole: OpenEndRange<Double>?): List<Pair<Double, Double>> {
            if (hole == null || hole.start >= end || hole.endExclusive <= start) {
                return if (end > start) listOf(start to end) else emptyList()
            }
            return listOf(start to hole.start, hole.endExclusive to end).filter { it.second > it.first }
        }
    }
}
