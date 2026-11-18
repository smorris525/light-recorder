package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.beatClicks
import java.util.UUID

/**
 * Patterns played live from a point on the tape. A take records the hits it schedules onto its track; one that
 * doesn't record is the pattern screens' audition, looping until it is dropped. Call [advance] regularly with a
 * horizon a little ahead of the clock; each event comes back once. Not thread-safe: one caller at a time.
 */
class PatternTake(
    val sampleRate: Double,
    pattern: UUID,
    val startTime: Double,
    val startFrame: Long,
    outTime: Double?,
    /** The track the take's hits are played as: at its volume and pan. `null` for an audition, heard at unity. */
    val track: UUID?,
    val records: Boolean = true,
) {
    /** A step as scheduled, on its unswung grid. */
    private class Step(val pattern: UUID, val index: Int, val loopSteps: Int, val start: Double, val end: Double)

    val outTime: Double? = if (records) outTime else null

    var playing: UUID = pattern
        private set

    var queued: UUID? = null
        private set

    private val scheduledHits = mutableListOf<Hit>()

    /** Every hit scheduled, at tape times. */
    val hits: List<Hit>
        get() = scheduledHits

    /** The tape time the take ended, once it has. */
    var endTime: Double? = null
        private set

    var endFrame: Long? = null
        private set

    /** The step to schedule next, and where it falls. */
    private var step = 0
    private var cursor = startFrame.toDouble()
    private val steps = ArrayDeque<Step>()

    /**
     * Queues [pattern] for the end of the loop. Before the take starts it replaces the pattern playing instead; steps
     * not yet scheduled carry on from the current step in it.
     */
    fun queue(pattern: UUID, now: Long) {
        if (now < startFrame) {
            playing = pattern
            queued = null
        } else {
            queued = if (pattern == playing) null else pattern
        }
    }

    /**
     * Ends the take now, keeping the hits already scheduled, which are on their way to being heard: so nothing heard
     * goes unrecorded. Before it starts, at once with no hits.
     */
    fun requestStop(now: Long) {
        if (now < startFrame) {
            end(startFrame)
            return
        }
        end(maxOf(now, roundHalfAway(cursor).toLong()))
    }

    /**
     * The events up to [horizon], from the patterns as they are now, so edits apply from the next step. With [clicks],
     * each beat clicks too, beat 1 accented (7/8 clicks three times: 2 + 2 + 3); a take that records never clicks.
     */
    fun advance(horizon: Long, now: Long, patterns: List<DrumPattern>, clicks: Boolean = false): List<ScheduledEvent> {
        val earliest = roundHalfAway(now - LATE_TOLERANCE * sampleRate).toLong()
        val events = mutableListOf<ScheduledEvent>()

        while (cursor < horizon && cursor < cutoff) {
            val pattern = patterns.firstOrNull { it.id == playing }
            if (pattern == null) {
                val next = queued ?: break
                playing = next
                queued = null
                step = 0
                continue
            }
            if (step >= pattern.stepsPerLoop) {
                step = 0
                val next = queued
                if (next != null) {
                    playing = next
                    queued = null
                    continue
                }
            }

            val length = pattern.stepDuration * sampleRate
            remember(Step(pattern.id, step, pattern.stepsPerLoop, cursor, cursor + length))
            val frame = roundHalfAway(cursor + pattern.swingOffset(step) * sampleRate).toLong()
            if (frame >= earliest && frame < cutoff) {
                val layout = pattern.meter.layout
                if (clicks && !records) {
                    // On the beat itself: swing only moves the steps between beats.
                    layout.beatClicks.firstOrNull { it.step == step % layout.stepsPerBar }?.let {
                        events += ScheduledEvent.Click(it.kind, roundHalfAway(cursor).toLong())
                    }
                }
                for ((sound, level) in pattern.sounding(step)) {
                    events += ScheduledEvent.Drum(track, sound, level, frame)
                    if (records) {
                        scheduledHits += Hit(tapeTime(frame), sound, level)
                    }
                }
            }
            cursor += length
            step += 1
        }

        val out = outFrame
        if (out != null && horizon >= out) {
            end(roundHalfAway(out).toLong(), outTime)
        }
        return events
    }

    /** The step heard at [frame], on the grid without swing, for the editor's playhead: its pattern and index. */
    fun position(frame: Long): Pair<UUID, Int>? {
        if (frame >= cutoff) {
            return null
        }
        val found = steps.lastOrNull { it.start <= frame && frame < it.end } ?: return null
        return found.pattern to found.index
    }

    /** The step whose start is nearest [frame], for pad overdubs. */
    fun nearestStep(frame: Long): Int? {
        val first = steps.firstOrNull() ?: return null
        val last = steps.last()
        val at = frame.toDouble()
        if (at < first.start || at > last.end) {
            return null
        }
        val found = steps.lastOrNull { it.start <= at } ?: return null
        if (at - found.start <= found.end - at) {
            return found.index
        }
        return (found.index + 1) % found.loopSteps
    }

    /** Where the take cuts off: steps at or after it aren't scheduled. */
    private val cutoff: Double
        get() = minOf(endFrame?.toDouble() ?: Double.POSITIVE_INFINITY, outFrame ?: Double.POSITIVE_INFINITY)

    private val outFrame: Double?
        get() = outTime?.let { startFrame + (it - startTime) * sampleRate }

    private fun tapeTime(frame: Long): Double = startTime + (frame - startFrame) / sampleRate

    /** Ends the take at [frame], unless it already ends sooner. */
    private fun end(frame: Long, time: Double? = null) {
        val current = endFrame
        if (current != null && current <= frame) {
            return
        }
        endFrame = frame
        val ended = time ?: tapeTime(frame)
        endTime = ended
        scheduledHits.removeAll { it.time >= ended }
    }

    private fun remember(current: Step) {
        steps.addLast(current)
        while (steps.size > KEPT_STEPS) {
            steps.removeFirst()
        }
    }

    companion object {
        /** How late a hit may be, in seconds, and still play. */
        const val LATE_TOLERANCE = TapeScheduler.LATE_TOLERANCE

        /** The most steps kept for [position] and [nearestStep]. */
        private const val KEPT_STEPS = 512
    }
}
