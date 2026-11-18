package com.stephenmorris.recorder.tape

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TakeTest {
    @Test
    fun edgesFadeInAndOut() {
        val samples = FloatArray(1_000) { 1f }

        fadeEdges(samples, frames = 100)

        assertEquals(0f, samples[0])
        assertTrue(samples[50] > 0.4f && samples[50] < 0.6f)
        assertEquals(1f, samples[500])
        assertTrue(samples[999] < 0.02f)
    }

    @Test
    fun aTakeShorterThanItsFadesSurvives() {
        val samples = FloatArray(10) { 1f }

        fadeEdges(samples, frames = 100)

        assertTrue(samples.all { it.isFinite() && it <= 1f })
    }

    @Test
    fun centredAtZeroDecibelsIsUnity() {
        val gains = Track(number = 1, input = TrackInput.MIC).gains

        assertEquals(1f, gains.left)
        assertEquals(1f, gains.right)
    }

    @Test
    fun panningRightTurnsTheLeftDown() {
        val track = Track(number = 1, input = TrackInput.MIC, pan = 50)

        assertEquals(0f, track.gains.left)
        assertEquals(1f, track.gains.right)
    }

    @Test
    fun silentIsZero() {
        val silent = Track(number = 1, input = TrackInput.MIC, volume = Track.SILENT)

        assertEquals(0f, silent.gains.left)
        assertEquals(0f, silent.gains.right)
    }

    @Test
    fun sixDecibelsDoublesTheGain() {
        val track = Track(number = 1, input = TrackInput.MIC, volume = 6)

        assertTrue(abs(track.gains.left - 2) < 0.01f)
    }

    // From SongTests: volumeGain lives with gains.
    @Test
    fun volumeGainIgnoresPan() {
        var track = Track(number = 2, input = TrackInput.MIC, volume = -6, pan = -50)
        assertTrue(abs(track.volumeGain - 0.501f) < 0.001f)
        track = track.copy(volume = Track.SILENT)
        assertEquals(0f, track.volumeGain)
    }
}
