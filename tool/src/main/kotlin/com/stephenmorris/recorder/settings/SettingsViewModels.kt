package com.stephenmorris.recorder.settings

import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.session.PatternSession
import com.thelightphone.sdk.SealedLightContext

/**
 * A Settings page's view model: only there to stop sound when the tool is paused. Unlike Drums', opening Settings
 * takes no snapshot: a pattern's meter and length change in the song itself.
 */
class SettingsPageViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context)

/** The Tempo page's view model: leaving it (by any back) applies the typed digits. */
class TempoViewModel(context: SealedLightContext, session: PatternSession) : RecorderViewModel<Unit>(context) {
    val model = TempoModel(session.pattern.value.bpm, session::setBpm) { System.nanoTime() / 1e9 }

    override fun onCleared() {
        model.leave()
        super.onCleared()
    }
}
