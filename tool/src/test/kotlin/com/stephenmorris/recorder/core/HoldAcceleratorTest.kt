package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HoldAcceleratorTest {
    @Test
    fun holdRepeatsByOneThenAcceleratesToFive() {
        assertEquals(420L, HoldAccelerator.INITIAL_DELAY_MS)
        for (count in 1..8) {
            assertEquals(HoldStep(size = 1, delayMs = 90), HoldAccelerator.repeatStep(count))
        }
        assertEquals(HoldStep(size = 5, delayMs = 140), HoldAccelerator.repeatStep(9))
        assertEquals(HoldStep(size = 5, delayMs = 140), HoldAccelerator.repeatStep(30))
    }
}
