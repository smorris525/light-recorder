package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TapeViewTest {
    @Test
    fun standardViewIsANewSongsBlankTape() {
        assertEquals(30.0, TapeView().secondsAcross)
        assertEquals(Song.BLANK_TAPE, TapeView().secondsAcross)
        assertEquals(TapeView.STANDARD_SECONDS_ACROSS, TapeView().secondsAcross)
    }

    @Test
    fun clampedNeverLeavesTheSongOrZooms() {
        assertEquals(12.0, TapeView(start = 40.0).clamped(songLength = 42.0).start)
        assertEquals(0.0, TapeView(start = -5.0).clamped(songLength = 100.0).start)
        val short = TapeView(start = 5.0).clamped(songLength = 30.0)
        assertEquals(0.0, short.start)
        assertEquals(30.0, short.secondsAcross)
    }

    @Test
    fun scrollingMovesByTheDragAndStaysInTheSong() {
        val view = TapeView(start = 10.0, secondsAcross = 30.0)

        assertEquals(20.0, view.scrolled(dx = 100.0, width = 300.0, songLength = 100.0).start)
        assertEquals(0.0, view.scrolled(dx = -1000.0, width = 300.0, songLength = 100.0).start)
    }

    @Test
    fun positionsRoundTrip() {
        val view = TapeView(start = 10.0, secondsAcross = 30.0)

        assertEquals(150.0, view.x(25.0, width = 300.0))
        assertEquals(25.0, view.time(150.0, width = 300.0))
    }

    @Test
    fun followingPagesOnce() {
        val view = TapeView(start = 0.0, secondsAcross = 10.0)

        assertEquals(view, view.following(9.0, songLength = 100.0))
        assertEquals(10.1, view.following(10.1, songLength = 100.0).start)
        assertEquals(3.0, TapeView(start = 20.0, secondsAcross = 10.0).following(3.0, songLength = 100.0).start)
    }

    @Test
    fun scaleCoarsensAsYouZoomOut() {
        val width = 320.0
        assertEquals(RulerScale(major = 0.5, minor = 0.05), RulerScale.forView(secondsAcross = 2.0, width = width))
        assertEquals(RulerScale(major = 10.0, minor = 1.0), RulerScale.forView(secondsAcross = 30.0, width = width))
        val far = RulerScale.forView(secondsAcross = 600.0, width = width)
        assertTrue(far.minor / 600 * width >= 6)
        assertTrue(far.major / 600 * width >= 44)
        assertTrue(far == RulerScale(major = 600.0, minor = 60.0) || far == RulerScale(major = 60.0, minor = 10.0))
    }

    @Test
    fun labels() {
        assertEquals("1:05", RulerScale.label(65.0, RulerScale(major = 10.0, minor = 1.0)))
        assertEquals("1:05.5", RulerScale.label(65.5, RulerScale(major = 1.0, minor = 0.1)))
        assertEquals("1:05.55", RulerScale.label(65.55, RulerScale(major = 0.5, minor = 0.05)))
    }

    @Test
    fun snapsToMinorTicks() {
        assertEquals(3.0, RulerScale(major = 10.0, minor = 1.0).snap(3.4))
    }
}
