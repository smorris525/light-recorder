package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Sound
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecorderKitTest {
    private val rate = 48_000.0

    @Test
    fun voicesAreDrumsKitTurnedDownByTheHeadroom() {
        val drums = DrumKit.voices(rate)
        val recorder = RecorderKit.voices(rate)
        val down = 10.0.pow(-7.0 / 20).toFloat()

        assertEquals(7.0, RecorderKit.HEADROOM_DB)
        assertEquals(drums.size, recorder.size)
        for (index in drums.indices) {
            assertEquals(drums[index].samples.size, recorder[index].samples.size)
            assertTrue(drums[index].samples.indices.all { abs(drums[index].samples[it] * down - recorder[index].samples[it]) < 1e-7f })
            assertContentEquals(drums[index].chokes, recorder[index].chokes)
        }
    }

    @Test
    fun voicesAreInDrumsOrder() {
        assertEquals(Sound.entries.map { DrumKit.voiceIndex(it) }, Sound.entries.map { RecorderKit.voice(it) })
        assertEquals(ClickKind.entries.map { DrumKit.clickVoice(it) }, ClickKind.entries.map { RecorderKit.clickVoice(it) })
    }
}
