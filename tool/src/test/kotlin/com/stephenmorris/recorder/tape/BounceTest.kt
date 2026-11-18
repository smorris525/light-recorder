package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Voice
import com.stephenmorris.recorder.core.Sound
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A bounce sums the picked tracks at their volumes (not pans) into one, unclamped. */
class BounceTest {
    @Test
    fun summaryReadsAsListed() {
        assertEquals("None", Bounce.summary(emptyList()))
        assertEquals("Track 1", Bounce.summary(listOf(1)))
        assertEquals("Tracks 1 and 2", Bounce.summary(listOf(1, 2)))
        assertEquals("Tracks 1, 2, and 3", Bounce.summary(listOf(1, 2, 3)))
        assertEquals("Tracks 1, 2, 3, and 4", Bounce.summary(listOf(1, 2, 3, 4)))
        assertEquals("Tracks 1 and 3", Bounce.summary(listOf(3, 1)))
    }

    @Test
    fun accumulateKeepsOverFullScale() {
        val mix = Bounce.accumulate(floatArrayOf(0.8f, 0.8f), gain = 1f, into = floatArrayOf(0.8f, 0.8f, 0.1f))
        assertEquals(3, mix.size)
        assertTrue(abs(mix[0] - 1.6f) < 1e-6f && abs(mix[1] - 1.6f) < 1e-6f && abs(mix[2] - 0.1f) < 1e-6f)
    }

    @Test
    fun accumulateAppliesGain() {
        val mix = Bounce.accumulate(floatArrayOf(1f, 1f), gain = 0.5f, into = FloatArray(0))
        assertContentEquals(floatArrayOf(0.5f, 0.5f), mix)
    }

    @Test
    fun applyBounceReplacesTheDestination() {
        var song = Song.new("s")
        song = song.withTrack(0, song.tracks[0].copy(hits = listOf(hit(at = 1.0)), spans = listOf(Span(0.0, 2.0, 1.0))))
        song = song.withTrack(1, song.tracks[1].copy(spans = listOf(Span(0.0, 1.0, 2.0))))
        val clip = UUID.randomUUID()
        song = song.applyingBounce(sources = setOf(1, 2), destination = 1, clip = clip, duration = 3.0)

        assertEquals(listOf(Span(0.0, 3.0, 0.0, clip)), song.tracks[0].spans)
        assertTrue(song.tracks[0].hits.isEmpty())
        assertTrue(song.tracks[1].spans.isEmpty())
        assertTrue(song.tracks[1].hits.isEmpty())
    }

    @Test
    fun applyBounceResetsTheDestinationsSettings() {
        var song = Song.new("s")
        song = song.withTrack(0, song.tracks[0].copy(input = TrackInput.DRUMS, volume = -12, pan = -30))
        song = song.applyingBounce(sources = setOf(1), destination = 1, clip = UUID.randomUUID(), duration = 1.0)

        val track = song.tracks[0]
        assertEquals(0, track.volume)
        assertEquals(0, track.pan)
        assertEquals(TrackInput.DRUMS, track.input)
    }

    @Test
    fun applyBounceKeepsSourceSettingsAndOtherTracks() {
        var song = Song.new("s")
        song = song.withTrack(1, song.tracks[1].copy(volume = -6, spans = listOf(Span(0.0, 1.0, 2.0))))
        song = song.withTrack(2, song.tracks[2].copy(spans = listOf(Span(0.0, 1.0, 3.0))))
        song = song.applyingBounce(sources = setOf(1, 2), destination = 1, clip = UUID.randomUUID(), duration = 1.0)

        assertEquals(-6, song.tracks[1].volume)
        assertTrue(song.tracks[1].spans.isEmpty())
        assertEquals(listOf(Span(0.0, 1.0, 3.0)), song.tracks[2].spans)
    }

    @Test
    fun applyBounceIgnoresNumbersOutsideTheTracks() {
        var song = Song.new("s")
        song = song.withTrack(0, song.tracks[0].copy(spans = listOf(Span(0.0, 1.0, 1.0))))
        song = song.applyingBounce(sources = setOf(0, 9), destination = 7, clip = UUID.randomUUID(), duration = 1.0)
        assertEquals(listOf(Span(0.0, 1.0, 1.0)), song.tracks[0].spans)
    }

