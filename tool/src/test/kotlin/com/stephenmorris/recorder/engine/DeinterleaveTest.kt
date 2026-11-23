package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertContentEquals

class DeinterleaveTest {
    @Test
    fun monoIsCopiedScaled() {
        val into = FloatArray(2)
        deinterleave(shortArrayOf(16_384, -32_768), frames = 2, channels = 1, channel = 1, into = into)
        assertContentEquals(floatArrayOf(0.5f, -1f), into)
    }

    @Test
    fun theChosenChannelIsKept() {
        val source = shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8).map { (it * 1_000).toShort() }.toShortArray()
        val into = FloatArray(2)
        deinterleave(source, frames = 2, channels = 4, channel = 3, into = into)
        assertContentEquals(floatArrayOf(3_000 / 32_768f, 7_000 / 32_768f), into)
    }
}
