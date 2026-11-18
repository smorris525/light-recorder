package com.stephenmorris.recorder.bounce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.engine.BounceChoice
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.ui.ROW_INSET
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.SummaryRow
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import kotlinx.coroutines.launch

/** Bounce's view model: the visit's From and To, and the bounce, which runs here so it finishes if the screen goes. */
class BounceViewModel(context: SealedLightContext) : RecorderViewModel<BounceScreen.Exit?>(context) {
    val choice = BounceChoice()

    fun bounce(ready: Recorder.Ready, then: () -> Unit) {
        viewModelScope.launch {
            if (choice.bounce(ready.measurer)) {
                then()
            }
        }
    }
}

/**
 * Bounce (spec §15): From and To, each opening its own page, the warning when the bounce would
 * replace To's recording, and BOUNCE alone at the bottom, disabled until From has a track other than just To.
 */
class BounceScreen(sealedActivity: SealedLightActivity) : LightScreen<BounceScreen.Exit?, BounceViewModel>(sealedActivity) {

    /** The bounce is on the tape: back to the song. */
    enum class Exit { BOUNCED }

    override val viewModelClass: Class<BounceViewModel>
        get() = BounceViewModel::class.java

    override fun createViewModel() = BounceViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Bounce(it) }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.ColumnScope.Bounce(ready: Recorder.Ready) {
        ready.model.songs.collectAsState().value
        val choice = viewModel.choice
        choice.sources.collectAsState().value
        val destination by choice.destination.collectAsState()
        choice.isBouncing.collectAsState().value
        val song = ready.model.song
        LightTopBar(leftButton = backButton { goBack(null) }, center = LightTopBarCenter.Text("Bounce"))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            SummaryRow("From", choice.summary()) { navigateTo({ BounceFromScreen(it, choice) }) }
            SummaryRow("To", "Track $destination") { navigateTo({ BounceToScreen(it, choice) }) }
            if (choice.replacesRecording(song)) {
                LightText(
                    "replaces track $destination's recording",
                    LightTextVariant.Paragraph,
                    modifier = Modifier.padding(horizontal = ROW_INSET),
                )
            }
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.Text("BOUNCE", enabled = choice.canBounce) {
                    viewModel.bounce(ready) { goBack(Exit.BOUNCED) }
                },
            ),
        )
    }
}
