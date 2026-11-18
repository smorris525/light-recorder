package com.stephenmorris.recorder.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.song.SongScreen
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.ui.DeleteConfirmScreen
import com.stephenmorris.recorder.ui.ScreenFrame
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The Library's view model: edit mode, and the work that must finish after the screen moves on. */
class LibraryViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context) {
    val isEditing = MutableStateFlow(false)

    val scrollState = ScrollState(0)

    /** Opens a song once its recordings are read off the main thread, then [then]. A tap while one opens is ignored. */
    fun open(id: UUID, then: () -> Unit) {
        val ready = ready.value ?: return
        viewModelScope.launch {
            ready.model.openPreloaded(id) { ready.clips.preload(it, Dispatchers.IO) }
            if (ready.model.openSongId.value == id) {
                then()
            }
        }
    }

    /** Deletes a song; deleting the last leaves edit mode. */
    fun delete(id: UUID) {
        val ready = ready.value ?: return
        viewModelScope.launch {
            ready.editing.delete(id)
            if (ready.model.songs.value.isEmpty()) {
                isEditing.value = false
            }
        }
    }
}

/** The first screen: the songs, oldest first, with EDIT and ⊕ underneath (spec §7), as Drums' library. */
@InitialScreen
class LibraryScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel(): LibraryViewModel = LibraryViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Library(it) }
        }
    }

    private fun open(id: UUID) = viewModel.open(id) { navigateTo({ SongScreen(it) }) }

    @Composable
    private fun Library(ready: Recorder.Ready) {
        val songs by ready.model.songs.collectAsState()
        val isEditing by viewModel.isEditing.collectAsState()
        val isOpening by ready.model.isOpening.collectAsState()

        fun confirmDelete(song: Song) = navigateTo({ DeleteConfirmScreen(it, song.name) }) { confirmed ->
            if (confirmed) {
                viewModel.delete(song.id)
            }
        }

        val add = LightBarButton.LightIcon(
            LightIcons.ADD,
            onClick = {
                viewModel.isEditing.value = false
                open(ready.editing.newSong())
            },
            contentDescription = "New song",
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightScrollView(
                    scrollState = viewModel.scrollState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp()),
                ) {
                    for (song in songs) {
                        SongRow(
                            song = song,
                            isEditing = isEditing,
                            onOpen = { if (!isOpening) open(song.id) },
                            onDelete = { confirmDelete(song) },
                        )
                    }
                    Spacer(Modifier.height(1f.gridUnitsAsDp()))
                }
                LightBottomBar(
                    items = if (songs.isEmpty()) {
                        listOf(null, add, null)
                    } else {
                        listOf(
                            LightBarButton.Text(if (isEditing) "DONE" else "EDIT") {
                                viewModel.isEditing.value = !isEditing
                            },
                            null,
                            add,
                        )
                    },
                )
            }
            // Centred on the whole screen, the bar's room included; not tappable.
            if (songs.isEmpty()) {
                LightText(
                    "tap below to start a song",
                    LightTextVariant.Paragraph,
                    align = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                )
            }
        }
    }
}

/**
 * A song: its name over its length. In edit mode a delete button sits before it, and tapping the row does nothing.
 * The text sets the row's height, so edit mode only moves it over.
 */
@Composable
private fun SongRow(song: Song, isEditing: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .lightClickable(enabled = !isEditing, onClickLabel = "Open ${song.name}", onClick = onOpen)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.8f.gridUnitsAsDp()),
    ) {
        if (isEditing) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DELETE_SIZE_UNITS.gridUnitsAsDp())
                    .lightClickable(onClickLabel = "Delete ${song.name}", onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                LightIcon(LightIcons.DELETE, size = DELETE_SIZE_UNITS, contentDescription = "Delete ${song.name}")
            }
        }
        Column {
            LightText(song.name, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LightText(song.meta, LightTextVariant.Detail, maxLines = 1)
        }
    }
}

/** The delete button's size, in grid units: about the name's height. */
private const val DELETE_SIZE_UNITS = 1.25f
