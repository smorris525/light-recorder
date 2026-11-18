package com.stephenmorris.recorder.notes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.ui.SCREEN_MARGIN
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable

/** The Notes screen's view model. */
class NotesViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context)

/** The song's notes (spec §12), as plain text; tapping them opens the editor. */
class NotesScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, NotesViewModel>(sealedActivity) {

    override val viewModelClass: Class<NotesViewModel>
        get() = NotesViewModel::class.java

    override fun createViewModel() = NotesViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Notes(it) }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.ColumnScope.Notes(ready: Recorder.Ready) {
        ready.model.songs.collectAsState().value
        val notes = ready.model.song.notes
        LightTopBar(leftButton = backButton { goBack() }, center = LightTopBarCenter.Text("Notes"))
        LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LightText(
                notes.ifEmpty { "tap to write notes" },
                LightTextVariant.Paragraph,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClickLabel = "Edit notes") {
                        navigateTo({ NotesEditorScreen(it, ready.model.song.notes) })
                    }
                    .padding(horizontal = SCREEN_MARGIN, vertical = 12.dp),
            )
        }
    }
}
