package com.stephenmorris.recorder.settings

import com.stephenmorris.recorder.core.SettingsRows
import com.stephenmorris.recorder.core.TapTempo
import com.stephenmorris.recorder.core.TempoEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Tempo page (spec §13): the keypad, the arrows and TAP. Typing sets the tempo as soon as the digits make one;
 * the arrows and TAP set it at once and show it afresh, ready to be replaced; leaving applies the digits, clamped.
 */
class TempoModel(initialBpm: Int, private val setBpm: (Int) -> Unit, private val nowSeconds: () -> Double) {
    private var bpm = initialBpm
    private var entry = TempoEntry.showing(initialBpm)
    private val tapTempo = TapTempo()
    private val _digits = MutableStateFlow(entry.digits)

    /** What the page shows. */
    val digits: StateFlow<String> = _digits.asStateFlow()

    fun press(digit: Int) = type(entry.pressed(digit))

    fun delete() = type(entry.deleted())

    fun tap() {
        tapTempo.tap(nowSeconds())?.let(::show)
    }

    /** An arrow: one step, or a held arrow's bigger ones, from what's shown. */
    fun step(by: Int) = show(SettingsRows.bpm(entry.liveTempo ?: bpm, by))

    /** Going back applies the digits, clamped; with none, the tempo stays. */
    fun leave() {
        entry.finalTempo?.let(::apply)
    }

    private fun type(next: TempoEntry) {
        entry = next
        _digits.value = next.digits
        next.liveTempo?.let(::apply)
    }

    private fun show(tempo: Int) {
        entry = TempoEntry.showing(tempo)
        _digits.value = entry.digits
        apply(tempo)
    }

    private fun apply(tempo: Int) {
        if (tempo != bpm) {
            bpm = tempo
            setBpm(tempo)
        }
    }
}
