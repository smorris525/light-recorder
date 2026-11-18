package com.stephenmorris.recorder.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.track.TrackScreen
import com.stephenmorris.recorder.ui.RenameScreen
import com.stephenmorris.recorder.ui.SCREEN_MARGIN
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

/** Between two strips, so they read as four. */
private val STRIP_GAP = 16.dp

/** How far the strips reach up into the room under the header, and down into the room over the ruler. */
private val REACH_UP = 8.dp
private val REACH_DOWN = 5.dp

/**
 * The Song screen's view model. However the screen goes (BACK or the system back key, which skips `onBackPressed`),
 * leaving it closes the song: playback stops, a take ends and is kept, and nothing stays armed (spec §6).
 */
class SongViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context) {
    override fun onCleared() {
        ready.value?.model?.close()
        super.onCleared()
    }
}

/** The open song (spec §8): its name, four channel strips, and the transport panel. */
class SongScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, SongViewModel>(sealedActivity) {

    override val viewModelClass: Class<SongViewModel>
        get() = SongViewModel::class.java

    override fun createViewModel() = SongViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Song(it) }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.ColumnScope.Song(ready: Recorder.Ready) {
        ready.model.songs.collectAsState().value
        val song = ready.model.song
        LightTopBar(
            leftButton = backButton { goBack() },
            center = LightTopBarCenter.Text(song.name) {
                navigateTo({ RenameScreen(it, song.name) }) { typed -> typed?.let(ready.editing::rename) }
            },
            rightButton = LightBarButton.LightIcon(
                LightIcons.ELLIPSES,
                onClick = {
                    navigateTo({ SongActionsScreen(it) }) { exit ->
                        if (exit == SongActionsScreen.Exit.DELETED) {
                            goBack()
                        }
                    }
                },
                contentDescription = "Song options",
            ),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    // Taller than its room by what it reaches up and down, drawn from REACH_UP above it.
                    val up = REACH_UP.roundToPx()
                    val extra = up + REACH_DOWN.roundToPx()
                    val height = constraints.maxHeight + extra
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -up) }
                }
                .padding(horizontal = SCREEN_MARGIN),
            horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
        ) {
            for (track in song.tracks) {
                ChannelStrip(ready, track, onOpen = { navigateTo({ TrackScreen(it, track.id) }) }, modifier = Modifier.weight(1f))
            }
        }
        TransportPanel(ready)
    }
}
