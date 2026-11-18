package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Sound
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrumKitTest {
    private val voices = DrumKit.voices(48_000.0)

    @Test
    fun tenVoicesInOrder() {
        assertEquals(10, voices.size)
        assertEquals(1, DrumKit.voiceIndex(Sound.SNARE))
        assertEquals(9, DrumKit.clickVoice(ClickKind.BEAT))
        assertEquals(8, DrumKit.clickVoice(ClickKind.ACCENT))
    }

    @Test
    fun hatChokesOpenHat() {
        assertTrue(voices[0].chokes.isEmpty())
        assertContentEquals(intArrayOf(4), voices[3].chokes)
    }

    @Test
    fun trimsAreTheSpecs() =
        assertEquals(listOf(0.43f, 1f, 1.12f, 1.15f, 0.62f, 0.38f, 0.48f, 1f), Sound.entries.map(DrumKit::trim))

    @Test
    fun noSingleSoundWithTheClickPassesTheCeiling() {
        val click = KitMix.clickPeak(48_000.0)
        for (index in 0 until 8) {
            assertTrue(KitMix.peak(voices[index].samples) + click <= 0.99f + 1e-6f, "voice $index")
        }
    }

    @Test
    fun clicksAreScaledByTheClickGain() =
        assertTrue(kotlin.math.abs(KitMix.peak(voices[8].samples) - KitMix.clickPeak(48_000.0)) < 1e-6f)
}
