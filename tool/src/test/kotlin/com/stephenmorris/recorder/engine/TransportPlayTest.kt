package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.tape.Arming
import com.stephenmorris.recorder.tape.Hit
import com.stephenmorris.recorder.tape.MixEvent
import com.stephenmorris.recorder.tape.Span
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransportPlayTest {
    private val fixture = TransportFixture()
    private val transport = fixture.transport
    private val model = fixture.model
    private val mix = fixture.mix

    private fun passes() = transportPasses(fixture)

    @Test
    fun playStartsAtThePlayheadAfterTheStartDelay() {
        fixture.advance(100)
        model.playhead.value = 4.0
        val at = mix.renderedFrames

        transport.togglePlay()
        fixture.advance(20)

        assertEquals(4.0 to at + 2_400, passes().first())
        assertTrue(transport.isPlaying.value)
        assertTrue(model.isFollowing)
    }

    @Test
    fun playFromTheEndStartsAtZero() {
        model.playhead.value = fixture.song.length

        transport.togglePlay()
        fixture.advance(20)

        assertEquals(0.0, passes().first().first)
    }

    @Test
    fun clipsAndHitsAreSentAheadOfTheClock() {
        val clip = fixture.clips.add(FloatArray(48_000) { 0.25f })!!
        model.updateTrack(fixture.song.tracks[1].id) { it.copy(spans = listOf(Span(0.0, 1.0, 0.0, clip))) }
        model.updateTrack(fixture.song.tracks[0].id) { it.copy(hits = listOf(Hit(0.1, Sound.SNARE, Level.HIT))) }

        transport.togglePlay()
        fixture.advance(20)
        // The hit is 0.15 s away (after the start delay), past the first tick's lookahead.
        assertTrue(mix.triggers.isEmpty())
        fixture.advance(20)

        val play = mix.events.filterIsInstance<MixEvent.PlayClip>().single()
        assertEquals(1, play.slot)
        assertEquals(fixture.clips.index(clip), play.clip)
        val hit = mix.triggers.single()
        assertEquals(0, hit.bus)
        assertEquals(RecorderKit.voice(Sound.SNARE), hit.voice)
        assertTrue(mix.triggers.all { it.frame <= mix.renderedFrames + fixture.frames(0.12) })
    }

    @Test
    fun tracksSendTheirGainsOncePerChange() {
        fixture.advance(60)
        assertEquals(4, mix.events.filterIsInstance<MixEvent.SetTrack>().size)
        assertEquals(4, mix.events.filterIsInstance<MixEvent.SetDrums>().size)

        model.updateTrack(fixture.song.tracks[2].id) { it.copy(pan = -50) }
        fixture.advance(20)

        val tracks = mix.events.filterIsInstance<MixEvent.SetTrack>()
        assertEquals(5, tracks.size)
        assertEquals(MixEvent.SetTrack(2, 1f, 0f), tracks.last())
    }

    @Test
    fun thePlayheadFollowsTheHeardFrame() {
        fixture.output.latencyFrames = 4_800

        transport.togglePlay()
        fixture.advance(1_000)

        // 1 s rendered, less the 0.05 s start delay and 0.1 s of latency.
        assertTrue(abs(model.playhead.value - 0.85) < 0.021, "playhead ${model.playhead.value}")
    }

    @Test
    fun playbackStopsAtTheSongsEnd() {
        model.playhead.value = fixture.song.length - 0.5

        transport.togglePlay()
        fixture.advance(1_000)

        assertFalse(transport.isPlaying.value)
    }

    @Test
    fun stopSilencesEverything() {
        transport.togglePlay()
        fixture.advance(100)

        transport.stop()

        assertTrue(mix.events.last() is MixEvent.StopClips)
        assertEquals(1, mix.stopVoicesCount)
        assertFalse(transport.isPlaying.value)
    }

    @Test
    fun seekPlaysOnFromThePlayhead() {
        transport.togglePlay()
        fixture.advance(200)
        model.playhead.value = 12.0

        transport.seek()
        fixture.advance(20)

        assertEquals(12.0, passes().last().first)
        assertTrue(transport.isPlaying.value)
    }

    @Test
    fun theOutputRunsOnlyWhileASongIsOpen() {
        fixture.advance(20)
        assertTrue(fixture.output.isRunning)

        model.close()
        fixture.advance(20)
        assertFalse(fixture.output.isRunning)

        model.open(model.songs.value[0].id)
        fixture.advance(20)
        assertTrue(fixture.output.isRunning)
    }

    @Test
    fun pauseStopsAndReleasesAudio() {
        model.arming.value = Arming().toggling(fixture.song.tracks[1])
        transport.togglePlay()
        fixture.advance(100)
        assertTrue(fixture.input.isOpen)

        transport.pause()
        fixture.advance(100)

        assertFalse(transport.isPlaying.value)
        assertFalse(fixture.output.isRunning)
        assertFalse(fixture.input.isOpen)

        transport.resume()
        fixture.advance(20)
        assertTrue(fixture.output.isRunning)
    }

    @Test
    fun armingAMicTrackOpensTheInput() {
        fixture.advance(20)
        assertFalse(fixture.input.isOpen)

        transport.toggleArm(fixture.song.tracks[1].id)
        fixture.advance(20)

        assertTrue(fixture.input.isOpen)
        assertEquals(fixture.song.tracks[1].id, model.arming.value.audio)

        transport.toggleArm(fixture.song.tracks[1].id)
        fixture.advance(20)
        assertFalse(fixture.input.isOpen)
    }

    @Test
    fun aRefusedMicArmsNothingAndIsNotRetried() {
        fixture.input.granted = false

        transport.toggleArm(fixture.song.tracks[1].id)
        fixture.advance(200)

        assertTrue(model.arming.value.isEmpty)
        assertEquals(1, fixture.input.opens)
        assertTrue(transport.isMicRefused.value)
    }

    @Test
    fun metersReadTheirPeaks() {
        model.arming.value = Arming().toggling(fixture.song.tracks[1])
        fixture.inputLevel = 1f
        fixture.advance(40)

        assertEquals(1.0, transport.inputLevel())
        assertTrue(transport.isClipping.value)
        fixture.mix.trackPeaks[2] = 1f
        assertEquals(1.0, transport.playbackLevel(fixture.song.tracks[2].id))
    }

    // Review: a closed song's recordings are let go, so memory doesn't grow with every song opened.
    @Test
    fun closingASongReleasesItsRecordings() {
        val clip = fixture.clips.add(FloatArray(48_000))!!
        val index = fixture.clips.index(clip)!!

        model.close()

        assertNull(fixture.clips.index(clip))
        assertNull(fixture.clips.samples(clip))
        assertTrue(fixture.clips.indexes.isEmpty())
        assertNull(fixture.clips.clipStore.samples(index))
    }
}

/** The tape passes the transport has heard of, as (time, frame). */
fun transportPasses(fixture: TransportFixture): List<Pair<Double, Long>> = fixture.transport.passesForTest()
