package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepGrid
import com.stephenmorris.recorder.core.Swing
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongTest {
    @Test
    fun replacingSplitsAroundTheNewSpan() {
        val spans = Span.replacing(listOf(Span(0.0, 10.0, 1.0)), Span(4.0, 6.0, 2.0))

        assertEquals(3, spans.size)
        assertTrue(spans.any { it.start == 0.0 && it.end == 4.0 && it.seed == 1.0 && it.sourceTime == 0.0 })
        assertTrue(spans.any { it.start == 6.0 && it.end == 10.0 && it.seed == 1.0 && it.sourceTime == 6.0 })
        assertTrue(spans.any { it.start == 4.0 && it.end == 6.0 && it.seed == 2.0 })
    }

    @Test
    fun spanCoveringAnotherRemovesIt() {
        val spans = Span.replacing(listOf(Span(2.0, 3.0, 1.0)), Span(0.0, 8.0, 2.0))

        assertEquals(1, spans.size)
        assertEquals(2.0, spans[0].seed)
    }

    @Test
    fun clearingCutsAndShiftsSource() {
        val clip = UUID.randomUUID()
        val spans = Span.clearing(listOf(Span(2.0, 8.0, 1.0, clip, sourceTime = 1.0)), 3.0..<5.0)

        assertEquals(2, spans.size)
        assertTrue(spans.any { it.start == 2.0 && it.end == 3.0 && it.sourceTime == 1.0 && it.clip == clip })
        assertTrue(spans.any { it.start == 5.0 && it.end == 8.0 && it.sourceTime == 4.0 && it.clip == clip })
    }

    @Test
    fun recordingHitsReplacesOnlyTheRange() {
        var track = Track(number = 1, input = TrackInput.DRUMS)
            .copy(hits = listOf(1.0, 2.0, 3.0, 4.0).map { Hit(it, Sound.KICK, Level.HIT) })

        track = track.recording(listOf(Hit(2.5, Sound.SNARE, Level.ACCENT)), over = 2.0..<3.5)

        assertEquals(listOf(1.0, 2.5, 4.0), track.hits.map { it.time })
    }

    @Test
    fun recordingASpanReplacesWhatItCovers() {
        val track = Track(number = 1, input = TrackInput.MIC)
            .recording(Span(0.0, 10.0, 1.0))
            .recording(Span(4.0, 6.0, 2.0))

        assertEquals(3, track.spans.size)
    }

    @Test
    fun lengthIsContentPlusBlankTape() {
        var song = Song.new("a")
        assertEquals(30.0, song.length)
        assertEquals("0:00", song.meta)

        val track = Track(number = 1, input = TrackInput.MIC).recording(Span(2.0, 12.5, 1.0))
        song = song.withTrack(0, track)

        assertEquals(12.5, song.contentEnd)
        assertEquals(42.5, song.length)
        assertEquals("0:12", song.meta)
    }

    @Test
    fun contentEndCountsHits() {
        val track = Track(number = 1, input = TrackInput.DRUMS).copy(hits = listOf(Hit(3.0, Sound.KICK, Level.HIT)))

        assertEquals(3.0, track.contentEnd)
    }

    @Test
    fun addPatternTakesNextLetterAndSelectedTempoMeterAndLength() {
        val song = Song.new("s").withPattern(0) {
            it.copy(bpm = 92, meter = MeterId.SIX_EIGHT, swing = Swing.EIGHTH_HEAVY, bars = 2)
        }

        val (added, id) = song.addingPattern()

        val pattern = added.pattern(id)
        assertEquals("B", pattern?.name)
        assertEquals(92, pattern?.bpm)
        assertEquals(MeterId.SIX_EIGHT, pattern?.meter)
        assertEquals(2, pattern?.bars)
        assertEquals(Swing.OFF, pattern?.swing)
        assertEquals(StepGrid.EMPTY, pattern?.steps)
        assertEquals(id, added.selectedPattern)
    }

    @Test
    fun duplicatePatternCopiesSteps() {
        val song = Song.new("s")
        val original = song.patterns[0]

        val (duplicated, id) = song.duplicatingPattern(original.id)

        val copy = duplicated.pattern(id)
        assertEquals("A 2", copy?.name)
        assertEquals(original.steps, copy?.steps)
        assertNotEquals(original.id, id)
        assertEquals(id, duplicated.selectedPattern)
    }

    @Test
    fun deletingSelectedSelectsNeighbour() {
        var song = Song.new("s")
        val a = song.patterns[0].id
        val (withB, b) = song.addingPattern()
        val (withC, c) = withB.addingPattern()
        song = withC.copy(selectedPattern = b)

        song = song.deletingPattern(b)
        assertEquals(c, song.selectedPattern)

        song = song.deletingPattern(c)
        assertEquals(a, song.selectedPattern)

        song = song.deletingPattern(a)
        assertEquals(1, song.patterns.size)
        assertEquals(a, song.selectedPattern)
    }

    @Test
    fun currentPatternIsTheSelectedElseTheLast() {
        var song = Song.new("s")
        val a = song.patterns[0].id
        val (added, b) = song.addingPattern()
        song = added.copy(selectedPattern = a)
        assertEquals(a, song.currentPattern?.id)
        song = song.copy(selectedPattern = null)
        assertEquals(b, song.currentPattern?.id)
    }

    @Test
    fun theSampleHoldsEightLoopsOfRock() {
        val song = Song.sample

        assertEquals(92, song.patterns[0].bpm)
        assertEquals(0.0, song.tracks[0].hits.first().time)
        assertTrue(abs(song.contentEnd - 8 * song.patterns[0].loopDuration) < 5)
    }

    @Test
    fun newSongHasFourNumberedTracks() {
        val song = Song.new("x")
        assertEquals(listOf("Track 1", "Track 2", "Track 3", "Track 4"), song.tracks.map { it.name })
        assertEquals(listOf(TrackInput.DRUMS, TrackInput.MIC, TrackInput.MIC, TrackInput.MIC), song.tracks.map { it.input })
        assertEquals(1, song.patterns.size)
        assertEquals(song.patterns[0].id, song.selectedPattern)
    }

    // New: the numbers, and the one pattern is the rock beat named "A".
    @Test
    fun aNewSongIsNumberedOneToFourWithRockPatternASelected() {
        val song = Song.new("X")
        assertEquals("X", song.name)
        assertEquals(listOf(1, 2, 3, 4), song.tracks.map { it.number })
        assertEquals(listOf("A"), song.patterns.map { it.name })
        assertEquals(DrumPattern.rock().steps, song.patterns[0].steps)
        assertEquals(song.patterns[0], song.currentPattern)
    }

    @Test
    fun micTakeClearsHitsUnderIt() {
        val track = Track(number = 1, input = TrackInput.MIC)
            .copy(hits = listOf(hit(at = 1.0), hit(at = 2.5), hit(at = 4.0)))
            .recording(Span(2.0, 3.0, 0.0))
        assertEquals(listOf(1.0, 4.0), track.hits.map { it.time })
    }

    @Test
    fun drumTakeClearsAudioUnderIt() {
        val track = Track(number = 1, input = TrackInput.DRUMS)
            .copy(spans = listOf(Span(0.0, 10.0, 0.0)))
            .recording(listOf(hit(at = 4.0)), over = 3.0..<5.0)
        assertEquals(listOf(listOf(0.0, 3.0), listOf(5.0, 10.0)), track.spans.map { listOf(it.start, it.end) })
        assertEquals(5.0, track.spans[1].sourceTime)
    }

    @Test
    fun pointsKeepInBeforeOut() {
        var song = Song.new("s").settingIn(5.0)
        assertFalse(song.canSetOut(5.0))
        assertFalse(song.canSetOut(4.0))
        assertTrue(song.canSetOut(6.0))
        song = song.settingOut(8.0)
        assertFalse(song.canSetIn(8.0))
        assertTrue(song.canSetIn(7.9))
        assertEquals(5.0, song.inPoint)
        assertEquals(8.0, song.outPoint)
    }

    @Test
    fun invalidPointIsIgnored() {
        val song = Song.new("s").settingOut(4.0).settingIn(6.0)
        assertNull(song.inPoint)
    }

    @Test
    fun outPointCantGoAtZero() {
        var song = Song.new("s")
        assertFalse(song.canSetOut(0.0))
        song = song.settingOut(0.0)
        assertNull(song.outPoint)
        assertTrue(song.canSetOut(0.01))
    }

    @Test
    fun uniqueNameCountsFromTwo() {
        assertEquals("A", uniqueName("A", listOf("B")))
        assertEquals("A 2", uniqueName("A", listOf("A")))
        assertEquals("A 3", uniqueName("A", listOf("A", "A 2")))
    }

    // Review: an empty or reversed span or range changes nothing, so it can't be saved or play audio twice.
    @Test
    fun recordingAnEmptyOrReversedSpanChangesNothing() {
        val track = Track(number = 1, input = TrackInput.MIC, spans = listOf(Span(0.0, 20.0, 1.0)), hits = listOf(hit(at = 7.0)))

        assertEquals(track, track.recording(Span(10.0, 5.0, 2.0)))
        assertEquals(track, track.recording(Span(5.0, 5.0, 2.0)))
        assertEquals(track, track.recording(listOf(hit(at = 6.0)), over = 10.0..<5.0))
        assertEquals(track.spans, Span.clearing(track.spans, 10.0..<5.0))
    }
}
