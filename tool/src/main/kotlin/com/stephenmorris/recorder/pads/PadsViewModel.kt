package com.stephenmorris.recorder.pads

import com.stephenmorris.recorder.core.PadTouchProbe
import com.stephenmorris.recorder.pattern.PatternExit
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The pads screen's view model: the touch probe that decides accents, and the click, which sounds only while Pads
 * shows (spec §3).
 */
class PadsViewModel(context: SealedLightContext) : RecorderViewModel<PatternExit?>(context) {
    /** Learns whether this screen reports real pressure or touch size. */
    val probe = PadTouchProbe()

    private val fallback = MutableStateFlow(true)

    /** The click setting, shared by every visit to Pads and saved as it changes. */
    val clickOn: StateFlow<Boolean>
        get() = ready.value?.clickOn ?: fallback.asStateFlow()

    private var showing = false

    /** Pads is showing or covered: the pattern clicks only while it shows. */
    fun setShowing(isShowing: Boolean) {
        showing = isShowing
        val ready = ready.value ?: return
        if (isShowing) ready.patterns.padsShown(ready.clickOn.value) else ready.patterns.padsHidden()
    }

    fun toggleClick() {
        val ready = ready.value ?: return
        ready.clickOn.value = !ready.clickOn.value
        setShowing(showing)
    }

    /** Leaving Pads, or opening Settings from it (spec §10, as Drums): an overdub ends; a count-in stops. */
    fun leavePads() {
        ready.value?.transport?.leavePads()
    }

    override fun onCleared() {
        leavePads()
        setShowing(false)
        super.onCleared()
    }
}
