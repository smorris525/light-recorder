package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.tape.Hit
import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MixMeasurerTest {
    private val fixture = TransportFixture()
    private val model = fixture.model
    private val drums = fixture.track(0).id
    private val mic = fixture.track(1).id

    private fun measurer(background: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) =
        MixMeasurer(model, fixture.transport, fixture.clips, fixture.store, background)

    /** One second of [level] on the Microphone track from 1 s, and a snare on the Drums track at 0.5 s. */
    private fun record(level: Float = 0.4f) {
        val clip = fixture.clips.add(FloatArray(48_000) { level })!!
        model.updateTrack(mic) { it.copy(spans = listOf(Span(1.0, 2.0, 0.0, clip))) }
        model.updateTrack(drums) { it.copy(hits = listOf(Hit(0.5, Sound.SNARE, Level.ACCENT))) }
    }

    @Test
    fun measuringShowsTheSavedMixFirst() = runBlocking {
        record()
        val saved = MixLevels(48_000.0, floatArrayOf(0.123f))
        fixture.store.saveMix(saved, model.song.id)
        val manual = ManualDispatcher()
        val measurer = measurer(manual)

        val job = launch { measurer.measure(model.song) }
        yield()
        assertEquals(saved, measurer.mixLevels.value)

        manual.runAll()
        job.join()
        val levels = assertNotNull(measurer.mixLevels.value)
        assertTrue(abs(assertNotNull(levels.level(1.2, 1.8)) - 0.4f) < 0.001f)
        assertEquals(levels, fixture.store.loadMix(model.song.id))
    }

    @Test
    fun aFaderMoveSumsWithoutRenderingAgain() = runBlocking {
        record()
        val measurer = measurer()
        measurer.measure(model.song)
        assertEquals(1, measurer.stemsRendered)

        model.updateTrack(mic) { it.copy(volume = 6) }
        measurer.measure(model.song)

        assertEquals(1, measurer.stemsRendered)
        val level = assertNotNull(measurer.mixLevels.value?.level(1.2, 1.8))
        assertTrue(abs(level - 0.4f * 1.995f) < 0.01f)
    }

    @Test
    fun aStaleStemIsRenderedAgain() = runBlocking {
        record()
        val measurer = measurer()
        measurer.measure(model.song)

        model.updateTrack(drums) { it.copy(hits = it.hits + Hit(3.0, Sound.KICK, Level.HIT)) }
        measurer.measure(model.song)

        assertEquals(2, measurer.stemsRendered)
    }

    @Test
    fun measuringTheSameSongTwiceDoesNothing() = runBlocking {
        record()
        val measurer = measurer()
        measurer.measure(model.song)
        val first = measurer.mixLevels.value

        measurer.measure(model.song.copy(name = "renamed"))

        assertSame(first, measurer.mixLevels.value)
        assertEquals(1, measurer.stemsRendered)
    }

    @Test
    fun aMeasurementForAClosedSongIsDropped() = runBlocking {
        record()
        val manual = ManualDispatcher()
        val measurer = measurer(manual)
        val song = model.song

        val job = launch { measurer.measure(song) }
        yield()
        model.close()
        manual.runAll()
        job.join()

        assertNull(measurer.mixLevels.value)
        assertNull(fixture.store.loadMix(song.id))
    }

    @Test
    fun bounceReplacesTheSourcesWithOneRecording() = runBlocking {
        record()
        val measurer = measurer()

        assertTrue(measurer.bounce(setOf(1, 2), destination = 3))

        val song = model.song
        assertTrue(song.tracks[0].hits.isEmpty())
        assertTrue(song.tracks[1].spans.isEmpty())
        val span = song.tracks[2].spans.single()
        val samples = assertNotNull(fixture.clips.samples(span.clip!!))
        assertEquals(0.0, span.start)
        assertEquals(samples.size / 48_000.0, span.end, 1e-9)
        assertEquals(0.4f, samples[(1.5 * 48_000).toInt()], 1e-6f)
        assertTrue(span.end < 2.1, "trimmed to ${span.end}")
    }

    @Test
    fun aBounceOfSilenceChangesNothing() = runBlocking {
        record(level = 0f)
        model.updateTrack(drums) { it.copy(hits = emptyList()) }
        val before = model.song

        assertFalse(measurer().bounce(setOf(2), destination = 3))

        assertEquals(before, model.song)
    }

    @Test
    fun aBounceWhoseSourcesChangedIsDropped() = runBlocking {
        record()
        val manual = ManualDispatcher()
        val measurer = measurer(manual)

        val bounced = async { measurer.bounce(setOf(2), destination = 3) }
        yield()
        model.updateTrack(mic) { it.copy(volume = -6) }
        manual.runAll()

        assertFalse(bounced.await())
        assertTrue(model.song.tracks[2].spans.isEmpty())
        assertEquals(1, model.song.tracks[1].spans.size)
    }

    @Test
    fun noBounceDuringATake() = runBlocking {
        record()
        fixture.transport.toggleArm(drums)
        fixture.transport.record()

        assertFalse(measurer().bounce(setOf(2), destination = 3))
    }

    @Test
    fun mixdownCoversToTheLastSound() = runBlocking {
        record()

        val job = assertNotNull(measurer().mixdown { })

        // The take ends at 2 s; a few frames of its fade-free edge may be quiet, but nothing past 2 s is kept.
        assertTrue(abs(job.mixdown.frameCount - 96_000) < 10, "frames ${job.mixdown.frameCount}")
        assertEquals(48_000.0, job.sampleRate)
    }

    @Test
    fun mixdownReportsProgressInOrderToTheEncodeShare() = runBlocking {
        record()
        val reports = mutableListOf<Double>()

        val job = assertNotNull(measurer().mixdown { reports += it })

        assertTrue(reports.isNotEmpty())
        assertEquals(reports.sorted(), reports)
        assertEquals(2 / 2.2, job.encodeStart, 1e-9)
        assertTrue(reports.last() <= job.encodeStart + 1e-9)
    }

    @Test
    fun noMixdownWithNothingRecorded() = runBlocking {
        assertNull(measurer().mixdown { })
    }

    @Test
    fun aMeasurementOfAnOlderVersionOfTheSongIsDropped() = runBlocking {
        record()
        val manual = ManualDispatcher()
        val measurer = measurer(manual)

        val job = launch { measurer.measure(model.song) }
        yield()
        model.updateTrack(mic) { it.copy(volume = -6) }
        manual.runAll()
        job.join()

        assertNull(measurer.mixLevels.value)
    }
}
