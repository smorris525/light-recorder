package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapeSchedulerTest {
    private val rate = 48_000.0

    private fun song(hits: List<Double> = emptyList(), spans: List<Span> = emptyList()): Song {
        var song = Song.new("Test")
        song = song.withTrack(0, song.tracks[0].copy(hits = hits.map { Hit(it, Sound.KICK, Level.HIT) }))
        return song.withTrack(1, song.tracks[1].copy(spans = spans))
    }

    private fun run(
        song: Song,
        from: Double,
        seconds: Double,
        now: Long = 0,
        bounds: PassBounds = PassBounds(),
        silence: Map<UUID, OpenEndRange<Double>> = emptyMap(),
    ): Pair<List<ScheduledEvent>, TapeScheduler> {
        val scheduler = TapeScheduler(sampleRate = rate, time = from, startFrame = 1000)
        val events = scheduler.advance(1000 + (seconds * rate).toLong(), now, song, bounds, silence)
        return events to scheduler
    }

    private fun List<ScheduledEvent>.drums() = filterIsInstance<ScheduledEvent.Drum>()

    @Test
    fun passStartsWithItsTime() {
        val (events, _) = run(song(), from = 5.0, seconds = 1.0, bounds = PassBounds(listOf(20.0)))

        assertEquals(ScheduledEvent.Pass(time = 5.0, frame = 1000), events.first())
    }

    @Test
    fun hitsLandAtTheirTimes() {
        val kicking = song(hits = listOf(6.0))
        val (events, _) = run(kicking, from = 5.0, seconds = 2.0, bounds = PassBounds(listOf(20.0)))

        assertTrue(ScheduledEvent.Drum(kicking.tracks[0].id, Sound.KICK, Level.HIT, 1000L + 48_000) in events)
    }

    @Test
    fun hitsTogetherAllPlay() {
        var together = song()
        together = together.withTrack(
            0,
            together.tracks[0].copy(hits = listOf(Hit(6.0, Sound.KICK, Level.HIT), Hit(6.0, Sound.HAT, Level.ACCENT))),
        )
        val drums = run(together, from = 5.0, seconds = 2.0, bounds = PassBounds(listOf(20.0))).first.drums()

        val frame = 1000L + 48_000
        val track = together.tracks[0].id
        assertEquals(
            listOf(
                ScheduledEvent.Drum(track, Sound.KICK, Level.HIT, frame),
                ScheduledEvent.Drum(track, Sound.HAT, Level.ACCENT, frame),
            ),
            drums,
        )
    }

    @Test
    fun clipStartsPartWayIn() {
        val clip = UUID.randomUUID()
        val vocal = song(spans = listOf(Span(4.0, 10.0, 1.0, clip, sourceTime = 1.0)))
        val (events, _) = run(vocal, from = 5.0, seconds = 1.0, bounds = PassBounds(listOf(20.0)))

        val track = vocal.tracks[1].id
        assertTrue(ScheduledEvent.Clip(track, clip, 2.0, 1000, 1000L + 5 * 48_000) in events)
    }

    @Test
    fun spansWithoutAClipPlayNothing() {
        val (events, _) = run(song(spans = listOf(Span(4.0, 10.0, 1.0))), from = 5.0, seconds = 1.0, bounds = PassBounds(listOf(20.0)))

        assertTrue(events.none { it is ScheduledEvent.Clip })
    }

    @Test
    fun endFinishes() {
        val (_, ending) = run(song(), from = 5.0, seconds = 10.0, bounds = PassBounds(listOf(7.0)))
        val (_, running) = run(song(), from = 5.0, seconds = 10.0, bounds = PassBounds())

        assertEquals(1000L + 96_000, ending.finishFrame)
        assertNull(running.finishFrame)
    }

    @Test
    fun silencedHitsDontPlay() {
        val kicking = song(hits = listOf(5.5, 6.5))
        val track = kicking.tracks[0].id
        val (events, _) = run(
            kicking, from = 5.0, seconds = 3.0, bounds = PassBounds(listOf(20.0)), silence = mapOf(track to 6.0..<10.0),
        )

        assertEquals(listOf(ScheduledEvent.Drum(track, Sound.KICK, Level.HIT, 1000L + 24_000)), events.drums())
    }

    @Test
    fun lateHitsAreDropped() {
        val late = run(song(hits = listOf(5.5)), from = 5.0, seconds = 1.0, now = 1000L + 24_000 + 4_800, bounds = PassBounds(listOf(20.0))).first
        val onTime = run(song(hits = listOf(5.5)), from = 5.0, seconds = 1.0, now = 1000L + 24_000, bounds = PassBounds(listOf(20.0))).first

        assertTrue(late.drums().isEmpty())
        assertTrue(onTime.drums().isNotEmpty())
    }

    @Test
    fun eachEventComesBackOnce() {
        val scheduler = TapeScheduler(sampleRate = rate, time = 5.0, startFrame = 1000)
        val tape = song(hits = listOf(5.5, 6.5))
        val first = scheduler.advance(1000L + 48_000, 0, tape, PassBounds(listOf(20.0)), emptyMap())
        val second = scheduler.advance(1000L + 3 * 48_000, 0, tape, PassBounds(listOf(20.0)), emptyMap())

        assertEquals(2, first.size)
        assertEquals(listOf(ScheduledEvent.Drum(tape.tracks[0].id, Sound.KICK, Level.HIT, 1000L + 72_000)), second)
    }

    @Test
    fun clipsAgainStartPartWayIntoASpan() {
        val clip = UUID.randomUUID()
        val vocal = song(spans = listOf(Span(4.0, 10.0, 1.0, clip, sourceTime = 1.0)))
        val scheduler = TapeScheduler(sampleRate = rate, time = 5.0, startFrame = 1000)
        scheduler.advance(1000L + 48_000, 0, vocal, PassBounds(listOf(20.0)), emptyMap())
        val again = scheduler.clipsAgain(vocal, from = 1000L + 24_000, bounds = PassBounds(listOf(20.0)), silence = emptyMap())

        assertEquals(listOf(ScheduledEvent.Clip(vocal.tracks[1].id, clip, 2.5, 25_000, 241_000)), again)
    }

    @Test
    fun stopBehindScheduledIsIgnored() {
        val scheduler = TapeScheduler(sampleRate = rate, time = 5.0, startFrame = 0)
        scheduler.advance((2 * rate).toLong(), 0, song(), PassBounds(listOf(30.0)), emptyMap())
        // An out point set at 6, behind the 7 s already scheduled.
        scheduler.advance((3 * rate).toLong(), 0, song(), PassBounds(listOf(6.0, 30.0)), emptyMap())
        assertNull(scheduler.finishFrame)
    }

    @Test
    fun nearestStopAheadWins() {
        val scheduler = TapeScheduler(sampleRate = rate, time = 5.0, startFrame = 0)
        scheduler.advance((10 * rate).toLong(), 0, song(), PassBounds(listOf(30.0, 8.0)), emptyMap())
        assertEquals((3 * rate).toLong(), scheduler.finishFrame)
    }

    @Test
    fun noStopsRunsOn() {
        val scheduler = TapeScheduler(sampleRate = rate, time = 0.0, startFrame = 0)
        scheduler.advance((100 * rate).toLong(), 0, song(), PassBounds(), emptyMap())
        assertNull(scheduler.finishFrame)
    }

    @Test
    fun hitsOnEveryTrackPlay() {
        var tape = Song.new("Test")
        tape = tape.withTrack(1, tape.tracks[1].copy(input = TrackInput.MIC, hits = listOf(Hit(1.0, Sound.KICK, Level.HIT))))
        tape = tape.withTrack(2, tape.tracks[2].copy(hits = listOf(Hit(2.0, Sound.SNARE, Level.HIT))))
        val drums = run(tape, from = 0.0, seconds = 3.0, bounds = PassBounds(listOf(20.0))).first.drums()

        assertEquals(
            listOf(
                ScheduledEvent.Drum(tape.tracks[1].id, Sound.KICK, Level.HIT, 1000L + 48_000),
                ScheduledEvent.Drum(tape.tracks[2].id, Sound.SNARE, Level.HIT, 1000L + 96_000),
            ),
            drums,
        )
    }

    @Test
    fun silenceDropsOnlyItsTracksHits() {
        var tape = Song.new("Test")
        tape = tape.withTrack(0, tape.tracks[0].copy(hits = listOf(Hit(1.0, Sound.KICK, Level.HIT))))
        tape = tape.withTrack(1, tape.tracks[1].copy(hits = listOf(Hit(1.0, Sound.SNARE, Level.HIT))))
        val silence = mapOf(tape.tracks[0].id to 0.0..<5.0)
        val drums = run(tape, from = 0.0, seconds = 3.0, bounds = PassBounds(listOf(20.0)), silence = silence).first.drums()

        assertEquals(listOf(ScheduledEvent.Drum(tape.tracks[1].id, Sound.SNARE, Level.HIT, 1000L + 48_000)), drums)
    }

    @Test
    fun silenceTrimsItsTracksClips() {
        val clip = UUID.randomUUID()
        var tape = Song.new("Test")
        tape = tape.withTrack(0, tape.tracks[0].copy(spans = listOf(Span(0.0, 10.0, 1.0, clip))))
        val track = tape.tracks[0].id
        val clips = run(tape, from = 0.0, seconds = 1.0, bounds = PassBounds(listOf(20.0)), silence = mapOf(track to 2.0..<4.0))
            .first.filterIsInstance<ScheduledEvent.Clip>()

        assertEquals(
            listOf(
                ScheduledEvent.Clip(track, clip, 0.0, 1000, 1000L + 2 * 48_000),
                ScheduledEvent.Clip(track, clip, 4.0, 1000L + 4 * 48_000, 1000L + 10 * 48_000),
            ),
            clips,
        )
    }
}
