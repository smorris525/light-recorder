package com.stephenmorris.recorder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** The delete confirmation's view model. */
class DeleteConfirmViewModel(context: SealedLightContext) : RecorderViewModel<Boolean>(context)

/**
 * "Delete ‹name›?" (spec §14), in the style of the SDK's full-screen modal, with cancel at the bottom left and delete
 * at the bottom right, as Drums'. Its result is `true` to delete.
 */
class DeleteConfirmScreen(sealedActivity: SealedLightActivity, private val name: String) :
    LightScreen<Boolean, DeleteConfirmViewModel>(sealedActivity) {

    override val viewModelClass: Class<DeleteConfirmViewModel>
        get() = DeleteConfirmViewModel::class.java

    override fun createViewModel() = DeleteConfirmViewModel(lightContext)

    @Composable
    override fun Content() {
        ScreenFrame {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightText(text = "Delete $name?", variant = LightTextVariant.Copy, align = TextAlign.Center)
            }
            LightBottomBar(
                items = listOf(
                    LightBarButton.LightIcon(LightIcons.DENY, onClick = { goBack(false) }, contentDescription = "Cancel"),
                    null,
                    LightBarButton.LightIcon(LightIcons.ACCEPT, onClick = { goBack(true) }, contentDescription = "Delete"),
                ),
            )
        }
    }
}
