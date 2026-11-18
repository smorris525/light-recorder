package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.Sound
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KitMixTest {
    private val sampleRate = 44_100.0

    private fun sine(frequency: Double, amplitude: Float, seconds: Double) =
        FloatArray((seconds * sampleRate).toInt()) { amplitude * sin(2 * PI * frequency * it / sampleRate).toFloat() }

    @Test
    fun clickPeakIsTheV1ClickLevel() {
        val peak = KitMix.clickPeak(sampleRate)
        assertTrue(peak > 0.1f)
        assertTrue(peak <= 0.35f * 0.37f)
    }

    // BS.1770's K-weighting is close to flat at 1 kHz, so a full-scale 1 kHz sine measures near its -3 dB power.
    @Test
    fun loudnessOfAFullScaleSineIsItsPower() = assertTrue(abs(KitMix.loudness(sine(1_000.0, 1f, 0.3), sampleRate) - -3) < 1)

    @Test
    fun doublingASoundAddsSixDecibels() {
        val quiet = KitMix.loudness(sine(1_000.0, 0.25f, 0.3), sampleRate)
        val loud = KitMix.loudness(sine(1_000.0, 0.5f, 0.3), sampleRate)
        assertTrue(abs(loud - quiet - 20 * log10(2.0)) < 0.01)
    }

    // A hit is measured over its first 300 ms; a long tail past that doesn't make it louder.
    @Test
    fun loudnessIgnoresTheTailPastTheWindow() {
        val hit = sine(1_000.0, 0.5f, 0.3)
        val longer = hit + sine(1_000.0, 0.5f, 2.0)
        assertTrue(abs(KitMix.loudness(longer, sampleRate) - KitMix.loudness(hit, sampleRate)) < 0.01)
    }

    // The ear hears little of the lowest bass: K-weighting turns it down.
    @Test
    fun lowBassMeasuresQuieterThanMidrange() =
        assertTrue(KitMix.loudness(sine(30.0, 0.5f, 0.3), sampleRate) < KitMix.loudness(sine(1_000.0, 0.5f, 0.3), sampleRate) - 1)

    @Test
    fun silenceHasNoLoudness() = assertEquals(Double.NEGATIVE_INFINITY, KitMix.loudness(FloatArray(100), sampleRate))

    // A quiet kit is brought up so its kick and snare sit at the target.
    @Test
    fun gainPutsTheBackboneAtTheTarget() {
        val samples = mapOf(Sound.KICK to sine(100.0, 0.02f, 0.3), Sound.SNARE to sine(1_000.0, 0.02f, 0.3))
        val gain = KitMix.gain(samples, 0.1f, sampleRate)
        val levelled = samples.mapValues { (_, it) -> FloatArray(it.size) { index -> it[index] * gain } }
        assertTrue(abs(KitMix.backboneLoudness(levelled, sampleRate) - KitMix.TARGET_LOUDNESS) < 0.01)
    }

    // A kit too peaky to reach the target is held back so no one sound at accent, with the click, passes the ceiling.
    @Test
    fun gainKeepsEverySingleSoundUnderTheCeiling() {
        val samples = mapOf(
            Sound.KICK to floatArrayOf(0.8f) + FloatArray(13_229),
            Sound.SNARE to floatArrayOf(-0.9f) + FloatArray(13_229),
        )
        val gain = KitMix.gain(samples, 0.1f, sampleRate)
        assertTrue(abs(0.9f * gain + 0.1f - KitMix.CEILING) < 1e-6f)
    }

    @Test
    fun gainForASilentKitIsUnity() = assertEquals(1f, KitMix.gain(mapOf(Sound.KICK to FloatArray(2)), 0.1f, sampleRate))

    // The electronic kit's sounds, levelled, never pass the ceiling one at a time with the click.
    @Test
    fun electronicKitSingleSoundsDoNotClip() {
        val samples = Sound.entries.associateWith { ElectronicKit.samples(it, sampleRate) }
        val click = KitMix.clickPeak(sampleRate)
        val gain = KitMix.gain(samples, click, sampleRate)
        for (sound in Sound.entries) {
            assertTrue(KitMix.peak(samples.getValue(sound)) * gain + click <= KitMix.CEILING + 1e-6f, sound.raw)
        }
    }

    // The trims set the balance against the snare (spec §4), so these sounds must stay where the trims expect them.
    @Test
    fun openHatAndRideSitUnderTheSnare() {
        val snare = KitMix.loudness(ElectronicKit.samples(Sound.SNARE, sampleRate), sampleRate)
        for ((sound, trim, balance) in listOf(Triple(Sound.OPEN_HAT, 0.62f, -5.8), Triple(Sound.RIDE, 0.38f, -10.4))) {
            val samples = ElectronicKit.samples(sound, sampleRate).let { s -> FloatArray(s.size) { s[it] * trim } }
            assertTrue(abs(KitMix.loudness(samples, sampleRate) - snare - balance) < 0.3, sound.raw)
        }
    }
}
