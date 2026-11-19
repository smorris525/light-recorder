package com.stephenmorris.recorder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * A list of choices under back and [title], the current one underlined (spec §9). Picking one moves the underline
 * and stays on the page. [below] fills the rest: the transport panel on Input.
 */
@Composable
fun ChoicePage(
    title: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    below: @Composable () -> Unit = {},
) {
    ScreenFrame {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text(title))
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp())) {
            options.forEachIndexed { index, (label, isCurrent) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClickLabel = label) { onPick(index) }
                        // As Drums' choice pages: rows close together.
                        .padding(horizontal = ROW_INSET, vertical = 0.4f.gridUnitsAsDp()),
                ) {
                    UnderlinedText(label, isCurrent)
                }
            }
        }
        below()
    }
}
