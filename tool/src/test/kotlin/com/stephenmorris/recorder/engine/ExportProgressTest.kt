package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportProgressTest {
    @Test
    fun roundsDownToAWholePercent() {
        assertEquals(42, ExportProgress.shown(0, 0.429))
    }

    @Test
    fun neverGoesBackwards() {
        assertEquals(50, ExportProgress.shown(50, 0.3))
    }

    @Test
    fun stopsAt100() {
        assertEquals(100, ExportProgress.shown(99, 1.4))
    }
}
