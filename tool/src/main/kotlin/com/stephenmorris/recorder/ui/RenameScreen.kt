package com.stephenmorris.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Rename (spec §11): the SDK's text editor and keyboard, opening with the current name. DONE hands back what was
 * typed; back hands back nothing, leaving the name as it was. As Drums'.
 */
class RenameScreen(sealedActivity: SealedLightActivity, private val name: String) :
    SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptions = rememberKeyboardOptions()
        val state = rememberTextFieldState(name)
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Rename",
                state = state,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { typed -> goBack(typed.toString()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "DONE",
                singleLine = true,
            )
        }
    }
}
