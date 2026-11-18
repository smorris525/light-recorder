package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Sound

/** A one-shot sample, and the voices it cuts off when it starts. */
class Voice(val samples: FloatArray, val chokes: IntArray = IntArray(0))

/**
 * The mixer's voices: the eight sounds in [Sound] order, trimmed for balance then levelled by [KitMix.gain], followed
 * by the two clicks at [KitMix.CLICK_GAIN].
 */
object DrumKit {
    fun voiceIndex(sound: Sound): Int = sound.ordinal

    /** Click voices follow the sounds. */
    fun clickVoice(kind: ClickKind): Int = Sound.entries.size + kind.ordinal

    /**
     * A sound's gain, for balance against the snare (spec §4): kick and tom ~1.5 dB under, open hat ~6 dB under,
     * sidestick and closed hat ~8.5 dB under, shaker ~9 dB under and ride ~10 dB under.
     */
    fun trim(sound: Sound): Float = when (sound) {
        Sound.KICK -> 0.43f
        Sound.TOM -> 0.48f
        Sound.OPEN_HAT -> 0.62f
        Sound.RIDE -> 0.38f
        Sound.SIDESTICK -> 1.12f
        Sound.HAT -> 1.15f
        Sound.SNARE, Sound.SHAKER -> 1f
    }

    fun voices(sampleRate: Double): List<Voice> {
        // Trimmed before levelling, so the trims set the balance and levelling the overall volume.
        val trimmed = Sound.entries.associateWith { sound ->
            ElectronicKit.samples(sound, sampleRate).scaled(trim(sound))
        }
        val gain = KitMix.gain(trimmed, KitMix.clickPeak(sampleRate), sampleRate)
        val sounds = Sound.entries.map { sound ->
            Voice(trimmed.getValue(sound).scaled(gain), sound.chokes.map(::voiceIndex).toIntArray())
        }
        val clicks = ClickKind.entries.map { Voice(ClickSound.samples(it, sampleRate).scaled(KitMix.CLICK_GAIN)) }
        return sounds + clicks
    }

    private fun FloatArray.scaled(gain: Float) = FloatArray(size) { this[it] * gain }
}
