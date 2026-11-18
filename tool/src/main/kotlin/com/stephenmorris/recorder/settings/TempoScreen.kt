package com.stephenmorris.recorder.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.session.PatternSession
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Tempo (spec §13): the BPM, big, between hold-to-repeat arrows, over a keypad of 1–9, TAP, 0 and delete. Each key
 * gives the keyboard's click; TAP acts on touch-down.
 */
class TempoScreen(sealedActivity: SealedLightActivity, private val session: PatternSession) :
    LightScreen<Unit, TempoViewModel>(sealedActivity) {

    override val viewModelClass: Class<TempoViewModel>
        get() = TempoViewModel::class.java

    override fun createViewModel() = TempoViewModel(lightContext, session)

    @Composable
    override fun Content() {
        val model = viewModel.model
        val digits by model.digits.collectAsState()
        SettingsPage("Tempo", session, onBack = { goBack() }) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp()),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepArrow(LightIcons.DOWN, "Slower", visible = true, repeats = true) { model.step(-it) }
                    Box(Modifier.width(150.dp).height(90.dp), contentAlignment = Alignment.Center) {
                        LightText(digits, LightTextVariant.Title)
                    }
                    StepArrow(LightIcons.UP, "Faster", visible = true, repeats = true) { model.step(it) }
                }
                Keypad(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
                    showsDelete = digits.isNotEmpty(),
                    onDigit = model::press,
                    onDelete = model::delete,
                    onTap = model::tap,
                )
            }
        }
    }
}

/** 1–9, then TAP, 0 and delete, filling the space it's given. */
@Composable
private fun Keypad(
    modifier: Modifier,
    showsDelete: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onTap: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
    val tap by rememberUpdatedState(onTap)
    Column(modifier = modifier) {
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (digit in row) {
                    Key(Modifier.weight(1f).lightClickable(onClickLabel = "$digit") { onDigit(digit) }) {
                        LightText("$digit", LightTextVariant.Heading)
                    }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Key(
                Modifier.weight(1f).pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        tap()
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    }
                },
            ) {
                LightText("TAP", LightTextVariant.Button)
            }
            Key(Modifier.weight(1f).lightClickable(onClickLabel = "0") { onDigit(0) }) {
                LightText("0", LightTextVariant.Heading)
            }
            Key(
                if (showsDelete) {
                    Modifier.weight(1f).lightClickable(onClickLabel = "Delete", onClick = onDelete)
                } else {
                    Modifier.weight(1f)
                },
            ) {
                if (showsDelete) LightIcon(LightIcons.BACK, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun Key(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
