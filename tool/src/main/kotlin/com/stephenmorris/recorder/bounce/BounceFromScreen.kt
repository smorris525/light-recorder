package com.stephenmorris.recorder.bounce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.engine.BounceChoice
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.song.asGridUnits
import com.stephenmorris.recorder.ui.ROW_INSET
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable

/** A Bounce page's view model: only there to stop sound when the tool is paused. */
class BouncePageViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context)

/**
 * Bounce's From (spec §15): Track 1…4, each with LightOS's select box, ticked while picked. A
 * tap picks or unpicks, and the page stays open. A track with nothing recorded is dimmed and can't be picked.
 */
class BounceFromScreen(sealedActivity: SealedLightActivity, private val choice: BounceChoice) :
    LightScreen<Unit, BouncePageViewModel>(sealedActivity) {

    override val viewModelClass: Class<BouncePageViewModel>
        get() = BouncePageViewModel::class.java

    override fun createViewModel() = BouncePageViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        val sources by choice.sources.collectAsState()
        ScreenFrame {
            LightTopBar(leftButton = backButton { goBack() }, center = LightTopBarCenter.Text("From"))
            val model = ready?.model ?: return@ScreenFrame
            model.songs.collectAsState().value
            val song = model.song
            for (track in song.tracks) {
                val canPick = track.contentEnd > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(enabled = canPick, onClickLabel = track.name) { choice.toggle(track.number, song) }
                        .padding(horizontal = ROW_INSET, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LightIcon(
                        if (track.number in sources) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                        size = 24.dp.asGridUnits(),
                        contentDescription = null,
                    )
                    LightText(track.name, LightTextVariant.Copy, lighten = !canPick)
                }
            }
        }
    }
}
