package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.Swing
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternTakeTest {
    private val rate = 48_000.0
    private val track = UUID.randomUUID()
    private val a = DrumPattern.rock(name = "A")
    private val b = DrumPattern(name = "B", bpm = 120).let { it.copy(steps = it.steps.with(Sound.KICK, 0, Level.HIT)) }
    private val rock120 = a.copy(bpm = 120)

    private fun frames(seconds: Double): Long = (seconds * rate).toLong()

    private fun kickFrames(events: List<ScheduledEvent>): List<Long> =
        events.filterIsInstance<ScheduledEvent.Drum>().filter { it.sound == Sound.KICK }.map { it.frame }

    private fun makeTake(startFrame: Long = 0, outTime: Double? = null, records: Boolean = true) = PatternTake(
        sampleRate = rate, pattern = a.id, startTime = 10.0, startFrame = startFrame, outTime = outTime,
        track = if (records) track else null, records = records,
    )

    @Test
    fun firstStepAtTheStart() {
        val take = makeTake()
        val events = take.advance(frames(1.0), 0, listOf(rock120, b))

        assertEquals(ScheduledEvent.Drum(track, Sound.KICK, Level.ACCENT, 0), events.first())
        assertEquals(10.0, take.hits.first().time)
    }

    @Test
    fun loopsUntilStopped() {
        val take = makeTake()
        take.advance(frames(5.0), 0, listOf(rock120, b))

        val kicks = take.hits.filter { it.sound == Sound.KICK && it.level == Level.ACCENT }.map { it.time }
        assertEquals(listOf(10.0, 12.0, 14.0), kicks)
    }

    @Test
    fun queuedSwitchesAtLoopEnd() {
        val take = makeTake()
        take.advance(frames(0.5), 0, listOf(rock120, b))
        take.queue(b.id, now = frames(0.5))
        val events = take.advance(frames(4.5), frames(0.5), listOf(rock120, b))

        assertEquals(b.id, take.playing)
        assertNull(take.queued)
        val late = events.filterIsInstance<ScheduledEvent.Drum>().filter { it.frame >= 96_000 }
        assertEquals(
            listOf(
                ScheduledEvent.Drum(track, Sound.KICK, Level.HIT, 96_000),
                ScheduledEvent.Drum(track, Sound.KICK, Level.HIT, 192_000),
            ),
            late,
        )
    }

    @Test
    fun queueBeforeStartReplaces() {
        val take = makeTake(startFrame = 48_000)
        take.advance(50_000, 46_000, listOf(rock120, b))
        take.queue(b.id, now = 46_000)
        val events = take.advance(200_000, 46_000, listOf(rock120, b))

        assertEquals(b.id, take.playing)
        assertNull(take.queued)
        // Step 0 stays as heard; the steps after it come from B, which has only a kick on step 0.
        assertEquals(listOf(144_000L), kickFrames(events))
    }

    @Test
    fun queueingThePlayingPatternClearsTheQueue() {
        val take = makeTake()
        take.advance(frames(0.5), 0, listOf(rock120, b))
        take.queue(b.id, now = frames(0.5))
        take.queue(a.id, now = frames(0.5))

        assertNull(take.queued)
    }

    @Test
    fun stopEndsAfterTheScheduledSteps() {
        val take = makeTake()
        take.advance(frames(1.0), 0, listOf(rock120, b))
        take.requestStop(now = frames(0.5))
        val events = take.advance(frames(5.0), frames(1.0), listOf(rock120, b))

        assertEquals(11.0, take.endTime)
        assertEquals(48_000L, take.endFrame)
        assertTrue(take.hits.isNotEmpty())
        assertTrue(take.hits.all { it.time < 11 })
        assertTrue(events.isEmpty())
    }

    @Test
    fun stopBeforeStartLeavesNothing() {
        val take = makeTake(startFrame = 48_000)
        take.requestStop(now = 0)
        take.advance(frames(5.0), 0, listOf(rock120, b))

        assertEquals(10.0, take.endTime)
        assertTrue(take.hits.isEmpty())
    }

    @Test
    fun outCutsMidLoop() {
        val take = makeTake(outTime = 11.0)
        val events = take.advance(frames(5.0), 0, listOf(rock120, b))

        assertEquals(11.0, take.endTime)
        assertTrue(take.hits.all { it.time < 11 })
        assertTrue(take.hits.isNotEmpty())
        assertTrue(events.filterIsInstance<ScheduledEvent.Drum>().all { it.frame < 48_000 })
    }

    @Test
    fun stopAndOutTheEarlierWins() {
        val take = makeTake(outTime = 13.0)
        take.advance(frames(1.0), 0, listOf(rock120, b))
        take.requestStop(now = frames(0.5))
        take.advance(frames(5.0), frames(1.0), listOf(rock120, b))

        assertEquals(11.0, take.endTime)
    }

    @Test
    fun tempoEditAppliesNextStep() {
        val take = makeTake()
        take.advance(frames(0.5), 0, listOf(rock120, b))
        val slower = rock120.copy(bpm = 60)
        val events = take.advance(frames(4.0), frames(0.5), listOf(slower, b))

        // Steps already scheduled keep their frames; those after are 0.25 s apart.
        val hats = events.filterIsInstance<ScheduledEvent.Drum>()
            .filter { it.sound == Sound.HAT && it.frame >= 24_000 }
            .map { it.frame }
        val gaps = hats.zipWithNext { first, second -> second - first }
        assertTrue(gaps.take(3).all { it == 24_000L })
    }

    @Test
    fun auditionKeepsNoHits() {
        val take = makeTake(records = false)
        val events = take.advance(frames(5.0), 0, listOf(rock120, b))

        assertTrue(events.isNotEmpty())
        assertTrue(take.hits.isEmpty())
        assertNull(take.endTime)
    }

    @Test
    fun staleStepsAreDropped() {
        val take = makeTake()
        val events = take.advance(frames(2.0), frames(1.0), listOf(rock120, b))

        assertTrue(events.isNotEmpty())
        assertTrue(take.hits.isNotEmpty())
        assertTrue(kickFrames(events).all { it >= frames(1.0) - 960 })
        assertTrue(take.hits.all { it.time >= 11 - 0.02 })
    }

    @Test
    fun missingPatternEmitsNothing() {
        val take = makeTake()
        val events = take.advance(frames(2.0), 0, listOf(b))

        assertTrue(events.isEmpty())
    }

    // New: Review Focus 4. A deleted pattern hands over to the queued one, or stops; it never spins.
    @Test
    fun aPatternDeletedMidTakeGoesToTheQueuedOneOrStops() {
        val queuedTake = makeTake()
        queuedTake.advance(frames(0.5), 0, listOf(rock120, b))
        queuedTake.queue(b.id, now = frames(0.5))
        val events = queuedTake.advance(frames(1.0), frames(0.5), listOf(b))

        assertEquals(b.id, queuedTake.playing)
        assertEquals(frames(0.5), kickFrames(events).first())

        val lone = makeTake()
        lone.advance(frames(0.5), 0, listOf(rock120))
        assertTrue(lone.advance(frames(60.0), frames(0.5), emptyList()).isEmpty())

        val emptied = makeTake()
        val empty = DrumPattern(id = a.id, name = "A", bars = 0)
        assertTrue(emptied.advance(frames(60.0), 0, listOf(empty)).isEmpty())
    }

    @Test
    fun positionReportsStep() {
        val take = makeTake()
        take.advance(frames(1.0), 0, listOf(rock120, b))

        assertEquals(a.id to 2, take.position(12_000))
        assertNull(take.position(-1))
    }

    @Test
    fun nearestStepRounds() {
        val take = makeTake()
        take.advance(frames(1.0), 0, listOf(rock120, b))

        assertEquals(2, take.nearestStep(11_000))
        assertEquals(1, take.nearestStep(7_000))
    }

    @Test
    fun stopInPreRollPurgesScheduledHits() {
        val take = makeTake(startFrame = 48_000)
        take.advance(50_000, 46_000, listOf(rock120, b))
        assertTrue(take.hits.isNotEmpty())
        take.requestStop(now = 46_000)

        assertEquals(10.0, take.endTime)
        assertTrue(take.hits.isEmpty())
    }

    @Test
    fun stopNearLoopEndKeepsTheNextLoopsScheduledHit() {
        val take = makeTake()
        take.advance(97_000, 92_000, listOf(rock120, b))
        take.requestStop(now = 92_000)

        // Step 0 of the next loop was already sent to be heard, so it stays.
        assertEquals(102_000L, take.endFrame)
        assertTrue(take.hits.any { it.time == 12.0 })
        assertTrue(take.hits.all { it.time < 12.125 })
    }

    @Test
    fun swingDelaysTheTakesOffbeats() {
        val swung = DrumPattern(name = "S", bpm = 120, swing = Swing.SIXTEENTH_HEAVY).let {
            it.copy(steps = it.steps.with(Sound.KICK, 0, Level.HIT).with(Sound.KICK, 1, Level.HIT))
        }
        val take = PatternTake(
            sampleRate = rate, pattern = swung.id, startTime = 10.0, startFrame = 0, outTime = null, track = track,
        )
        val events = take.advance(frames(0.5), 0, listOf(swung))

        val kicks = kickFrames(events)
        assertTrue(kicks.size >= 2)
        assertTrue(kicks[1].toDouble() > swung.stepDuration * rate)
        val times = take.hits.filter { it.sound == Sound.KICK }.map { it.time }
        assertTrue(times.size >= 2)
        assertTrue(times[1] > 10 + swung.stepDuration)
    }

    @Test
    fun aChokedSoundIsLeftOutOfTheTake() {
        val choked = DrumPattern(name = "C", bpm = 120).let {
            it.copy(steps = it.steps.with(Sound.HAT, 0, Level.HIT).with(Sound.OPEN_HAT, 0, Level.HIT))
        }
        val take = PatternTake(
            sampleRate = rate, pattern = choked.id, startTime = 10.0, startFrame = 0, outTime = null, track = track,
        )
        val events = take.advance(frames(0.1), 0, listOf(choked))

        assertEquals(listOf<ScheduledEvent>(ScheduledEvent.Drum(track, Sound.HAT, Level.HIT, 0)), events)
        assertEquals(listOf(Sound.HAT), take.hits.map { it.sound })
    }

    @Test
    fun clicksOnEachBeatAccentingTheFirst() {
        val take = makeTake(records = false)
        val clicks = take.advance(frames(2.0), 0, listOf(rock120), clicks = true).filterIsInstance<ScheduledEvent.Click>()
        // 120 BPM in 4/4: a beat every half second, beat 1 accented.
        assertEquals(listOf(0L, frames(0.5), frames(1.0), frames(1.5)), clicks.map { it.frame })
        assertEquals(listOf(ClickKind.ACCENT, ClickKind.BEAT, ClickKind.BEAT, ClickKind.BEAT), clicks.map { it.kind })
    }

    @Test
    fun aTakeThatRecordsNeverClicks() {
        val take = makeTake()
        val events = take.advance(frames(2.0), 0, listOf(rock120), clicks = true)
        assertTrue(events.none { it is ScheduledEvent.Click })
    }

    @Test
    fun noClicksUnlessAskedFor() {
        val take = makeTake(records = false)
        val events = take.advance(frames(2.0), 0, listOf(rock120))
        assertTrue(events.none { it is ScheduledEvent.Click })
    }

    @Test
    fun hitsCarryTheTakesTrack() {
        val drums = makeTake().advance(frames(2.0), 0, listOf(rock120))
            .filterIsInstance<ScheduledEvent.Drum>().map { it.track }
        val auditioned = makeTake(records = false).advance(frames(2.0), 0, listOf(rock120))
            .filterIsInstance<ScheduledEvent.Drum>().map { it.track }

        assertTrue(drums.isNotEmpty() && drums.all { it == track })
        assertTrue(auditioned.isNotEmpty() && auditioned.all { it == null })
    }
}
