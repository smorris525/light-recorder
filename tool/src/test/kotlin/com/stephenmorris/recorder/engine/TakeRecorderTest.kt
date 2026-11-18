package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.TakeLevels
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TakeRecorderTest {
    private fun levels() = TakeLevels(start = 0.0, sampleRate = 48_000.0)

    @Test
    fun captureIsShiftedByLatency() {
        val recorder = TakeRecorder(startFrame = 50, endFrame = 200, levels = levels())
        val input = FloatArray(256) { it.toFloat() }

        recorder.append(input, 256, firstFrame = -50)

        val samples = recorder.samples()
        assertEquals(150, samples.size)
        assertEquals(100f, samples[0])
        assertTrue(recorder.isComplete)
    }

    @Test
    fun endCaptureKeepsWhatItHas() {
        val recorder = TakeRecorder(startFrame = 0, levels = levels())
        recorder.append(FloatArray(256) { 0.5f }, 256, firstFrame = 0)
        assertFalse(recorder.isComplete)

        recorder.end(256)
        recorder.append(FloatArray(256) { 0.5f }, 256, firstFrame = 256)

        assertEquals(256, recorder.samples().size)
        assertTrue(recorder.isComplete)
    }

    @Test
    fun aLaterEndNeverExtendsIt() {
        val recorder = TakeRecorder(startFrame = 0, endFrame = 100, levels = levels())
        recorder.end(500)
        recorder.append(FloatArray(300) { 1f }, 300, firstFrame = 0)

        assertEquals(100, recorder.samples().size)
    }

    @Test
    fun nothingBeforeTheStartIsKept() {
        val recorder = TakeRecorder(startFrame = 1_000, levels = levels())
        recorder.append(FloatArray(500) { 1f }, 500, firstFrame = 0)

        assertEquals(0, recorder.samples().size)
    }

    @Test
    fun aGapInTheInputIsSilence() {
        val recorder = TakeRecorder(startFrame = 0, levels = levels())
        recorder.append(FloatArray(10) { 1f }, 10, firstFrame = 0)
        recorder.append(FloatArray(10) { 1f }, 10, firstFrame = 20)

        val samples = recorder.samples()
        assertEquals(30, samples.size)
        assertTrue((10 until 20).all { samples[it] == 0f })
        assertTrue((20 until 30).all { samples[it] == 1f })
    }

    @Test
    fun levelsFollowWhatIsKept() {
        val levels = levels()
        val recorder = TakeRecorder(startFrame = 512, levels = levels)
        val input = FloatArray(1_024)
        input.fill(0.9f, 0, 512)
        input.fill(0.3f, 512, 1_024)

        recorder.append(input, 1_024, firstFrame = 0)

        assertEquals(listOf(0.3f), levels.peaks)
    }

    @Test
    fun aLongTakeJoinsItsChunks() {
        val recorder = TakeRecorder(startFrame = 0, levels = levels())
        val total = 120_000
        var frame = 0
        while (frame < total) {
            val count = minOf(960, total - frame)
            recorder.append(FloatArray(count) { (frame + it).toFloat() }, count, firstFrame = frame.toLong())
            frame += count
        }

        assertContentEquals(FloatArray(total) { it.toFloat() }, recorder.samples())
    }
}