    // New: Review Focus 2.
    @Test
    fun aBounceToATrackThatIsNotThereChangesNothingButTheSources() {
        var song = Song.new("s")
        song = song.withTrack(0, song.tracks[0].copy(spans = listOf(Span(0.0, 1.0, 1.0)), hits = listOf(hit(at = 0.5))))
        song = song.withTrack(1, song.tracks[1].copy(spans = listOf(Span(0.0, 1.0, 2.0))))
        val clip = UUID.randomUUID()

        song = song.applyingBounce(sources = setOf(1, 2), destination = 5, clip = clip, duration = 1.0)

        assertTrue(song.tracks.all { it.spans.isEmpty() && it.hits.isEmpty() })
        assertTrue(song.tracks.none { track -> track.spans.any { it.clip == clip } })
    }

    @Test
    fun canBounceOnceSomethingIsRecorded() {
        var song = Song.new("s")
        assertFalse(song.canBounce)
        song = song.withTrack(0, song.tracks[0].copy(spans = listOf(Span(0.0, 1.0, 1.0))))
        assertTrue(song.canBounce)
    }

    @Test
    fun trimmingSilenceKeepsUpToTheLastSound() {
        val samples = Bounce.trimmedSilence(floatArrayOf(0.5f, 0f, 1e-3f, 1e-7f, 0f))
        assertContentEquals(floatArrayOf(0.5f, 0f, 1e-3f), samples)
    }

    @Test
    fun trimmingSilenceOfQuietIsEmpty() {
        assertEquals(0, Bounce.trimmedSilence(floatArrayOf(1e-7f, 0f)).size)
    }

    // These need ClipStore and MixLevels.renderStem, so they arrive with them.
    @Test
    fun bounceOfMicAndDrumsSumsAtVolumesIgnoringPan() {
        val rate = 1_000.0
        val clips = ClipStore()
        val clip = UUID.randomUUID()
        val index = assertNotNull(clips.add(FloatArray(500) { 0.5f }))
        val indexes = mapOf(clip to index)

        var song = Song.new("s")
        song = song.withTrack(1, song.tracks[1].copy(spans = listOf(Span(0.0, 0.5, 0.0, clip)), volume = -6, pan = 50))
        song = song.withTrack(2, song.tracks[2].copy(input = TrackInput.DRUMS, hits = listOf(hit(at = 0.25))))
        val voices = Sound.entries.map { Voice(FloatArray(64) { 0.5f }) }
        val length = 1.0

        fun stem(track: Track): FloatArray = assertNotNull(MixLevels.renderStem(track, length, clips, indexes, voices, rate))
        val mic = stem(song.tracks[1])
        val drums = stem(song.tracks[2])

        var mix = FloatArray(0)
        for (track in listOf(song.tracks[1], song.tracks[2])) {
            mix = Bounce.accumulate(stem(track), gain = track.volumeGain, into = mix)
        }

        assertEquals(maxOf(mic.size, drums.size), mix.size)
        assertTrue(drums.any { it != 0f })
        for (frame in mix.indices) {
            val expected = mic[frame] * 0.501_187f + drums[frame]
            assertTrue(abs(mix[frame] - expected) < 1e-4f)
        }
        assertTrue(abs(mix[100] - 0.5f * 0.501_187f) < 1e-4f)
    }

    @Test
    fun aHitAtTheEndKeepsItsTail() {
        val rate = 8_000.0
        var song = Song.new("s")
        song = song.withTrack(0, song.tracks[0].copy(hits = listOf(hit(at = 2.0))))
        val track = song.tracks[0]
        val voices = Sound.entries.map { Voice(FloatArray(64) { 0.5f }) }
        val bounce = Bounce.trimmedSilence(
            assertNotNull(MixLevels.renderStem(track, song.length, ClipStore(), emptyMap(), voices, rate)),
        )
        val hitFrame = (track.contentEnd * rate).toInt()

        assertTrue(bounce.size > (song.contentEnd * rate).toInt())
        assertTrue(bounce[hitFrame] != 0f)
        assertTrue(bounce[hitFrame + 32] != 0f)
    }
}
