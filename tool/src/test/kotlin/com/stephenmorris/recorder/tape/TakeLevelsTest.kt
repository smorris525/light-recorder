package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TakeLevelsTest {
    private val rate = 48_000.0
    private val size = TakeLevels.FRAMES_PER_PEAK

    @Test
    fun peaksAreMeasuredAsTheyArriveInAnyChunks() {
        val levels = TakeLevels(start = 10.0, sampleRate = rate)
        val samples = FloatArray(size * 2) { 0.1f }
        samples[size + 5] = -0.75f

        levels.append(samples.copyOfRange(0, 300), 300)
        levels.append(samples.copyOfRange(300, samples.size), samples.size - 300)

        assertEquals(listOf(0.1f, 0.75f), levels.peaks)
    }

    @Test
    fun levelsArePlacedOnTheTapeFromTheTakesStart() {
        val levels = TakeLevels(start = 10.0, sampleRate = rate)
        levels.append(FloatArray(size * 4) { 0.5f }, size * 4)
        val peak = size / rate

        assertNull(levels.level(from = 9.9, to = 10.0))
        assertEquals(0.5f, levels.level(from = 10.0, to = 10 + peak))
        assertNull(levels.level(from = 10 + peak * 4, to = 10 + peak * 5))
    }

    /** As the mix measured with the take in has it, so the lane doesn't change at the take's start when it's measured. */
    @Test
    fun aRangeThatRunsIntoTheTakeHasItsLevel() {
        val levels = TakeLevels(start = 10.0, sampleRate = rate)
        levels.append(FloatArray(size * 4) { 0.5f }, size * 4)

        assertEquals(0.5f, levels.level(from = 9.9, to = 10.001))
    }

    // New: only the first count samples of a reused buffer are measured.
    @Test
    fun onlyTheCountedSamplesAreMeasured() {
        val levels = TakeLevels(start = 0.0, sampleRate = rate)
        val buffer = FloatArray(size * 2) { 0.9f }
        buffer.fill(0.2f, 0, size)

        levels.append(buffer, size)

        assertEquals(listOf(0.2f), levels.peaks)
    }
}
