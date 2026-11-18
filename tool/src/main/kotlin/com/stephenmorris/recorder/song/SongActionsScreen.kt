package com.stephenmorris.recorder.song

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.bounce.BounceScreen
import com.stephenmorris.recorder.export.ExportScreen
import com.stephenmorris.recorder.notes.NotesScreen
import com.stephenmorris.recorder.tape.canBounce
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.ui.DeleteConfirmScreen
import com.stephenmorris.recorder.ui.ROW_INSET
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch

/** The song's actions' view model: a confirmed delete runs here, as it must finish after the screen goes. */
class SongActionsViewModel(context: SealedLightContext) : RecorderViewModel<SongActionsScreen.Exit>(context) {
    fun delete(ready: Recorder.Ready, then: () -> Unit) {
        viewModelScope.launch {
            ready.model.openSongId.value?.let(ready.editing::delete)
            then()
        }
    }
}

/**
 * The song's Actions (spec §10), titled with its name: Notes, then after a gap Bounce…, Export,
 * Duplicate and Delete. Bounce… is dimmed until something is recorded, while playing and during a take; Export until
 * something is recorded and during a take; Duplicate and Delete are hidden during a take.
 */
class SongActionsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<SongActionsScreen.Exit, SongActionsViewModel>(sealedActivity) {

    /** What happened to the song, for the Song screen. */
    enum class Exit { DELETED, DUPLICATED }

    override val viewModelClass: Class<SongActionsViewModel>
        get() = SongActionsViewModel::class.java

    override fun createViewModel() = SongActionsViewModel(lightContext)

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
        val take by ready.transport.take.collectAsState()
        val patternTake by ready.transport.patternTake.collectAsState()
        val isPlaying by ready.transport.isPlaying.collectAsState()
        val isTakeUnderWay = take != null || patternTake?.records == true
        val song = ready.model.song
        LightTopBar(leftButton = backButton { goBack() }, center = LightTopBarCenter.Text(song.name))
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            ActionRow("Notes") { navigateTo({ NotesScreen(it) }) }
            Spacer(Modifier.height(24.dp))
            // Always listed, so they can be found before there's anything to bounce or export; dimmed till then.
            ActionRow("Bounce…", enabled = song.canBounce && !isPlaying && !isTakeUnderWay) {
                navigateTo({ BounceScreen(it) }) { exit -> if (exit == BounceScreen.Exit.BOUNCED) goBack() }
            }
            ActionRow("Export", enabled = song.canBounce && !isTakeUnderWay) {
                navigateTo({ ExportScreen(it) }) { exit -> if (exit == ExportScreen.Exit.DONE) goBack() }
            }
            if (!isTakeUnderWay) {
                ActionRow("Duplicate") {
                    ready.editing.duplicateOpenSong()
                    goBack(Exit.DUPLICATED)
                }
                ActionRow("Delete") {
                    navigateTo({ DeleteConfirmScreen(it, song.name) }) { confirmed ->
                        if (confirmed) {
                            viewModel.delete(ready) { goBack(Exit.DELETED) }
                        }
                    }
                }
            }
        }
    }
}

/** A menu row: its text at the rows' inset, the whole row tappable. */
@Composable
fun ActionRow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    LightText(
        label,
        LightTextVariant.Copy,
        lighten = !enabled,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
    )
}
