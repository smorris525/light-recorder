package com.stephenmorris.recorder.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.song.TransportPanel
import com.stephenmorris.recorder.tape.TrackInput
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.SummaryRow
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import java.util.UUID

/** The view model of every screen over the transport panel that needs nothing of its own. */
class PanelViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context)

/**
 * A track's screen (spec §9), over the transport panel: Input, and on Drums the pattern ● records
 * first. Its name is fixed, and there's nothing else to do with a track.
 */
class TrackScreen(sealedActivity: SealedLightActivity, private val trackId: UUID) :
    LightScreen<Unit, PanelViewModel>(sealedActivity) {

    override val viewModelClass: Class<PanelViewModel>
        get() = PanelViewModel::class.java

    override fun createViewModel() = PanelViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Track(it) }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.ColumnScope.Track(ready: Recorder.Ready) {
        ready.model.songs.collectAsState().value
        val isMicRefused by ready.transport.isMicRefused.collectAsState()
        val hasInterface by ready.route.hasInterface.collectAsState()
        ready.transport.take.collectAsState().value
        ready.transport.patternTake.collectAsState().value
        val track = ready.model.track(trackId) ?: return
        val song = ready.model.song
        LightTopBar(leftButton = backButton { goBack() }, center = LightTopBarCenter.Text(track.name))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            val inputName = if (track.input == TrackInput.MIC && isMicRefused) {
                "microphone not allowed"
            } else {
                track.input.displayName
            }
            // Locked while the track records, as a take can't change what it records from.
            SummaryRow(
                "Input",
                inputName,
                open = if (ready.editing.isRecording(on = trackId)) null else {
                    { navigateTo({ InputScreen(it, trackId) }) }
                },
            )
            // Open during a take too: Patterns is where the next pattern is queued.
            if (track.input == TrackInput.DRUMS) {
                SummaryRow("Pattern", song.currentPattern?.name.orEmpty()) {
                    navigateTo({ PatternsScreen(it) })
                }
            }
            // No monitoring through the app (spec §1): with an interface, the player hears themselves on its own.
            if (track.input == TrackInput.MIC && hasInterface) {
                LightText("to hear yourself, use your interface's monitor", LightTextVariant.Detail)
            }
        }
        TransportPanel(ready)
    }
}
