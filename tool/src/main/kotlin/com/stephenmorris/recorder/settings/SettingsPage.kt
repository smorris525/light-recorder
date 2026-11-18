package com.stephenmorris.recorder.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.stephenmorris.recorder.session.PatternSession
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

/** Every Settings page (spec §10, as Drums §13): back and the page's title above, ▶/■ alone below, so playback works throughout. */
@Composable
fun SettingsPage(title: String, session: PatternSession, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val themeColors by LightThemeController.colors.collectAsState()
    val isPlaying by session.isPlaying.collectAsState()
    LightTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = onBack, contentDescription = "Back"),
                center = LightTopBarCenter.Text(title),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
            LightBottomBar(
                items = listOf(
                    null,
                    LightBarButton.LightIcon(
                        if (isPlaying) LightIcons.STOP else LightIcons.PLAY,
                        onClick = session::togglePlay,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                    ),
                    null,
                ),
            )
        }
    }
}
