package com.stephenmorris.recorder.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternPlaybackTest {
    @Test
    fun timesStepsFromTheMeter() {
        assertEquals(0.125, testPlayback(bpm = 120).stepDuration)
        assertEquals(0.5, testPlayback(bpm = 120, meter = MeterId.SEVEN_EIGHT).stepDuration)
        assertEquals(60, testPlayback(meter = MeterId.FIVE_FOUR, bars = 3).totalSteps)
        assertEquals(12, testPlayback(meter = MeterId.TWELVE_EIGHT).stepsPerBar)
    }

    @Test
    fun playsHitsAtTheirLevelsInSoundOrder() {
        val playback = testPlayback(
            steps = grid(
                Triple(Sound.SNARE, 4, Level.HIT),
                Triple(Sound.KICK, 4, Level.ACCENT),
                Triple(Sound.KICK, 0, Level.HIT),
            ),
        )
        assertEquals(
            mapOf(
                0 to listOf(Trigger(Sound.KICK, 0.6f)),
                4 to listOf(Trigger(Sound.KICK, 1f), Trigger(Sound.SNARE, 0.6f)),
            ),
            playback.triggersByStep,
        )
    }

    // A hat and an open hat on one step: the hat plays at its level, and the open hat it would choke is left out.
    @Test
    fun hatChokesTheOpenHatOnTheSameStep() {
        val playback = testPlayback(
            steps = grid(
                Triple(Sound.HAT, 0, Level.HIT),
                Triple(Sound.OPEN_HAT, 0, Level.ACCENT),
                Triple(Sound.OPEN_HAT, 2, Level.ACCENT),
            ),
        )
        assertEquals(
            mapOf(0 to listOf(Trigger(Sound.HAT, 0.6f)), 2 to listOf(Trigger(Sound.OPEN_HAT, 1f))),
            playback.triggersByStep,
        )
    }

    // Hits past the end or before the start never play.
    @Test
    fun ignoresHitsOutsideThePattern() {
        val playback = testPlayback(
            steps = grid(Triple(Sound.KICK, -1, Level.HIT), Triple(Sound.KICK, 16, Level.HIT), Triple(Sound.KICK, 15, Level.HIT)),
        )
        assertEquals(listOf(15), playback.triggersByStep.keys.sorted())
    }

    @Test
    fun clampsCorruptTempoAndBars() {
        val playback = testPlayback(bpm = 0, bars = 0)
        assertEquals(60.0 / 30 * 0.25, playback.stepDuration)
        assertEquals(16, playback.totalSteps)
        val big = testPlayback(bpm = 0, bars = 99)
        assertEquals(60.0 / 30 * 0.25, big.stepDuration)
        assertEquals(16 * 8, big.totalSteps)
    }

    @Test
    fun swingsEachStepOfTheBar() {
        val playback = testPlayback(swing = Swing.EIGHTH_HEAVY)
        assertEquals(16, playback.swingDelays.size)
        assertEquals(0.0, playback.swingDelays[0])
        assertTrue(abs(playback.swingDelays[2] - 0.64) < 1e-9)
        assertTrue(abs(playback.swingDelays[6] - 0.64) < 1e-9)
    }

    @Test
    fun storedSwingDoesNotPlayInSixEight() {
        assertEquals(List(6) { 0.0 }, testPlayback(meter = MeterId.SIX_EIGHT, swing = Swing.SIXTEENTH_HEAVY).swingDelays)
    }

    @Test
    fun clicksOnEachBeat() {
        assertEquals(
            mapOf(0 to ClickKind.ACCENT, 2 to ClickKind.BEAT, 4 to ClickKind.BEAT),
            testPlayback(meter = MeterId.SEVEN_EIGHT).clicks,
        )
    }
}
