package com.stephenmorris.recorder.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.pattern.BarDots
import com.stephenmorris.recorder.session.PatternSession
import com.stephenmorris.recorder.session.SoundEditorLayout
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** The sound screen's view model: only there to stop sound when the tool is paused, as every screen's does. */
class SoundViewModel(context: SealedLightContext) : RecorderViewModel<Unit>(context)

/**
 * One sound's steps (spec §9), opened from a row of the pattern screen. It shares the pattern screen's session, so
 * the bars in view and playback carry on across both.
 */
class SoundScreen(sealedActivity: SealedLightActivity, private val session: PatternSession, private val sound: Sound) :
    LightScreen<Unit, SoundViewModel>(sealedActivity) {

    override val viewModelClass: Class<SoundViewModel>
        get() = SoundViewModel::class.java

    override fun createViewModel() = SoundViewModel(lightContext)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val pattern by session.pattern.collectAsState()
        val window by session.window.collectAsState()
        val step by session.playingStep.collectAsState()
        val isPlaying by session.isPlaying.collectAsState()
        val playhead = session.playhead(step)
        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }, contentDescription = "Back"),
                    center = LightTopBarCenter.Text(sound.displayName),
                )
                BarDots(window, playhead?.let(window::bar), session::showBar)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    // Kept across recompositions: every painted step recomposes, and a new layout would restart the
                    // editor's gesture handling mid-drag.
                    val layout = remember(maxWidth, maxHeight, pattern.meter, window.span) {
                        SoundEditorLayout(maxWidth.value, maxHeight.value, pattern.meter, window.span)
                    }
                    StepEditor(session, sound, layout, window, pattern.steps, playhead)
                }
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
}
