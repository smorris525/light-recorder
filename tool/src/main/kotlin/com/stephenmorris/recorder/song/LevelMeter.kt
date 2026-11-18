package com.stephenmorris.recorder.song

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.panel.SliderMath
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.tape.Track
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlin.math.roundToInt

/**
 * A channel's meter (spec §8): upright beside its fader and drawn as it is, rising from the foot,
 * with a gap where the hot zone starts, level with the fader's 0 dB. It shows what's coming in while it's the armed
 * Microphone track, otherwise what the track is playing. CLIP shows on the hot zone for 2 s after full scale.
 */
@Composable
fun LevelMeter(ready: Recorder.Ready, track: Track, modifier: Modifier = Modifier) {
    val transport = ready.transport
    val arming by ready.model.arming.collectAsState()
    val isClipping by transport.isClipping.collectAsState()
    val isInput = arming.audio == track.id
    val level = remember { mutableFloatStateOf(0f) }
    val colors = LightThemeTokens.colors
    val rail = 0.1f.gridUnitsAsDp()

    // Read every frame into state the drawing alone reads, so only the meter redraws.
    LaunchedEffect(isInput, track.id) {
        while (true) {
            withFrameNanos { }
            level.floatValue = (if (isInput) transport.inputLevel() else transport.playbackLevel(track.id)).toFloat()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val length = size.height
            bar(vertical = true, 0f, length, rail.toPx(), colors.content)
            bar(vertical = true, 0f, length * level.floatValue.coerceIn(0f, 1f), FILL_THICKNESS.toPx(), colors.content)
            val gap = RAIL_GAP.toPx()
            bar(vertical = true, (length * SliderMath.VOLUME_HOME).toFloat() - gap / 2, gap, FILL_THICKNESS.toPx() + 2.dp.toPx(), colors.background)
        }
        if (isInput && isClipping) {
            val centre = maxHeight * (1 - SliderMath.VOLUME_HOME.toFloat()) / 2
            var textHeight by remember { mutableIntStateOf(0) }
            BasicText(
                "CLIP",
                style = LightThemeTokens.typography.superfine.copy(color = colors.content, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { textHeight = it.height }
                    .offset { IntOffset(0, (centre.toPx() - textHeight / 2f).roundToInt()) }
                    .background(colors.background)
                    .padding(vertical = 1.dp),
            )
        }
    }
}
