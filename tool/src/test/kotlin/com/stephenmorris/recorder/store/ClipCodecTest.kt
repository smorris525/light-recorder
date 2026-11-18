package com.stephenmorris.recorder.store

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClipCodecTest {
    @Test
    fun aClipComesBackAsItWasSaved() {
        val (rate, samples) = assertNotNull(ClipCodec.decode(ClipCodec.encode(floatArrayOf(0.5f, -1f, 0f), 48_000.0)))
        assertEquals(48_000.0, rate)
        assertContentEquals(floatArrayOf(0.5f, -1f, 0f), samples)
    }

    @Test
    fun tooShortIsNothing() {
        assertNull(ClipCodec.decode(ByteArray(7)))
    }

    @Test
    fun aBadRateIsNothing() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(ClipCodec.decode(ClipCodec.encode(floatArrayOf(1f), rate)), "rate $rate")
        }
    }

    @Test
    fun aTrailingPartialSampleIsIgnored() {
        val bytes = ClipCodec.encode(floatArrayOf(0.25f), 48_000.0) + byteArrayOf(1, 2)
        val (_, samples) = assertNotNull(ClipCodec.decode(bytes))
        assertContentEquals(floatArrayOf(0.25f), samples)
    }
}
