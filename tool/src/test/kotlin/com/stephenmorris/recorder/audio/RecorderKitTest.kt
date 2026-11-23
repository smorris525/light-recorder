package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.tape.ClipStore
import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.sample
import kotlin.math.log10
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecorderKitTest {
    private val rate = 48_000.0

    @Test
    fun voicesAreDrumsKitTurnedDownByTheHeadroom() {
        val drums = DrumKit.voices(rate)
        val recorder = RecorderKit.voices(rate)
        val down = 10.0.pow(-3.0 / 20).toFloat()

        assertEquals(3.0, RecorderKit.HEADROOM_DB)
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

    /**
     * The kit leaves the mix headroom. The default pattern at 0 dB peaks around −2.6 dB,
     * in the meter's hot zone (from −6.5 dB) but short of full scale.
     */
    @Test
    fun defaultPatternPeaksUnderFullScale() {
        val levels = assertNotNull(MixLevels.render(Song.sample, ClipStore(), emptyMap(), RecorderKit.voices(rate), rate))
        val peak = assertNotNull(levels.peaks.maxOrNull())
        val decibels = 20 * log10(peak.toDouble())
        assertTrue(decibels > -3 && decibels <= -2, "peaks at $decibels dB")
    }
}
