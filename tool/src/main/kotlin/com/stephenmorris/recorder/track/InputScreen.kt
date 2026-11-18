package com.stephenmorris.recorder.track

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.stephenmorris.recorder.song.TransportPanel
import com.stephenmorris.recorder.tape.TrackInput
import com.stephenmorris.recorder.ui.ChoicePage
import com.stephenmorris.recorder.ui.ScreenFrame
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import java.util.UUID

/**
 * A track's input (spec §9): Microphone or Drums, the current one underlined, over the transport panel. Changing it
 * disarms the track, and leaves what's on its tape as it is.
 */
class InputScreen(sealedActivity: SealedLightActivity, private val trackId: UUID) :
    LightScreen<Unit, PanelViewModel>(sealedActivity) {

    override val viewModelClass: Class<PanelViewModel>
        get() = PanelViewModel::class.java

    override fun createViewModel() = PanelViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        val current = ready ?: return ScreenFrame { }
        current.model.songs.collectAsState().value
        val input = current.model.track(trackId)?.input
        ChoicePage(
            title = "Input",
            options = TrackInput.entries.map { it.displayName to (it == input) },
            onPick = { index -> current.editing.setInput(TrackInput.entries[index], on = trackId) },
            onBack = { goBack() },
            below = { TransportPanel(current) },
        )
    }
}
