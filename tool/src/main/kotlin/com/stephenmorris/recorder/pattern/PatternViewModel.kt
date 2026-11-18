package com.stephenmorris.recorder.pattern

import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.session.PatternSession
import com.thelightphone.sdk.SealedLightContext
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The pattern screen's view model: builds the [PatternSession] Pattern, Sound and Pads share, keeps it in step with
 * the song and the step heard, and, however the screen goes (BACK, the system back key, Delete), stops the pattern
 * and hands ▶ back to the tape (spec §6).
 */
class PatternViewModel(context: SealedLightContext, private val id: UUID) : RecorderViewModel<PatternExit?>(context) {
    private val _session = MutableStateFlow<PatternSession?>(null)
    val session: StateFlow<PatternSession?> = _session.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = ready.filterNotNull().first()
            val session = PatternSession(ready.model, ready.transport, ready.patterns, id)
            _session.value = session
            launch { ready.model.songs.collect { session.refresh() } }
            launch { ready.model.playingStep.collect { step -> session.playhead(step)?.let(session::playheadMoved) } }
        }
    }

    override fun onCleared() {
        ready.value?.patterns?.leave()
        super.onCleared()
    }
}
