package com.stephenmorris.recorder.audio

import com.stephenmorris.recorder.core.ClickKind
import com.stephenmorris.recorder.core.Sound
import kotlin.math.pow

/**
 * Recorder's drum voices: Drums' kit, clicks included and in the same order, turned down to leave the mix headroom.
 * Drums only plays drums, so it uses the whole range; here they share it with the audio tracks, so at 0 dB the
 * default pattern's loudest moment (snare and hat together) peaks at about −2.6 dB instead of just over full scale.
 */
object RecorderKit {
    /** How far below Drums' level the kit sits, in dB. */
    const val HEADROOM_DB = 3.0

    /** Drums' voices, scaled by −[HEADROOM_DB]. */
    fun voices(sampleRate: Double): List<Voice> {
        val down = 10.0.pow(-HEADROOM_DB / 20).toFloat()
        return DrumKit.voices(sampleRate).map { voice ->
            Voice(FloatArray(voice.samples.size) { voice.samples[it] * down }, voice.chokes)
        }
    }

    /** The voice that plays [sound]. */
    fun voice(sound: Sound): Int = DrumKit.voiceIndex(sound)

    /** The voice that plays a click, after the kit's: the pattern screens' metronome. */
    fun clickVoice(kind: ClickKind): Int = DrumKit.clickVoice(kind)
}
