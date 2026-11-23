package com.stephenmorris.recorder.track

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.stephenmorris.recorder.song.TransportPanel
import com.stephenmorris.recorder.tape.inputChoices
import com.stephenmorris.recorder.tape.isChoice
import com.stephenmorris.recorder.tape.label
import com.stephenmorris.recorder.ui.ChoicePage
import com.stephenmorris.recorder.ui.ScreenFrame
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import java.util.UUID

/**
 * A track's input (spec §9): Mic/Line or Drums, the current one underlined, over the transport panel; with an
 * interface of two or more inputs, each of them in place of Mic/Line. Changing between Mic/Line and Drums disarms the
 * track; nothing changes what's on its tape.
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
        val inputCount by current.route.inputCount.collectAsState()
        // With an interface in, the mic opens for a moment to learn how many inputs it has, once a session.
        LaunchedEffect(Unit) {
            if (current.route.hasInterface.value && !current.route.isCounted) {
                current.transport.countInputs()
            }
        }
        val track = current.model.track(trackId)
        val choices = inputChoices(inputCount)
        ChoicePage(
            title = "Input",
            options = choices.map { it.label(inputCount) to (track?.isChoice(it, inputCount) == true) },
            onPick = { index -> current.editing.setInput(choices[index].input, choices[index].channel, on = trackId) },
            onBack = { goBack() },
            below = { TransportPanel(current) },
        )
    }
}
