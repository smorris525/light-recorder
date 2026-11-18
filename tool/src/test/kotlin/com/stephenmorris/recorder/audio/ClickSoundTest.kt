package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickSoundTest {
    private fun zeroCrossings(samples: FloatArray) = (1 until samples.size).count { (samples[it - 1] < 0) != (samples[it] < 0) }

    @Test
    fun lastsFortyFiveMilliseconds() = assertEquals(1_984, ClickSound.samples(ClickKind.BEAT, 44_100.0).size)

    @Test
    fun peaksBelowItsLevel() {
        val beat = peak(ClickSound.samples(ClickKind.BEAT, 44_100.0))
        assertTrue(beat <= 0.35)
        assertTrue(beat > 0.2)
    }

    // The accent is higher (1,760 Hz against 1,180 Hz), so it crosses zero about 1.5 times as often.
    @Test
    fun theAccentIsHigher() {
        val accent = zeroCrossings(ClickSound.samples(ClickKind.ACCENT, 44_100.0))
        val beat = zeroCrossings(ClickSound.samples(ClickKind.BEAT, 44_100.0))
        assertTrue(accent > beat * 1.4)
    }
}
