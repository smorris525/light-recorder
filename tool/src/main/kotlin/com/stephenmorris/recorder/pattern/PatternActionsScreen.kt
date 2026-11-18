package com.stephenmorris.recorder.pattern

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.session.PatternSession
import com.stephenmorris.recorder.song.ActionRow
import com.stephenmorris.recorder.ui.DeleteConfirmScreen
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import kotlinx.coroutines.launch

/** The pattern's Actions' view model: a confirmed delete runs here, as it must finish after the screen goes. */
class PatternActionsViewModel(context: SealedLightContext) : RecorderViewModel<PatternExit?>(context) {
    fun delete(ready: Recorder.Ready, session: PatternSession, then: () -> Unit) {
        viewModelScope.launch {
            ready.patterns.delete(session.id)
            then()
        }
    }
}

/**
 * A pattern's Actions (spec §10): Duplicate, which opens the copy, and Delete, only while the song
 * has more than one pattern, which asks first and then returns to Patterns.
 */
class PatternActionsScreen(sealedActivity: SealedLightActivity, private val session: PatternSession) :
    LightScreen<PatternExit?, PatternActionsViewModel>(sealedActivity) {

    override val viewModelClass: Class<PatternActionsViewModel>
        get() = PatternActionsViewModel::class.java

    override fun createViewModel() = PatternActionsViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Actions(it) }
        }
    }

    @Composable
    private fun Actions(ready: Recorder.Ready) {
        ready.model.songs.collectAsState().value
        val pattern by session.pattern.collectAsState()
        val count = ready.model.song.patterns.size
        LightTopBar(leftButton = backButton { goBack(null) }, center = LightTopBarCenter.Text(pattern.name))
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            ActionRow("Duplicate") { goBack(PatternExit.Open(ready.patterns.duplicate(session.id))) }
            if (count > 1) {
                ActionRow("Delete") {
                    navigateTo({ DeleteConfirmScreen(it, pattern.name) }) { confirmed ->
                        if (confirmed) {
                            viewModel.delete(ready, session) { goBack(PatternExit.Deleted) }
                        }
                    }
                }
            }
        }
    }
}
