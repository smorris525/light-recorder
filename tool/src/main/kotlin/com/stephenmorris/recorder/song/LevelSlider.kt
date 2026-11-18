package com.stephenmorris.recorder.song

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.panel.SliderMath
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** The thick part: the scroll bar's thumb, the other thick line on the song screen. */
val FILL_THICKNESS = 5.dp

/** A break through a rail, marking home or the meter's hot zone. */
val RAIL_GAP = 4.dp

private val HANDLE_LENGTH = 3.dp
private val HANDLE_THICKNESS = 14.dp

/**
 * A level along a hairline (spec §8): a thin rail with a handle at [value], thick up to it when
 * [isFilled] (from [home] when [isCentred]), and a gap marking home while the handle is away. Upright when
 * [vertical], its start at the foot. A tap or drag anywhere along it sets it; it catches at home, with a tap felt as it
 * arrives.
 */
@Composable
fun LevelSlider(
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    home: Double?,
    isCentred: Boolean,
    isFilled: Boolean,
    vertical: Boolean,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    val rail = 0.1f.gridUnitsAsDp()
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentRange by rememberUpdatedState(range)
    val currentHome by rememberUpdatedState(home)

    Canvas(
        modifier = modifier.pointerInput(vertical) {
            fun along(x: Float, y: Float): Double =
                (if (vertical) size.height - y else x) / density.toDouble()
            val length = { (if (vertical) size.height else size.width) / density.toDouble() }
            awaitEachGesture {
                var isCaught: Boolean? = null
                fun slide(at: Double) {
                    val slide = SliderMath.slide(at, length(), currentRange, currentHome)
                    if (slide.isHome && isCaught == false && hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    isCaught = slide.isHome
                    currentOnChange(slide.value)
                }
                val down = awaitFirstDown()
                slide(along(down.position.x, down.position.y))
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        break
                    }
                    slide(along(change.position.x, change.position.y))
                    change.consume()
                }
            }
        },
    ) {
        val length = if (vertical) size.height else size.width
        val position = (SliderMath.position(value, length.toDouble(), range)).toFloat()
        val origin = if (isCentred && home != null) SliderMath.position(home, length.toDouble(), range).toFloat() else 0f
        val fill = FILL_THICKNESS.toPx()
        bar(vertical, 0f, length, rail.toPx(), colors.content)
        if (isFilled) {
            bar(vertical, minOf(position, origin), kotlin.math.abs(position - origin), fill, colors.content)
        }
        if (home != null && value != home) {
            val at = SliderMath.position(home, length.toDouble(), range).toFloat()
            val gap = RAIL_GAP.toPx()
            bar(vertical, at - gap / 2, gap, fill + 2.dp.toPx(), colors.background)
        }
        val handle = HANDLE_LENGTH.toPx()
        bar(vertical, (position - handle / 2).coerceIn(0f, maxOf(0f, length - handle)), handle, HANDLE_THICKNESS.toPx(), colors.content)
    }
}

/**
 * A bar along the rail from [start], [length] long and [thickness] across, centred across it; upright, measured from
 * the foot.
 */
fun DrawScope.bar(vertical: Boolean, start: Float, length: Float, thickness: Float, color: Color) {
    if (vertical) {
        drawRect(color, Offset((size.width - thickness) / 2, size.height - start - length), Size(thickness, length))
    } else {
        drawRect(color, Offset(start, (size.height - thickness) / 2), Size(length, thickness))
    }
}
