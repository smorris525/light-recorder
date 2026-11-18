package com.stephenmorris.recorder.song

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.panel.TapeFrame
import com.stephenmorris.recorder.panel.WaveformLines
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.gains
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlin.math.abs

/** The lane's height (spec §8). */
val LANE_HEIGHT = 30.dp

/** How far the lane's touches reach above it (the room under the ruler's ticks) and below it (over the bar). */
val LANE_REACH_ABOVE = 5.dp
val LANE_REACH_BELOW = 9.dp

/** The gap through a line over full scale. */
private const val CLIP_GAP = 4f

/**
 * The mix on the tape as one waveform (spec §8): the measured mix, stand-ins for
 * spans with no recording, and the take under way. Measured again, in the background, whenever what it's made from
 * changes; the stale measurement is cancelled.
 */
@Composable
fun Lane(ready: Recorder.Ready) {
    val model = ready.model
    model.songs.collectAsState().value
    val song = model.song
    val playheadState = model.playhead.collectAsState()
    val levelsState = ready.measurer.mixLevels.collectAsState()
    val liveTakeState = ready.transport.liveTake.collectAsState()
    val liveHitsState = ready.transport.liveHits.collectAsState()
    val colors = LightThemeTokens.colors
    val density = LocalDensity.current.density

    LaunchedEffect(MixLevels.Key(song), ready.transport.sampleRate) {
        ready.measurer.measure(song)
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(LANE_HEIGHT)) {
        // Read here, in the drawing, so the lane redraws as a take grows without recomposing the screen.
        val playhead = playheadState.value
        val tracks = song.tracks.map { track -> track.spans to track.gains.let { maxOf(it.left, it.right) } }
        val live = liveTakeState.value?.let { take ->
            model.track(take.track)?.let { track ->
                WaveformLines.Source(track.gains.let { maxOf(it.left, it.right) }) { from, to -> take.levels.level(from, to) }
            }
        }
        val hits = liveHitsState.value?.let { take ->
            model.track(take.track)?.let { track ->
                val until = if (take.isWritten) null else playhead
                WaveformLines.Source(track.gains.let { maxOf(it.left, it.right) }, until) { from, to -> take.levels.level(from, to) }
            }
        }
        val widthDp = size.width / density.toDouble()
        val heightDp = size.height / density.toDouble()
        val lines = WaveformLines.lines(widthDp, heightDp, song.view, levelsState.value, tracks, live, hits)
        for (line in lines) {
            val x = (line.x * density).toFloat()
            when (line) {
                is WaveformLines.Line.Bar -> {
                    val height = (line.height * density).toFloat()
                    drawRect(colors.content, Offset(x, (size.height - height) / 2), Size(density, height))
                }
                is WaveformLines.Line.Clipped -> {
                    val half = (size.height - CLIP_GAP * density) / 2
                    drawRect(colors.content, Offset(x, 0f), Size(density, half))
                    drawRect(colors.content, Offset(x, size.height - half), Size(density, half))
                }
            }
        }
        val marker = TapeFrame(widthDp).x(playhead, song.view)
        if (marker in 0.0..widthDp) {
            drawRect(colors.content, Offset((marker * density).toFloat(), 0f), Size(density, size.height))
        }
    }
}

/**
 * The lane's touches, in a band laid over the lane and the room either side ([modifier] sizes and
 * places it): a tap jumps the playhead there, a sideways drag scrolls the tape. Touches are ignored during a take
 * (spec §8).
 */
@Composable
fun LaneTouchArea(ready: Recorder.Ready, modifier: Modifier) {
    Box(modifier = modifier.then(laneTouch(ready)))
}

@Composable
private fun laneTouch(ready: Recorder.Ready): Modifier {
    val density = LocalDensity.current.density
    val width = remember { mutableDoubleStateOf(1.0) }
    return Modifier
        .onSizeChanged { width.doubleValue = maxOf(1.0, it.width / density.toDouble()) }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position
                var lastX: Double? = null
                var isSideways: Boolean? = null
                var x = start.x / density.toDouble()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    x = change.position.x / density.toDouble()
                    if (!change.pressed) {
                        break
                    }
                    val dx = (change.position.x - start.x) / density
                    val dy = (change.position.y - start.y) / density
                    if (isSideways == null && maxOf(abs(dx), abs(dy)) >= 6f) {
                        isSideways = abs(dx) > abs(dy)
                    }
                    if (isSideways == true && !ready.transport.isTakeUnderWay) {
                        // The tape moves with the finger: a drag to the right shows earlier tape.
                        val inner = TapeFrame(width.doubleValue).inner
                        ready.controls.scroll((lastX ?: (start.x / density.toDouble())) - x, inner)
                        lastX = x
                        change.consume()
                    }
                }
                if (isSideways == null && !ready.transport.isTakeUnderWay) {
                    val tape = TapeFrame(width.doubleValue)
                    ready.controls.scrub(tape.tapeX(x), tape.inner)
                    ready.controls.endScrub()
                }
            }
        }
}
