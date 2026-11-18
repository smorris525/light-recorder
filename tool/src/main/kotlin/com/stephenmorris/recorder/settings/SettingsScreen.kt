package com.stephenmorris.recorder.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.stephenmorris.recorder.core.SettingsRows
import com.stephenmorris.recorder.session.PatternSession
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** The main Settings page (spec §13): a row per setting, its label over its summary, each opening its page. */
class SettingsScreen(sealedActivity: SealedLightActivity, private val session: PatternSession) :
    LightScreen<Unit, SettingsPageViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsPageViewModel>
        get() = SettingsPageViewModel::class.java

    override fun createViewModel() = SettingsPageViewModel(lightContext)

    @Composable
    override fun Content() {
        val pattern by session.pattern.collectAsState()
        SettingsPage("Settings", session, onBack = { goBack() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
                verticalArrangement = Arrangement.spacedBy(1.5f.gridUnitsAsDp()),
            ) {
                SummaryRow("Tempo", SettingsRows.tempo(pattern.bpm)) { navigateTo({ TempoScreen(it, session) }) }
                SummaryRow("Time Signature", pattern.meter.raw) {
                    navigateTo({ ChoiceScreen(it, session, ChoiceScreen.Kind.TIME_SIGNATURE) })
                }
                SummaryRow("Length", SettingsRows.length(pattern.bars)) { navigateTo({ LengthScreen(it, session) }) }
                if (SettingsRows.swingShown(pattern.meter)) {
                    SummaryRow("Swing", pattern.swing.optionName) {
                        navigateTo({ ChoiceScreen(it, session, ChoiceScreen.Kind.SWING) })
                    }
                }
            }
        }
    }
}

/** A setting's label over its summary; the whole row opens its page. */
@Composable
private fun SummaryRow(label: String, summary: String, onOpen: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().lightClickable(onClickLabel = label, onClick = onOpen)) {
        LightText(label, LightTextVariant.Detail)
        LightText(summary, LightTextVariant.Heading)
    }
}
