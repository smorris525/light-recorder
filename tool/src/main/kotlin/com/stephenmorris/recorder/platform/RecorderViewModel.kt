package com.stephenmorris.recorder.platform

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The base of every Recorder screen's view model: it gets the process-wide parts ready, and stops sound and saves when
 * the tool is paused (spec §4.4). The SDK only tells the top screen, so every screen does this.
 */
abstract class RecorderViewModel<T>(context: SealedLightContext) : LightViewModel<T>() {
    private val _ready = MutableStateFlow<Recorder.Ready?>(null)

    /** The parts, once built. */
    val ready: StateFlow<Recorder.Ready?> = _ready.asStateFlow()

    init {
        viewModelScope.launch { _ready.value = Recorder.prepare(context) }
    }

    override fun onScreenShow(screen: SimpleLightScreen<T>) {
        super.onScreenShow(screen)
        ready.value?.transport?.resume()
    }

    override fun onAppPause() {
        ready.value?.let {
            it.transport.pause()
            it.model.saveNow()
        }
    }
}
