package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CaptureRingTest {
    @Test
    fun whatIsWrittenIsReadInOrder() {
        val ring = CaptureRing(capacity = 16)
        ring.write(floatArrayOf(1f, 2f, 3f), 3)
        val out = FloatArray(8)

        assertEquals(3, ring.read(out))
        assertContentEquals(floatArrayOf(1f, 2f, 3f), out.copyOf(3))
    }

    @Test
    fun readingAnEmptyRingGivesNothing() {
        assertEquals(0, CaptureRing(capacity = 16).read(FloatArray(8)))
    }

    @Test
    fun aWriteThatWrapsComesBackWhole() {
        val ring = CaptureRing(capacity = 8)
        val out = FloatArray(8)
        ring.write(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), 6)
        assertEquals(6, ring.read(out))
        ring.write(floatArrayOf(7f, 8f, 9f, 10f, 11f, 12f), 6)

        assertEquals(6, ring.read(out))
        assertContentEquals(floatArrayOf(7f, 8f, 9f, 10f, 11f, 12f), out.copyOf(6))
    }

    @Test
    fun aFullRingDropsWhatDoesNotFit() {
        val ring = CaptureRing(capacity = 4)
        ring.write(floatArrayOf(1f, 2f, 3f, 4f, 5f), 5)
        val out = FloatArray(8)

        assertEquals(3, ring.read(out))
        assertContentEquals(floatArrayOf(1f, 2f, 3f), out.copyOf(3))
    }
}
