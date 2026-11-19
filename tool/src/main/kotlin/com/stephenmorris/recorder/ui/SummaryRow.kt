package com.stephenmorris.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** The inset of a screen's rows: one grid unit, in line with the top bar's back button, as LightOS lists and Drums. */
val ROW_INSET: Dp
    @Composable get() = 1f.gridUnitsAsDp()

/** The screen's margin. */
val SCREEN_MARGIN = 24.dp

/**
 * A small label over a value, the whole row opening its own page, as Drums' Settings. A row with
 * no [open] is locked: dimmed, and it doesn't open.
 */
@Composable
fun SummaryRow(label: String, value: String, open: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(enabled = open != null, onClickLabel = label) { open?.invoke() }
            .padding(horizontal = ROW_INSET),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LightText(label, LightTextVariant.Detail, lighten = open == null)
        LightText(value, LightTextVariant.Copy, lighten = open == null, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
