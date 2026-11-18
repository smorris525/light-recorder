package com.stephenmorris.recorder.song

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.panel.PointBrackets
import com.stephenmorris.recorder.panel.RulerMarks
import com.stephenmorris.recorder.panel.TapeFrame
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.ui.scaled
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlin.math.abs
import kotlinx.coroutines.delay

/** The ruler's row of times, then ticks, right on the lane's top. */
private val RULER_HEIGHT = 28.dp

/** Where a major tick runs, top to foot; the brackets stand as tall, their bottoms reaching [BRACKETS_FOOT]. */
private const val MAJOR_TOP = 14f
private const val TICK_FOOT = 25f
private const val MINOR_TOP = 21f
private const val BRACKETS_FOOT = 31f

/** A finger this near an end while dragging scrolls the tape, every [EDGE_INTERVAL_MS], [EDGE_STEP] of a screen. */
private const val EDGE = 24.0
private const val EDGE_INTERVAL_MS = 33L
private const val EDGE_STEP = 1.0 / 60

/** A drag begins once a finger moves this far. */
private const val DRAG_START = 6.0

/** A tap this near the playhead's marker follows it again. */
private const val MARKER_REACH = 22.0

/**
 * The ruler (spec §8): the playhead's time over its marker and each major tick's time, ticks, the in and
 * out points' brackets. A tap moves the playhead, or on its marker follows it again; a drag scrubs it by hundredths,
 * with a tap felt on each tick, and a finger held near either end scrolls the tape.
 */
@Composable
fun Ruler(ready: Recorder.Ready) {
    val model = ready.model
    val controls = ready.controls
    val transport = ready.transport
    model.songs.collectAsState().value
    val song = model.song
    val playheadState = model.playhead.collectAsState()
    val colors = LightThemeTokens.colors
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val style = LightThemeTokens.typography.superfine.scaled().copy(color = colors.content, fontFeatureSettings = "tnum")
    val playheadStyle = style.copy(fontWeight = FontWeight.Medium)
    val width = remember { mutableDoubleStateOf(1.0) }
    val fingerX = remember { mutableDoubleStateOf(0.0) }
    val edge = remember { mutableIntStateOf(0) }
    val currentSong by rememberUpdatedState(song)

    ScrubHaptics(ready)

    LaunchedEffect(edge.intValue) {
        while (edge.intValue != 0) {
            val tape = TapeFrame(width.doubleValue)
            controls.scroll(edge.intValue * tape.inner * EDGE_STEP, tape.inner)
            controls.scrub(tape.tapeX(fingerX.doubleValue), tape.inner)
            delay(EDGE_INTERVAL_MS)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .onSizeChanged { width.doubleValue = maxOf(1.0, it.width / density.toDouble()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startX = down.position.x / density.toDouble()
                    var x = startX
                    var hasMoved = false
                    val tape = { TapeFrame(width.doubleValue) }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        x = change.position.x / density.toDouble()
                        if (!change.pressed) {
                            break
                        }
                        if (!hasMoved && abs(x - startX) > DRAG_START) {
                            hasMoved = true
                        }
                        if (hasMoved) {
                            fingerX.doubleValue = x
                            controls.scrub(tape().tapeX(x), tape().inner)
                            edge.intValue = when {
                                x < EDGE -> -1
                                x > width.doubleValue - EDGE -> 1
                                else -> 0
                            }
                            change.consume()
                        }
                    }
                    if (!hasMoved && !transport.isTakeUnderWay) {
                        val marker = tape().x(model.playhead.value, currentSong.view)
                        if (abs(startX - marker) < MARKER_REACH && marker in 0.0..width.doubleValue) {
                            controls.followAgain()
                        } else {
                            controls.scrub(tape().tapeX(x), tape().inner)
                        }
                    }
                    controls.endScrub()
                    edge.intValue = 0
                }
            },
    ) {
        val playhead = playheadState.value
        val widthDp = size.width / density.toDouble()
        val (ticks, labels) = RulerMarks.layout(song, song.view, playhead, widthDp) { text, isPlayhead ->
            measurer.measure(text, if (isPlayhead) playheadStyle else style).size.width / density.toDouble()
        }
        val px = { dp: Double -> (dp * density).toFloat() }
        for (tick in ticks) {
            val top = if (tick.isMajor) MAJOR_TOP else MINOR_TOP
            drawLine(
                colors.content,
                Offset(px(tick.x), top * density),
                Offset(px(tick.x), TICK_FOOT * density),
                strokeWidth = (if (tick.isMajor) 1.5f else 1f) * density,
            )
        }
        for (label in labels) {
            val layout = measurer.measure(label.text, if (label.isPlayhead) playheadStyle else style)
            drawText(layout, topLeft = Offset(px(label.x), 0f))
        }
        val brackets = Path()
        for (upright in PointBrackets.uprights(song, song.view, widthDp)) {
            val foot = upright.x + if (upright.opens) PointBrackets.FOOT else -PointBrackets.FOOT
            val top = (MAJOR_TOP + 0.75f) * density
            val bottom = (BRACKETS_FOOT - 0.75f) * density
            brackets.moveTo(px(foot), top)
            brackets.lineTo(px(upright.x), top)
            brackets.lineTo(px(upright.x), bottom)
            brackets.lineTo(px(foot), bottom)
        }
        drawPath(brackets, colors.content, style = Stroke(1.5f * density, join = StrokeJoin.Miter))
        val marker = TapeFrame(widthDp).x(playhead, song.view)
        if (marker in 0.0..widthDp) {
            val left = px(marker - 5)
            val top = MAJOR_TOP * density
            val triangle = Path().apply {
                moveTo(left, top)
                lineTo(left + 10 * density, top)
                lineTo(left + 5 * density, top + 7 * density)
                close()
            }
            drawPath(triangle, colors.content)
        }
    }
}

/** A tap felt on each tick a scrub crosses, a firmer one on each labelled tick (spec §16). */
@Composable
private fun ScrubHaptics(ready: Recorder.Ready) {
    val haptics = LocalHapticFeedback.current
    val enabled by rememberUpdatedState(LocalHapticsEnabled.current)
    LaunchedEffect(ready) {
        var last = 0
        ready.controls.scrub.collect { scrub ->
            val crossed = scrub?.ticksCrossed ?: 0
            if (scrub != null && crossed > last && enabled) {
                haptics.performHapticFeedback(
                    if (scrub.isMajor) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick,
                )
            }
            last = crossed
        }
    }
}
