package com.stephenmorris.recorder.pads

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stephenmorris.recorder.engine.Transport
import com.stephenmorris.recorder.core.PadLayout
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.pattern.BarDots
import com.stephenmorris.recorder.pattern.PatternActionsScreen
import com.stephenmorris.recorder.pattern.PatternExit
import com.stephenmorris.recorder.ui.RenameScreen
import com.stephenmorris.recorder.session.PatternSession
import com.stephenmorris.recorder.settings.SettingsScreen
import com.stephenmorris.recorder.ui.RecorderIcons
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live pads (spec §10, as Drums §10): eight pads filling the screen that sound on touch-down, with several fingers at
 * once, and, while ● overdubs, land on the pattern's nearest step. Settings, Click, Play, ● and back to the step grid
 * sit below.
 */
class PadsScreen(sealedActivity: SealedLightActivity, private val session: PatternSession) :
    LightScreen<PatternExit?, PadsViewModel>(sealedActivity) {

    override val viewModelClass: Class<PadsViewModel>
        get() = PadsViewModel::class.java

    override fun createViewModel() = PadsViewModel(lightContext)

    override fun willShow() {
        viewModel.setShowing(true)
    }

    override fun willHide() {
        viewModel.setShowing(false)
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val ready by viewModel.ready.collectAsState()
        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                ready?.let { Pads(it.transport) }
            }
        }
    }

    @Composable
    private fun ColumnScope.Pads(transport: Transport) {
        val pattern by session.pattern.collectAsState()
        val window by session.window.collectAsState()
        val step by session.playingStep.collectAsState()
        val isPadRecording by transport.isPadRecording.collectAsState()
        val clickOn by viewModel.clickOn.collectAsState()
        val isPlaying by session.isPlaying.collectAsState()
        val playhead = session.playhead(step)

        LightTopBar(
            // Back from Pads returns to Patterns (spec §6), through Pattern.
            leftButton = LightBarButton.LightIcon(
                LightIcons.BACK,
                onClick = { goBack(PatternExit.ToPatterns) },
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(pattern.name, onClick = {
                navigateTo({ RenameScreen(it, session.pattern.value.name) }) { typed -> typed?.let(session::rename) }
            }),
            rightButton = LightBarButton.LightIcon(LightIcons.LIST, onClick = {
                navigateTo({ PatternActionsScreen(it, session) }) { exit -> exit?.let { goBack(it) } }
            }, contentDescription = "Actions"),
        )
        BarDots(window, playhead?.let(window::bar), session::showBar)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
        ) {
            PadGridView(maxWidth.value, maxHeight.value) { sound, touchMajorDp, pressure ->
                viewModel.probe.observe(touchMajorDp, pressure)
                transport.hit(sound, viewModel.probe.level(touchMajorDp, pressure))
            }
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    LightIcons.SETTINGS,
                    onClick = { navigateTo({ SettingsScreen(it, session) }) },
                    contentDescription = "Settings",
                ),
                LightBarButton.Icon(
                    RecorderIcons.metronome(underlined = clickOn),
                    onClick = viewModel::toggleClick,
                    contentDescription = "Click",
                    sizeUnits = RecorderIcons.UNDERLINABLE_SIZE_UNITS,
                ),
                LightBarButton.LightIcon(
                    if (isPlaying) LightIcons.STOP else LightIcons.PLAY,
                    onClick = session::togglePlay,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                ),
                LightBarButton.Icon(
                    RecorderIcons.record(underlined = isPadRecording),
                    onClick = { transport.recordIntoPattern(session.id) },
                    contentDescription = if (isPadRecording) "Stop recording" else "Record",
                    sizeUnits = RecorderIcons.UNDERLINABLE_SIZE_UNITS,
                ),
                LightBarButton.Icon(RecorderIcons.stepGrid(), onClick = { goBack(null) }, contentDescription = "Step grid"),
            ),
        )
    }

    /** The eight pads, [width] x [height] dp. A pad lights for 120 ms when it sounds. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun PadGridView(width: Float, height: Float, onHit: (Sound, Float, Float) -> Unit) {
        val density = LocalDensity.current
        val gap = 1f.gridUnitsAsDp().value
        val lit = remember { mutableStateMapOf<Sound, Long>() }
        val scope = rememberCoroutineScope()
        val measurer = rememberTextMeasurer()
        val touches = remember(width, height, gap) { PadTouches { x, y -> PadGrid.padAt(x, y, width, height, gap) } }
        // Copy's size, scaled for the screen as LightText scales it.
        val startSize = LightThemeTokens.typography.copy.fontSize.value.designVerticalPxToSp().value
        val style = LightThemeTokens.typography.copy.copy(lineHeight = TextUnit.Unspecified)
        val padWidth = (width - gap) / 2
        val textSize = remember(padWidth, startSize) {
            PadGrid.fittingTextSize(
                widths = { size ->
                    Sound.entries.maxOf { measurer.measure(it.displayName, style.copy(fontSize = size.sp)).size.width.toFloat() } /
                        density.density
                },
                padWidth = padWidth,
                start = startSize,
            )
        }
        val content = LightThemeTokens.colors.content
        val background = LightThemeTokens.colors.background

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    val index = event.actionIndex
                    val x = event.getX(index) / density.density
                    val y = event.getY(index) / density.density
                    val sound = when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                            touches.down(event.getPointerId(index), x, y)
                        MotionEvent.ACTION_MOVE -> touches.move(event.getPointerId(index), x, y)
                        else -> null
                    }
                    if (sound != null) {
                        onHit(sound, event.getTouchMajor(index) / density.density, event.getPressure(index))
                        val token = System.nanoTime()
                        lit[sound] = token
                        scope.launch {
                            delay(FLASH_MS)
                            if (lit[sound] == token) lit.remove(sound)
                        }
                    }
                    true
                },
            verticalArrangement = Arrangement.spacedBy(gap.dp),
        ) {
            for (row in PadLayout.ORDER) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    for (sound in row) {
                        val isLit = sound in lit
                        val shape = RoundedCornerShape(0.75f.gridUnitsAsDp())
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(if (isLit) content else background, shape)
                                .border(1.dp, content, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                sound.displayName,
                                style = style.copy(fontSize = textSize.sp, color = if (isLit) background else content),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** How long a pad stays lit after it sounds. */
        const val FLASH_MS = 120L
    }
}
