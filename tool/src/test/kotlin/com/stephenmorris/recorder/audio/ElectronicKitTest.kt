package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.Sound
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElectronicKitTest {
    private val sampleRate = 44_100.0

    @Test
    fun kickIsTwoLayers() {
        assertEquals(
            listOf(
                SynthLayer.Tone(Waveform.SINE, 72.0, 44.0, 0.07, 1.0, 0.7),
                SynthLayer.Noise(Biquad.Kind.LOWPASS, 2_500.0, 0.7, 0.15, 0.01, 0.002),
            ),
            ElectronicKit.layers(Sound.KICK),
        )
    }

    @Test
    fun closedHatIsMetal() =
        assertEquals(listOf(SynthLayer.Metal(40.0, 0.35, 0.05, 10_000.0)), ElectronicKit.layers(Sound.HAT))

    @Test
    fun openHatIsTheClosedHatsMetalOverNoise() {
        val layers = ElectronicKit.layers(Sound.OPEN_HAT)
        assertTrue(SynthLayer.Metal(40.0, 0.278, 0.7, 10_000.0) in layers)
        assertEquals(3, layers.size)
    }

    @Test
    fun rideIsSineTonesOverNoiseWithATraceOfTheHatsMetal() {
        val layers = ElectronicKit.layers(Sound.RIDE)
        assertEquals(6, layers.count { it is SynthLayer.Tone && it.waveform == Waveform.SINE })
        assertTrue(SynthLayer.Metal(40.0, 0.08, 0.6, 8_000.0) in layers)
    }

    @Test
    fun closedHatPeaksAtTwiceItsLayersPeak() =
        assertTrue(abs(peak(ElectronicKit.samples(Sound.HAT, sampleRate)) - 0.7) < 0.001)

    @Test
    fun lastsAsLongAsItsLongestLayer() {
        assertEquals((0.75 * sampleRate).toInt(), ElectronicKit.samples(Sound.KICK, sampleRate).size)
        assertEquals((0.182 * sampleRate).toInt(), ElectronicKit.samples(Sound.SHAKER, sampleRate).size)
    }

    @Test
    fun rendersTheSameEveryTime() {
        for (sound in Sound.entries) {
            assertContentEquals(ElectronicKit.samples(sound, sampleRate), ElectronicKit.samples(sound, sampleRate), sound.raw)
        }
    }

    @Test
    fun isAudibleAndFinite() {
        for (sound in Sound.entries) {
            val samples = ElectronicKit.samples(sound, sampleRate)
            assertTrue(samples.all { it.isFinite() }, sound.raw)
            assertTrue(peak(samples) > 0.05, sound.raw)
            assertTrue(peak(samples) < 1.5, sound.raw)
        }
    }

    // The last 10 ms are past every envelope, so cutting the sound there doesn't click.
    @Test
    fun endsInSilence() {
        for (sound in Sound.entries) {
            val samples = ElectronicKit.samples(sound, sampleRate)
            assertTrue(peak(samples.copyOfRange(samples.size - 441, samples.size)) < 0.001, sound.raw)
        }
    }
}

/** The largest absolute sample. */
fun peak(samples: FloatArray): Double = samples.maxOfOrNull { abs(it) }?.toDouble() ?: 0.0
