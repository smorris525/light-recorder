package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BounceChoiceTest {
    private val fixture = TransportFixture()
    private val model = fixture.model
    private val choice = BounceChoice()

    /** A second of audio on Track [number]. */
    private fun record(number: Int) {
        val clip = fixture.clips.add(FloatArray(48_000) { 0.3f })!!
        model.updateTrack(fixture.track(number - 1).id) { it.copy(spans = listOf(Span(0.0, 1.0, 0.0, clip))) }
    }

    @Test
    fun opensWithNothingFromAndTrack1To() {
        assertEquals(emptySet(), choice.sources.value)
        assertEquals(1, choice.destination.value)
        assertEquals("None", choice.summary())
        assertFalse(choice.canBounce)
    }

    @Test
    fun anEmptyTrackCantBePicked() {
        choice.toggle(2, model.song)
        assertEquals(emptySet(), choice.sources.value)
        record(2)
        choice.toggle(2, model.song)
        assertEquals(setOf(2), choice.sources.value)
        choice.toggle(2, model.song)
        assertEquals(emptySet(), choice.sources.value)
    }

    @Test
    fun bounceNeedsATrackOtherThanJustTo() {
        record(1)
        record(2)
        choice.toggle(1, model.song)
        assertFalse(choice.canBounce)
        choice.pickDestination(2)
        assertTrue(choice.canBounce)
        choice.pickDestination(1)
        choice.toggle(2, model.song)
        assertTrue(choice.canBounce)
    }

    @Test
    fun warnsWhenToHasARecordingThatIsntASource() {
        record(1)
        record(2)
        choice.toggle(2, model.song)
        assertTrue(choice.replacesRecording(model.song))
        choice.toggle(1, model.song)
        assertFalse(choice.replacesRecording(model.song))
    }

    @Test
    fun noWarningWithNothingFrom() {
        record(1)
        assertFalse(choice.replacesRecording(model.song))
    }

    @Test
    fun bouncingRefusesDuringATake() = runBlocking {
        record(2)
        choice.toggle(2, model.song)
        choice.pickDestination(3)
        fixture.transport.toggleArm(fixture.track(3).id)
        fixture.transport.record()
        fixture.advance(4_000)
        val before = model.song.tracks[2]

        val measurer = MixMeasurer(model, fixture.transport, fixture.clips, fixture.store, Dispatchers.Unconfined)
        assertFalse(choice.bounce(measurer))
        assertEquals(before, model.song.tracks[2])
        assertFalse(choice.isBouncing.value)
    }

    @Test
    fun bouncingWritesTheMixToTo() = runBlocking {
        record(2)
        choice.toggle(2, model.song)
        choice.pickDestination(3)

        val measurer = MixMeasurer(model, fixture.transport, fixture.clips, fixture.store, Dispatchers.Unconfined)
        assertTrue(choice.bounce(measurer))
        assertEquals(1, model.song.tracks[2].spans.size)
    }
}
