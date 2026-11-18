package com.stephenmorris.recorder.tape

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spans with no recording to measure get a stand-in level; recorded ones and blank tape get none. */
class LaneDetailTest {
    @Test
    fun standInCoversSpansWithoutARecording() {
        val spans = listOf(Span(2.0, 3.0, 0.0))

        val level = assertNotNull(LaneDetail.standIn(2.5, spans))
        assertTrue(level in 0.0..1.0)
        assertNull(LaneDetail.standIn(1.0, spans))
        assertNull(LaneDetail.standIn(3.0, spans))
    }

    @Test
    fun recordedSpansHaveNoStandIn() {
        val spans = listOf(Span(0.0, 5.0, 0.0, clip = UUID.randomUUID()))

        assertNull(LaneDetail.standIn(1.0, spans))
    }
}
