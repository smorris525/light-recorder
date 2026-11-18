package com.stephenmorris.recorder.panel

import com.stephenmorris.recorder.tape.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class SliderMathTest {
    @Test
    fun nearHomeCatches() {
        assertEquals(SliderMath.Slide(0.0, true), SliderMath.slide(54.0, 100.0, -50.0..50.0, 0.0))
    }

    @Test
    fun pastTheDetentDoesnt() {
        assertEquals(SliderMath.Slide(9.0, false), SliderMath.slide(59.0, 100.0, -50.0..50.0, 0.0))
    }

    @Test
    fun withNoHomeNothingCatches() {
        assertEquals(SliderMath.Slide(1.0, false), SliderMath.slide(51.0, 100.0, -50.0..50.0, null))
    }

    @Test
    fun aSlideStaysWithinTheRail() {
        assertEquals(SliderMath.Slide(50.0, false), SliderMath.slide(140.0, 100.0, -50.0..50.0, 0.0))
    }

    @Test
    fun volumeBelowTheRangeIsSilent() {
        assertEquals(Track.SILENT, SliderMath.volume(-48.6))
    }

    @Test
    fun volumeRoundsHalfAway() {
        assertEquals(-3, SliderMath.volume(-2.5))
    }

    @Test
    fun panSnapsToFives() {
        assertEquals(10, SliderMath.pan(12.4))
        assertEquals(15, SliderMath.pan(12.6))
    }

    @Test
    fun volumeHomeIsZeroDecibels() {
        assertEquals(49.0, SliderMath.position(0.0, 55.0, SliderMath.VOLUME_RANGE), 1e-9)
        assertEquals(49.0 / 55.0, SliderMath.VOLUME_HOME, 1e-12)
    }

    @Test
    fun aSilentTrackSitsAtTheFadersFoot() {
        assertEquals(-49.0, SliderMath.volumeValue(Track(number = 1, input = com.stephenmorris.recorder.tape.TrackInput.MIC, volume = Track.SILENT)))
    }
}
