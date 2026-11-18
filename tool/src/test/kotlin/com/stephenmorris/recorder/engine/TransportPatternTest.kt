package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.tape.MixEvent
import com.stephenmorris.recorder.tape.MixRenderer
import com.stephenmorris.recorder.tape.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransportPatternTest {
    private val fixture = TransportFixture()
    private val transport = fixture.transport
    private val model = fixture.model
    private val mix = fixture.mix
    private val drums = fixture.track(0).id
    private val patternA = fixture.song.patterns[0].id
    private val clickVoices = ClickKind.entries.map { RecorderKit.clickVoice(it) }.toSet()

    private fun hits() = model.track(drums)!!.hits

    private fun recordDrumsFromZero() {
        transport.toggleArm(drums)
        transport.record()
    }

    @Test
    fun aDrumTakeWritesItsHitsOntoTheTrack() {
        recordDrumsFromZero()
        fixture.advance(5_480)
        transport.record()
        assertTrue(hits().isEmpty())

        fixture.advance(300)

        val hits = hits()
        assertEquals(0.0, hits.first().time)
        assertEquals(Sound.KICK, hits.first().sound)
        assertTrue(hits.any { it.sound == Sound.KICK && it.time == 2.4 })
        assertTrue(hits.all { it.time < 2.5 })
        assertNull(transport.patternTake.value)
    }

    @Test
    fun selectingDuringATakeQueuesThePattern() {
        val (withB, patternB) = model.song.addingPattern()
        model.updateSong { withB.copy(selectedPattern = patternA) }
        recordDrumsFromZero()
        fixture.advance(3_200)

        transport.select(patternB)

        assertEquals(patternB, model.song.selectedPattern)
        assertEquals(patternB, transport.patternTake.value?.queued)
        assertEquals(patternA, transport.patternTake.value?.playing)
    }

    @Test
    fun playOnAPatternsScreensLoopsItAlone() {
        model.auditionPattern = patternA

        transport.togglePlay()
        fixture.advance(500)

        assertTrue(transport.isPlaying.value)
        assertTrue(mix.triggers.isNotEmpty())
        assertTrue(mix.triggers.all { it.bus == MixRenderer.UNITY_BUS })
        assertTrue(transportPasses(fixture).isEmpty())
        assertEquals(patternA, model.playingStep.value?.pattern)
    }

    @Test
    fun leavingThePatternsScreensStopsTheAudition() {
        model.auditionPattern = patternA
        transport.togglePlay()
        fixture.advance(100)

        model.auditionPattern = null
        fixture.advance(20)

        assertFalse(transport.isPlaying.value)
        assertNull(model.playingStep.value)
    }

    @Test
    fun theClickPlaysOnlyWhenAskedAndNeverInATake() {
        model.auditionPattern = patternA
        model.clicks = true
        transport.togglePlay()
        fixture.advance(1_000)
        assertTrue(mix.triggers.any { it.voice in clickVoices })

        transport.stop()
        mix.triggers.clear()
        model.clicks = false
        transport.togglePlay()
        fixture.advance(1_000)
        assertTrue(mix.triggers.none { it.voice in clickVoices })

        transport.stop()
        model.auditionPattern = null
        mix.triggers.clear()
        model.clicks = true
        recordDrumsFromZero()
        fixture.advance(5_000)
        assertTrue(mix.triggers.isNotEmpty())
        assertTrue(mix.triggers.none { it.voice in clickVoices })
    }

    @Test
    fun aPadSoundsAtOnceOnTheUnityBus() {
        fixture.advance(100)
        val now = mix.renderedFrames

        transport.hit(Sound.SNARE, Level.ACCENT)
        transport.hit(Sound.SNARE, Level.HIT)

        assertEquals(
            listOf(
                Trigger(MixRenderer.UNITY_BUS, RecorderKit.voice(Sound.SNARE), now, 1f),
                Trigger(MixRenderer.UNITY_BUS, RecorderKit.voice(Sound.SNARE), now, 0.85f),
            ),
            mix.triggers,
        )
    }

    @Test
    fun padRecordingLandsOnTheNearestStep() {
        model.auditionPattern = patternA
        transport.recordIntoPattern(patternA)
        assertTrue(transport.isPadRecording.value)
        assertTrue(transport.isPlaying.value)
        fixture.advance(400)

        transport.hit(Sound.SHAKER, Level.HIT)

        assertEquals(1, model.pattern(patternA)!!.steps.hits(Sound.SHAKER).size)

        transport.recordIntoPattern(patternA)
        assertFalse(transport.isPadRecording.value)
        transport.hit(Sound.TOM, Level.HIT)
        assertTrue(model.pattern(patternA)!!.steps.hits(Sound.TOM).isEmpty())
    }

    @Test
    fun stoppingMidPatternTakeKeepsWhatWasHeard() {
        recordDrumsFromZero()
        fixture.advance(4_060)

        transport.stop()

        val hits = hits()
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.time < 1.0 })
        assertTrue(hits.any { it.time == 0.0 })
    }

    @Test
    fun theLiveHitsShowTheTakeAsItPlays() {
        recordDrumsFromZero()
        fixture.advance(3_400)

        val live = assertNotNull(transport.liveHits.value)
        assertEquals(drums, live.track)
        assertFalse(live.isWritten)
        assertNotNull(live.levels.level(0.0, 0.02))
    }

    @Test
    fun aPatternTakeOverAudioPlaysTheAudioAgainAfterIt() {
        val clip = fixture.clips.add(FloatArray(48_000 * 30) { 0.1f })!!
        model.updateTrack(drums) { it.copy(spans = listOf(Span(0.0, 30.0, 0.0, clip))) }
        transport.toggleArm(drums)
        transport.togglePlay()
        fixture.advance(500)
        val before = mix.events.count { it is MixEvent.StopClips }

        transport.record()
        fixture.advance(1_000)
        assertEquals(before + 1, mix.events.count { it is MixEvent.StopClips })
        assertTrue(mix.events.last { it is MixEvent.PlayClip || it is MixEvent.StopClips } is MixEvent.PlayClip)

        transport.record()
        fixture.advance(500)
        assertEquals(before + 2, mix.events.count { it is MixEvent.StopClips })
        assertTrue(mix.events.last { it is MixEvent.PlayClip || it is MixEvent.StopClips } is MixEvent.PlayClip)
    }

    @Test
    fun clearLiveDropsOnlyWhatTheMixNowHolds() {
        val mic = fixture.track(1).id
        transport.toggleArm(mic)
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(4_060)
        transport.stop()
        fixture.advance(40)
        val live = assertNotNull(transport.liveTake.value)
        assertNotNull(live.clip)

        transport.clearLive(Song0)
        assertNotNull(transport.liveTake.value)

        transport.clearLive(model.song)
        assertNull(transport.liveTake.value)
    }

    private companion object {
        val Song0 = com.stephenmorris.recorder.tape.Song.new("other")
    }
}
