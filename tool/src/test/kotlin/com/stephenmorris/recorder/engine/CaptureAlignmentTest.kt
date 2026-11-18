package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureAlignmentTest {
    @Test
    fun simultaneousStampsMapFrameForFrame() {
        val frame = CaptureAlignment.renderFrame(150, Stamp(100, 1_000), Stamp(5_000, 1_000), 48_000)
        assertEquals(5_050L, frame)
    }

    @Test
    fun laterInputIsLaterOnTheRenderClock() {
        val frame = CaptureAlignment.renderFrame(0, Stamp(0, 10_001_000), Stamp(0, 1_000), 48_000)
        assertEquals(480L, frame)
    }

    @Test
    fun theCorrectionShiftsEveryFrame() {
        val frame = CaptureAlignment.renderFrame(150, Stamp(100, 1_000), Stamp(5_000, 1_000), 48_000, correctionFrames = 7)
        assertEquals(5_057L, frame)
        assertEquals(0L, CaptureAlignment.CORRECTION_FRAMES)
    }
}
