package com.stephenmorris.recorder.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.core.BarWindow
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepGrid
import com.stephenmorris.recorder.session.PatternSession
import com.stephenmorris.recorder.session.SoundEditorLayout
import com.stephenmorris.recorder.session.StepActions
import com.stephenmorris.recorder.session.StepGesture
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a row of the editor starts, in dp from the editor's top: rows in a bar 6 dp apart, bars 20 dp apart. */
private fun SoundEditorLayout.rowTop(index: Int): Float =
    index * stepSize + (index - rows[index].bar) * SoundEditorLayout.STEP_SPACING + rows[index].bar * SoundEditorLayout.BAR_GAP

/** Where a step column starts, in dp from the editor's left. */
private fun SoundEditorLayout.columnLeft(column: Int): Float =
    SoundEditorLayout.LABEL_WIDTH + SoundEditorLayout.LABEL_SPACING + column * (stepSize + SoundEditorLayout.STEP_SPACING)

/** The step under a point, counted from the window's first step, or `null` between or outside cells. */
private fun SoundEditorLayout.cellAt(x: Float, y: Float): Int? {
    val index = rows.indices.firstOrNull { y >= rowTop(it) && y < rowTop(it) + stepSize } ?: return null
    val row = rows[index]
    val column = (0 until row.steps).firstOrNull { x >= columnLeft(it) && x < columnLeft(it) + stepSize } ?: return null
    return row.firstStep + column
}

/**
 * The sound screen's steps (spec §9): one row per beat, labelled with its number, a square cell per step with its
 * dot, and a line under the playing step. Tap toggles, holding sets an accent (with one buzz), and dragging paints.
 */
@Composable
fun StepEditor(
    session: PatternSession,
    sound: Sound,
    layout: SoundEditorLayout,
    window: BarWindow,
    steps: StepGrid,
    playhead: Int?,
) {
    val color = LightThemeTokens.colors.content
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
    val scope = rememberCoroutineScope()
    val actions = object : StepActions {
        override fun tap(step: Int) = session.tap(sound, window.firstStep + step)

        override fun accent(step: Int) {
            session.accent(sound, window.firstStep + step)
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        override fun beginPaint(step: Int) = session.beginPaint(sound, window.firstStep + step)

        override fun paint(step: Int) = session.paint(sound, window.firstStep + step)

        override fun endPaint() = session.endPaint()
    }
    val current by rememberUpdatedState(actions)

    Box(
        modifier = Modifier
            .size(layout.width.dp, layout.height.dp)
            .pointerInput(layout, window) {
                awaitEachGesture {
                    val gesture = StepGesture({ x, y -> layout.cellAt(x, y) }, current)
                    val down = awaitFirstDown()
                    if (!gesture.down(down.position.x.toDp().value, down.position.y.toDp().value)) {
                        return@awaitEachGesture
                    }
                    val hold = scope.launch {
                        delay(StepGesture.HOLD_MS)
                        if (gesture.holdPending) gesture.holdElapsed()
                    }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            hold.cancel()
                            gesture.cancel()
                            break
                        }
                        val x = change.position.x.toDp().value
                        val y = change.position.y.toDp().value
                        if (!change.pressed) {
                            hold.cancel()
                            gesture.up(x, y)
                            break
                        }
                        gesture.move(x, y)
                        if (!gesture.holdPending) hold.cancel()
                        change.consume()
                    }
                }
            },
    ) {
        layout.rows.forEachIndexed { index, row ->
            Box(
                modifier = Modifier
                    .offset(0.dp, layout.rowTop(index).dp)
                    .size(SoundEditorLayout.LABEL_WIDTH.dp, layout.stepSize.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                LightText("${row.beat + 1}", LightTextVariant.Detail)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val playing = playhead?.minus(window.firstStep)
            layout.rows.forEachIndexed { index, row ->
                val top = layout.rowTop(index)
                for (column in 0 until row.steps) {
                    val step = row.firstStep + column
                    val left = layout.columnLeft(column)
                    val centre = Offset((left + layout.stepSize / 2).dp.toPx(), (top + layout.stepSize / 2).dp.toPx())
                    val diameter = layout.dot(steps[sound, window.firstStep + step]).dp.toPx()
                    drawCircle(color, radius = diameter / 2, center = centre)
                    if (step == playing) {
                        val width = layout.accentDot.dp.toPx()
                        drawRect(
                            color,
                            Offset(centre.x - width / 2, (top + layout.stepSize + 2).dp.toPx()),
                            Size(width, 2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
