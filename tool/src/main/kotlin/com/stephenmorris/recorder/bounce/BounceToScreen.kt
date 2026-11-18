package com.stephenmorris.recorder.bounce

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.stephenmorris.recorder.engine.BounceChoice
import com.stephenmorris.recorder.ui.ChoicePage
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity

/**
 * Bounce's To (spec §15): Track 1…4, the current one underlined. It stays open on a pick, as every other
 * choice page does. Any track can be picked.
 */
class BounceToScreen(sealedActivity: SealedLightActivity, private val choice: BounceChoice) :
    LightScreen<Unit, BouncePageViewModel>(sealedActivity) {

    override val viewModelClass: Class<BouncePageViewModel>
        get() = BouncePageViewModel::class.java

    override fun createViewModel() = BouncePageViewModel(lightContext)

    @Composable
    override fun Content() {
        val destination by choice.destination.collectAsState()
        ChoicePage(
            title = "To",
            options = (1..4).map { "Track $it" to (it == destination) },
            onPick = { index -> choice.pickDestination(index + 1) },
            onBack = { goBack() },
        )
    }
}
