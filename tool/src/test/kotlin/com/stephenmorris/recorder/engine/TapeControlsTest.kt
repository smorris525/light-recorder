package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A new song is 30 s long and its view shows 30 s; 300 dp across gives ticks every second and labels every 10. */
class TapeControlsTest {
    private val fixture = TransportFixture()
    private val model = fixture.model
    private val transport = fixture.transport
    private val controls = TapeControls(model, transport)
    private val mic = fixture.track(1).id
    private val width = 300.0

    private fun startTake() {
        transport.toggleArm(mic)
        transport.record()
        fixture.advance(4_000)
    }

    @Test
    fun scrubbingRoundsToHundredths() {
        controls.scrub(123.456, width)
        assertEquals(12.35, model.playhead.value, 1e-9)
    }

    @Test
    fun scrubbingStaysWithinTheSong() {
        controls.scrub(-10.0, width)
        assertEquals(0.0, model.playhead.value)
        controls.scrub(400.0, width)
        assertEquals(30.0, model.playhead.value)
    }

    @Test
    fun scrubbingCountsTicksCrossed() {
        controls.scrub(123.456, width)
        val first = assertNotNull(controls.scrub.value)
        assertEquals(1, first.ticksCrossed)
        assertFalse(first.isMajor)

        controls.scrub(200.0, width)
        val second = assertNotNull(controls.scrub.value)
        assertEquals(2, second.ticksCrossed)
        assertTrue(second.isMajor)

        controls.scrub(200.4, width)
        assertEquals(2, controls.scrub.value?.ticksCrossed)
    }

    @Test
    fun scrubbingMarksTheScrubAndEndingSeeks() {
        transport.togglePlay()
        fixture.advance(500)

        controls.scrub(150.0, width)
        assertTrue(model.isScrubbingPlayhead)
        controls.endScrub()
        fixture.advance(400)

        assertFalse(model.isScrubbingPlayhead)
        assertNull(controls.scrub.value)
        assertTrue(transport.isPlaying.value)
        assertTrue(model.playhead.value in 15.0..15.5, "playhead ${model.playhead.value}")
    }

    @Test
    fun scrubIsIgnoredDuringATake() {
        startTake()
        val before = model.playhead.value

        controls.scrub(250.0, width)

        assertEquals(before, model.playhead.value)
        assertNull(controls.scrub.value)
    }

    @Test
    fun setInNeedsToBeBeforeTheOutPoint() {
        model.playhead.value = 5.0
        controls.setOut()
        model.playhead.value = 6.0

        controls.setIn()

        assertNull(model.song.inPoint)
        assertEquals(5.0, model.song.outPoint)
    }

    @Test
    fun setOutNotAtZero() {
        controls.setOut()
        assertNull(model.song.outPoint)
    }

    @Test
    fun pointsAreLockedDuringATake() {
        model.playhead.value = 1.0
        controls.setIn()
        model.playhead.value = 0.0
        startTake()

        controls.clearIn()

        assertEquals(1.0, model.song.inPoint)
    }

    @Test
    fun scrollingByHandStopsFollowing() {
        model.isFollowing = true
        model.updateTrack(mic) { it.copy(spans = listOf(com.stephenmorris.recorder.tape.Span(0.0, 40.0, 0.0))) }

        controls.scroll(30.0, width)

        assertFalse(model.isFollowing)
        assertEquals(3.0, model.song.view.start, 1e-9)
    }

    @Test
    fun steppingSnapsToTheNextTickAndSeeks() {
        model.playhead.value = 3.04
        controls.stepPlayhead(1, width)
        assertEquals(4.0, model.playhead.value, 1e-9)
    }

    @Test
    fun tappingTheMarkerFollowsAgain() {
        controls.followAgain()
        assertTrue(model.isFollowing)
    }
}
