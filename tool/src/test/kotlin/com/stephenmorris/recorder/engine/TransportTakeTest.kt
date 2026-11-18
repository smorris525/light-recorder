package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.MixEvent
import com.stephenmorris.recorder.tape.Span
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransportTakeTest {
    private val fixture = TransportFixture()
    private val transport = fixture.transport
    private val model = fixture.model
    private val mic = fixture.track(1).id

    private fun armMic() = transport.toggleArm(mic)

    private fun spans() = fixture.model.track(mic)!!.spans

    private fun silences() = fixture.mix.events.filterIsInstance<MixEvent.SilenceTrack>()

    @Test
    fun recordFromStoppedPlaysALeadInAndCounts() {
        armMic()
        model.playhead.value = 10.0

        transport.record()
        val counts = mutableListOf<Int?>()
        repeat(170) {
            fixture.advance(20)
            counts += transport.countNumber.value
        }

        assertEquals(7.0, transportPasses(fixture).first().first)
        assertEquals(listOf(3, 2, 1, null), counts.distinct())
        assertTrue(assertNotNull(transport.take.value).isRecording)
    }

    @Test
    fun aLeadInNearZeroStartsTheTapeAtZero() {
        armMic()
        model.playhead.value = 1.0
        val at = fixture.mix.renderedFrames

        transport.record()
        // 2 s of silence, then the tape's last second before the take.
        fixture.advance(2_100)

        assertEquals(0.0 to at + fixture.frames(0.05 + 3 - 1.0), transportPasses(fixture).first())
        assertEquals(at + fixture.frames(3.05), transport.take.value?.recordFrame)
    }

    @Test
    fun recordWhilePlayingWithNoInPointPunchesInAtOnce() {
        armMic()
        transport.togglePlay()
        fixture.advance(1_000)
        val heard = fixture.mix.renderedFrames

        transport.record()

        val take = assertNotNull(transport.take.value)
        assertEquals(heard, take.recordFrame)
        assertTrue(abs(take.recordTime - model.playhead.value) < 0.021)
        assertNull(transport.countNumber.value)
    }

    @Test
    fun recordingStartsAtTheInPoint() {
        armMic()
        model.updateSong { it.settingIn(5.0) }
        model.playhead.value = 12.0

        transport.record()

        assertEquals(5.0, transport.take.value?.recordTime)
    }

    @Test
    fun theTakeStopsByItselfAtTheOutPoint() {
        armMic()
        model.updateSong { it.settingOut(12.0) }
        model.playhead.value = 10.0

        transport.record()
        fixture.advance(6_000)

        assertFalse(transport.isPlaying.value)
        assertNull(transport.take.value)
        val span = spans().single()
        assertEquals(10.0, span.start)
        assertEquals(12.0, span.end, 1e-9)
    }

    @Test
    fun aFinishedTakeIsFadedSavedAndPlaced() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(4_060)

        transport.record()
        fixture.advance(100)

        val span = spans().single()
        val clip = assertNotNull(span.clip)
        val samples = assertNotNull(fixture.clips.samples(clip))
        assertEquals(10.0, span.start)
        assertEquals(0f, samples[0])
        assertEquals(0.5f, samples[samples.size / 2])
        assertTrue(abs(span.end - span.start - 1.0) < 0.05, "length ${span.end - span.start}")
        assertNotNull(fixture.store.loadClip(clip, 48_000.0))
        assertTrue(transport.isPlaying.value)

        transport.stop()
        assertEquals(10.0, model.playhead.value)
    }

    @Test
    fun theTakesTrackIsSilencedOverIt() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        val take = assertNotNull(transport.take.value)
        fixture.advance(4_060)
        transport.record()
        fixture.advance(40)

        val silences = silences()
        assertEquals(MixEvent.SilenceTrack(1, take.recordFrame, Long.MAX_VALUE), silences.first())
        assertEquals(1, silences.last().slot)
        assertEquals(take.recordFrame, silences.last().fromFrame)
        assertTrue(silences.last().toFrame < Long.MAX_VALUE)
    }

    @Test
    fun recordAgainDuringTheLeadInAbandonsTheTake() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(1_000)

        transport.record()
        fixture.advance(100)

        assertTrue(spans().isEmpty())
        assertNull(transport.take.value)
        assertNull(transport.liveTake.value)
        assertEquals(MixEvent.SilenceTrack(1, 0, 0), silences().last())
        assertFalse(transport.isPlaying.value)
        // It stays where the lead-in had got to: 1 s in, less the start delay.
        assertEquals(7.95, model.playhead.value, 0.021)
    }

    @Test
    fun stopMidTakeKeepsWhatWasRecorded() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(4_060)

        transport.stop()
        fixture.advance(100)

        val span = spans().single()
        assertTrue(abs(span.end - span.start - 1.0) < 0.05)
        assertEquals(10.0, model.playhead.value)
    }

    @Test
    fun pauseMidTakeKeepsWhatWasRecordedAndClosesTheMic() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(4_060)

        transport.pause()

        val span = spans().single()
        assertTrue(abs(span.end - span.start - 1.0) < 0.05)
        assertNotNull(fixture.store.loadClip(span.clip!!, 48_000.0))
        assertFalse(fixture.input.isOpen)
        assertFalse(transport.isPlaying.value)
        assertNull(transport.take.value)
        assertEquals(10.0, model.playhead.value)
    }

    @Test
    fun withoutTheMicOnlyTheDrumTakeRuns() {
        fixture.input.granted = false
        armMic()
        transport.toggleArm(fixture.track(0).id)

        transport.record()

        assertNull(transport.take.value)
        assertNotNull(transport.patternTake.value)
    }

    @Test
    fun nothingArmedRecordsNothing() {
        transport.record()
        fixture.advance(100)

        assertNull(transport.take.value)
        assertFalse(transport.isPlaying.value)
    }

    @Test
    fun aTwoSecondStallLosesNothing() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(4_000)
        fixture.inputLevel = 0.25f
        fixture.stall(2_000)
        fixture.advance(20)
        transport.record()
        fixture.advance(100)

        // Recording began 3.05 s after ●; the mic gave 0.5 until 4 s, then 0.25 through the stall and after it.
        val span = spans().single()
        val samples = fixture.clips.samples(span.clip!!)!!
        val fade = fixture.frames(0.005).toInt()
        val switch = (fixture.frames(4.0) - fixture.frames(3.05)).toInt()
        assertEquals(10.0, span.start)
        assertTrue((fade until switch).all { samples[it] == 0.5f })
        assertTrue((switch until samples.size - fade).all { samples[it] == 0.25f })
        assertEquals(fixture.frames(6.02 - 3.05).toInt(), samples.size)
    }

    @Test
    fun theLiveTakeShowsTheTakesLevelAsItRecords() {
        armMic()
        model.playhead.value = 10.0
        transport.record()
        fixture.advance(4_060)

        val live = assertNotNull(transport.liveTake.value)
        assertEquals(mic, live.track)
        assertEquals(0.5f, live.levels.level(10.2, 10.3))
    }
}
