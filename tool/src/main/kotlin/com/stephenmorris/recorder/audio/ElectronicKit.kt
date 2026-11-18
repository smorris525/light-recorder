package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.Sound
import kotlin.math.abs

/** One part of an electronic sound: a tone, filtered noise or metal. */
sealed interface SynthLayer {
    /** Seconds from the start until the source stops. */
    val duration: Double

    /** An oscillator, gliding from [frequency] to [glideTo] over [glide] seconds when [glideTo] isn't 0. */
    data class Tone(
        val waveform: Waveform,
        val frequency: Double,
        val glideTo: Double,
        val glide: Double,
        val peak: Double,
        val decay: Double,
    ) : SynthLayer {
        override val duration: Double
            get() = decay + TAIL
    }

    /** Filtered white noise. */
    data class Noise(
        val kind: Biquad.Kind,
        val frequency: Double,
        val q: Double,
        val peak: Double,
        val decay: Double,
        val attack: Double,
    ) : SynthLayer {
        override val duration: Double
            get() = attack + decay + TAIL
    }

    /** 808-style metal: six square waves at inharmonic ratios of [base], band-passed at [bandpass], high-passed at 6.5 kHz. */
    data class Metal(val base: Double, val peak: Double, val decay: Double, val bandpass: Double) : SynthLayer {
        override val duration: Double
            get() = decay + TAIL
    }

    companion object {
        /** Web Audio's default attack, for tones and noise. */
        const val ATTACK = 0.002

        /** Each source stops 50 ms after its envelope ends. */
        const val TAIL = 0.05

        val METAL_RATIOS = doubleArrayOf(2.0, 3.0, 4.16, 5.43, 6.79, 8.21)
    }
}

/**
 * The electronic kit (spec §4): synth voices rendered once at launch into samples.
 */
object ElectronicKit {
    /** How far above its layer's `peak` metal peaks: a short bright hit sounds quieter than its peak suggests. */
    private const val METAL_LEVEL = 2.0

    /** Each sound's layers. */
    fun layers(sound: Sound): List<SynthLayer> = when (sound) {
        Sound.KICK -> listOf(
            SynthLayer.Tone(Waveform.SINE, 72.0, 44.0, 0.07, 1.0, 0.7),
            SynthLayer.Noise(Biquad.Kind.LOWPASS, 2_500.0, 0.7, 0.15, 0.01, SynthLayer.ATTACK),
        )
        Sound.SNARE -> listOf(
            SynthLayer.Noise(Biquad.Kind.HIGHPASS, 1_500.0, 0.7, 0.55, 0.15, SynthLayer.ATTACK),
            SynthLayer.Tone(Waveform.SINE, 240.0, 0.0, 0.0, 0.3, 0.07),
            SynthLayer.Tone(Waveform.SINE, 180.0, 0.0, 0.0, 0.3, 0.1),
        )
        Sound.SIDESTICK -> listOf(
            SynthLayer.Tone(Waveform.TRIANGLE, 1_700.0, 0.0, 0.0, 0.35, 0.02),
            SynthLayer.Tone(Waveform.SINE, 455.0, 0.0, 0.0, 0.45, 0.035),
        )
        Sound.HAT -> listOf(SynthLayer.Metal(40.0, 0.35, 0.05, 10_000.0))
        // The closed hat's metal, as the two choke each other, under a wash of noise: metal alone sounded harsh, noise
        // alone not like a cymbal. Longer than 0.35 s, which choked off too soon to sound open.
        Sound.OPEN_HAT -> listOf(
            SynthLayer.Noise(Biquad.Kind.BANDPASS, 8_000.0, 0.6, 0.111, 0.7, SynthLayer.ATTACK),
            SynthLayer.Noise(Biquad.Kind.HIGHPASS, 11_000.0, 0.7, 0.028, 0.3, SynthLayer.ATTACK),
            SynthLayer.Metal(40.0, 0.278, 0.7, 10_000.0),
        )
        // A wash of noise with a stick's tick, and the ring from sine tones at unrelated pitches, the higher ones dying
        // sooner. A trace of the hats' metal ties it to them. Darker than the open hat.
        Sound.RIDE -> listOf(
            SynthLayer.Noise(Biquad.Kind.BANDPASS, 6_000.0, 0.8, 0.277, 0.9, SynthLayer.ATTACK),
            SynthLayer.Noise(Biquad.Kind.BANDPASS, 3_000.0, 1.2, 0.332, 0.02, SynthLayer.ATTACK),
            SynthLayer.Tone(Waveform.SINE, 2_710.0, 0.0, 0.0, 0.033, 0.7),
            SynthLayer.Tone(Waveform.SINE, 3_580.0, 0.0, 0.0, 0.033, 0.8),
            SynthLayer.Tone(Waveform.SINE, 4_490.0, 0.0, 0.0, 0.028, 0.6),
            SynthLayer.Tone(Waveform.SINE, 5_630.0, 0.0, 0.0, 0.028, 0.55),
            SynthLayer.Tone(Waveform.SINE, 6_870.0, 0.0, 0.0, 0.022, 0.45),
            SynthLayer.Tone(Waveform.SINE, 8_240.0, 0.0, 0.0, 0.017, 0.35),
            SynthLayer.Metal(40.0, 0.08, 0.6, 8_000.0),
        )
        Sound.TOM -> listOf(SynthLayer.Tone(Waveform.SINE, 115.0, 82.0, 0.15, 0.85, 0.5))
        // A band of noise that swells in over 12 ms and dies over 120 ms gives the beads' "chk", with a short bright
        // tick on top for the attack.
        Sound.SHAKER -> listOf(
            SynthLayer.Noise(Biquad.Kind.BANDPASS, 6_000.0, 1.2, 0.45, 0.12, 0.012),
            SynthLayer.Noise(Biquad.Kind.HIGHPASS, 9_000.0, 0.7, 0.2, 0.025, SynthLayer.ATTACK),
        )
    }

