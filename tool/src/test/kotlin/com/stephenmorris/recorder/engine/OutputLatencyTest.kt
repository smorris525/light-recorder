package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class OutputLatencyTest {
    // 40,000 frames played at the timestamp, 4,800 more in the 0.1 s since, against 48,000 written.
    @Test
    fun latencyIsWrittenMinusPlayed() = assertEquals(3_200L, OutputLatency.frames(48_000, 40_000, 0, 100_000_000, 48_000))

    @Test
    fun neverNegative() = assertEquals(0L, OutputLatency.frames(100, 1_000, 0, 0, 48_000))
}

class OutputClockTest {
    // Review finding 1: a new AudioTrack's position starts at 0, but the render clock keeps counting across
    // restarts. Latency must count only what this track has been given.
    @Test
    fun latencyIsMeasuredFromTheTracksOwnStart() {
        val clock = OutputClock()
        clock.trackStarted(renderedFrames = 1_200_000)
        // 48,000 frames rendered into this track; 40,000 heard at the timestamp, 4,800 more since.
        assertEquals(3_200L, clock.latency(1_248_000, 40_000, 0, 100_000_000, 48_000))
    }

    @Test
    fun aFreshClockStartsAtZero() {
        assertEquals(3_200L, OutputClock().latency(48_000, 40_000, 0, 100_000_000, 48_000))
    }
}
