package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Voice
import com.stephenmorris.recorder.core.Sound
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mix's level is the true sum of the tracks at their volumes and pans, before the limiters. */
class MixLevelsTest {
    private val rate = 8_000.0

    /** A Mic/Line track playing one second of a steady [level] from 1 s, and the store holding it. */
    private fun track(level: Float, clips: ClipStore, indexes: MutableMap<UUID, Int>): Track {
        val clip = UUID.randomUUID()
        indexes[clip] = assertNotNull(clips.add(FloatArray(rate.toInt()) { level }))
        return Track(number = 1, input = TrackInput.MIC, spans = listOf(Span(1.0, 2.0, 0.0, clip)))
    }

    private fun song(tracks: List<Track>): Song = Song.new("s").copy(tracks = tracks)

    private fun render(song: Song, clips: ClipStore, indexes: Map<UUID, Int>): MixLevels =
        assertNotNull(MixLevels.render(song, clips, indexes, emptyList(), rate))

    @Test
    fun tracksSumAndClipPastFullScale() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val a = track(0.6f, clips, indexes)
        val b = track(0.6f, clips, indexes)
        val levels = render(song(listOf(a, b)), clips, indexes)

        val during = assertNotNull(levels.level(1.2, 1.8))
        assertTrue(abs(during - 1.2f) < 0.001f)
        assertTrue(MixLevels.clips(during))
        assertEquals(0f, levels.level(0.2, 0.8))
    }

    @Test
    fun volumeChangesTheSum() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val a = track(0.6f, clips, indexes)
        val b = track(0.6f, clips, indexes).copy(volume = -6)
        val levels = render(song(listOf(a, b)), clips, indexes)

        val during = assertNotNull(levels.level(1.2, 1.8))
        assertTrue(abs(during - 0.6f * (1 + 10.0.pow(-6.0 / 20).toFloat())) < 0.001f)
    }

    @Test
    fun onlyTracksWithHitsNeedAStem() {
        val track = Track(number = 1, input = TrackInput.DRUMS)
        assertFalse(MixLevels.needsStem(track))
        assertTrue(MixLevels.needsStem(track.copy(hits = listOf(hit(at = 1.0)))))
    }

    @Test
    fun aTracksStemKeyHoldsItsSpansAndItsHits() {
        var track = Track(number = 1, input = TrackInput.MIC)
        val plain = MixLevels.StemKey(track, songLength = 10.0, sampleRate = rate)
        track = track.copy(hits = listOf(hit(at = 1.0)))
        val withHits = MixLevels.StemKey(track, songLength = 10.0, sampleRate = rate)
        track = track.copy(spans = listOf(Span(0.0, 1.0, 0.0)))
        val withBoth = MixLevels.StemKey(track, songLength = 10.0, sampleRate = rate)
        assertNotEquals(plain, withHits)
        assertNotEquals(withHits, withBoth)
        assertEquals(withBoth, MixLevels.StemKey(track, songLength = 10.0, sampleRate = rate))
    }

    // New: Review Focus 3. A ten-minute song's frames fit an Int, worked out without allocating a stem.
    @Test
    fun aTenMinuteSongsFrameCountFitsAnInt() {
        val key = MixLevels.StemKey(Track(number = 1, input = TrackInput.MIC), songLength = 600.0, sampleRate = 48_000.0)
        assertEquals(28_800_000, key.frames)
    }

    @Test
    fun theKeyIgnoresWhatDoesNotChangeTheLevel() {
        val song = Song.sample
        assertEquals(MixLevels.Key(song), MixLevels.Key(song.copy(name = "renamed", notes = "n")))
        assertNotEquals(MixLevels.Key(song), MixLevels.Key(song.withTrack(0, song.tracks[0].copy(volume = -6))))
    }

    @Test
    fun coversTheWholeSong() {
        val song = Song.new("s")
        val levels = render(song, ClipStore(), emptyMap())

        val size = MixLevels.FRAMES_PER_PEAK
        assertEquals(((song.length * rate).toInt() + size - 1) / size, levels.peaks.size)
        assertTrue(levels.peaks.all { it == 0f })
        assertNull(levels.level(song.length + 1, song.length + 2))
    }

    @Test
    fun stopsWhenCancelled() {
        assertNull(MixLevels.render(Song.new("s"), ClipStore(), emptyMap(), emptyList(), rate, isCancelled = { true }))
    }

    @Test
    fun panChangesTheSum() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val a = track(0.6f, clips, indexes).copy(pan = -50)
        val b = track(0.6f, clips, indexes).copy(pan = 50)
        val levels = render(song(listOf(a, b)), clips, indexes)

        // Hard left and hard right never meet in a channel, so they don't add up.
        val during = assertNotNull(levels.level(1.2, 1.8))
        assertTrue(abs(during - 0.6f) < 0.001f)
    }

    @Test
    fun summingFromRecordingsMatchesRendering() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val samples = mutableMapOf<UUID, FloatArray>()
        val tracks = listOf(Triple(0.3f, 0, -20), Triple(0.5f, -6, 30)).map { (level, volume, pan) ->
            val clip = UUID.randomUUID()
            // A ramp, so where it's read from matters.
            val ramp = FloatArray(rate.toInt()) { it / rate.toFloat() * level }
            samples[clip] = ramp
            indexes[clip] = assertNotNull(clips.add(ramp))
            Track(number = 1, input = TrackInput.MIC, volume = volume, pan = pan, spans = listOf(Span(1.0, 2.0, 0.0, clip)))
        }
        val song = song(tracks)

        val rendered = render(song, clips, indexes)
        val summed = assertNotNull(MixLevels.sum(song, emptyMap(), samples, rate))
        assertEquals(rendered.peaks.size, summed.peaks.size)
        assertTrue(rendered.peaks.indices.all { abs(rendered.peaks[it] - summed.peaks[it]) < 0.002f })
    }

    @Test
    fun aFaderMoveOnlySumsAgain() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        var track = track(0.4f, clips, indexes)
        val stem = assertNotNull(
            MixLevels.renderStem(track, song(listOf(track)).length, clips, indexes, emptyList(), rate),
        )
        track = track.copy(volume = 6)
        val song = song(listOf(track))

        val levels = assertNotNull(MixLevels.sum(song, mapOf(track.id to stem), emptyMap(), rate))
        val during = assertNotNull(levels.level(1.2, 1.8))
        assertTrue(abs(during - 0.4f * 10.0.pow(6.0 / 20).toFloat()) < 0.001f)
    }

    @Test
    fun aStemHoldsItsTracksRecordingsAndHits() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val track = track(0.4f, clips, indexes).copy(hits = listOf(hit(at = 3.0)))
        val voices = Sound.entries.map { Voice(FloatArray(64) { 0.5f }) }
        val stem = assertNotNull(MixLevels.renderStem(track, 4.0, clips, indexes, voices, rate))

        assertEquals(0.4f, stem[(1.5 * rate).toInt()])
        assertTrue(stem[(3 * rate).toInt()] > 0)
    }

    @Test
    fun renderingAStemReportsItsProgressAsItGoes() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val track = track(0.4f, clips, indexes)
        val reports = mutableListOf<Double>()
        assertNotNull(MixLevels.renderStem(track, 4.0, clips, indexes, emptyList(), rate, progress = { reports += it }))

        // 4 s at 8 kHz is eight blocks of 4,096 frames.
        assertEquals(8, reports.size)
        assertEquals(reports.sorted(), reports)
        assertEquals(1.0, reports.last())
    }

    @Test
    fun summingAThreeMinuteSongIsQuick() {
        val rate = 48_000.0
        val frames = (180 * rate).toInt()
        val stem = FloatArray(frames) { (sin(it * 0.01) * 0.3).toFloat() }
        val tracks = List(Song.TRACK_COUNT) {
            Track(number = 1, input = TrackInput.MIC, spans = listOf(Span(0.0, 150.0, 0.0, UUID.randomUUID())))
        }
        val stems = tracks.associate { it.id to stem }
        val song = song(tracks)

        MixLevels.sum(song, stems, emptyMap(), rate)
        val start = System.nanoTime()
        MixLevels.sum(song, stems, emptyMap(), rate)
        assertTrue((System.nanoTime() - start) / 1e9 < 1.0)
    }

    // Review: a track with no hits is read from its recordings, not rendered into a duplicate stem.
    @Test
    fun renderingReadsTracksWithoutHitsFromTheirRecordings() {
        val clips = ClipStore()
        val indexes = mutableMapOf<UUID, Int>()
        val stemmed = mutableListOf<UUID>()
        val track = track(0.6f, clips, indexes)

        val levels = assertNotNull(
            MixLevels.render(song(listOf(track)), clips, indexes, emptyList(), rate, onStem = { stemmed += it }),
        )

        assertTrue(stemmed.isEmpty())
        assertTrue(abs(assertNotNull(levels.level(1.2, 1.8)) - 0.6f) < 0.001f)
    }
}