    /** The sound's samples: its layers mixed, as long as the longest. The same every time. */
    fun samples(sound: Sound, sampleRate: Double): FloatArray {
        val layers = layers(sound)
        val length = ((layers.maxOfOrNull { it.duration } ?: 0.0) * sampleRate).toInt()
        val mix = DoubleArray(length)
        val noise = SeededNoise()
        for (layer in layers) {
            val count = minOf(length, (layer.duration * sampleRate).toInt())
            render(layer, count, sampleRate, noise).forEachIndexed { index, sample -> mix[index] += sample }
        }
        return FloatArray(length) { mix[it].toFloat() }
    }

    private fun render(layer: SynthLayer, count: Int, sampleRate: Double, noise: SeededNoise): DoubleArray = when (layer) {
        is SynthLayer.Tone -> {
            val oscillator = Oscillator(layer.waveform, layer.frequency, layer.glideTo, layer.glide, sampleRate)
            val envelope = Envelope(layer.peak, SynthLayer.ATTACK, layer.decay)
            DoubleArray(count) { oscillator.next() * envelope.gain(it / sampleRate) }
        }
        is SynthLayer.Noise -> {
            val filter = Biquad(layer.kind, layer.frequency, layer.q, sampleRate)
            val envelope = Envelope(layer.peak, layer.attack, layer.decay)
            DoubleArray(count) { filter.process(noise.next()) * envelope.gain(it / sampleRate) }
        }
        is SynthLayer.Metal -> {
            val oscillators = SynthLayer.METAL_RATIOS.map { Oscillator(Waveform.SQUARE, layer.base * it, sampleRate = sampleRate) }
            val bandpass = Biquad(Biquad.Kind.BANDPASS, layer.bandpass, 0.8, sampleRate)
            // Web Audio's default Q (1 dB).
            val highpass = Biquad(Biquad.Kind.HIGHPASS, 6_500.0, 1.0, sampleRate)
            val envelope = Envelope(layer.peak, SynthLayer.ATTACK, layer.decay)
            val metal = DoubleArray(count) { index ->
                var sum = 0.0
                for (oscillator in oscillators) {
                    sum += oscillator.next()
                }
                highpass.process(bandpass.process(sum * 0.18)) * envelope.gain(index / sampleRate)
            }
            // The filters leave the low squares only a sliver of their level; brought up to peak at METAL_LEVEL x peak.
            val level = metal.maxOfOrNull { abs(it) } ?: 0.0
            if (level > 0) DoubleArray(count) { metal[it] * layer.peak * METAL_LEVEL / level } else metal
        }
    }
}
