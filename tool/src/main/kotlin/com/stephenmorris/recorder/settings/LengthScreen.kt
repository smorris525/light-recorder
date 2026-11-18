package com.stephenmorris.recorder.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.core.MeterLayout
import com.stephenmorris.recorder.core.SettingsRows
import com.stephenmorris.recorder.session.PatternSession
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant

/** Length (spec §13): the bar count, big, with ⌄ and ⌃ either side stepping it from 1 to 8. */
class LengthScreen(sealedActivity: SealedLightActivity, private val session: PatternSession) :
    LightScreen<Unit, SettingsPageViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsPageViewModel>
        get() = SettingsPageViewModel::class.java

    override fun createViewModel() = SettingsPageViewModel(lightContext)

    @Composable
    override fun Content() {
        val pattern by session.pattern.collectAsState()
        SettingsPage("Length", session, onBack = { goBack() }) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val bars = pattern.bars
                StepArrow(LightIcons.DOWN, "Fewer bars", bars > MeterLayout.BAR_RANGE.first, repeats = false) {
                    session.setBars(SettingsRows.bars(session.pattern.value.bars, -it))
                }
                Box(Modifier.width(150.dp).height(90.dp), contentAlignment = Alignment.Center) {
                    LightText("$bars", LightTextVariant.Title)
                }
                StepArrow(LightIcons.UP, "More bars", bars < MeterLayout.BAR_RANGE.last, repeats = false) {
                    session.setBars(SettingsRows.bars(session.pattern.value.bars, it))
                }
            }
        }
    }
}
