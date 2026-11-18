package com.stephenmorris.recorder.pattern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.core.BarWindow
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepGrid
import com.stephenmorris.recorder.session.OverviewLayout
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable

/** The width of the sound-name column. */
val NAME_WIDTH: Dp = 72.dp

/** The x of each step's centre in a row, after the name column, in dp. */
fun OverviewLayout.centres(): List<Float> {
    var x = 0f
    return gaps.map { gap ->
        val centre = x + cell / 2
        x += cell + gap
        centre
    }
}

/** A short line over the playing step (spec §8), one accent dot wide. */
@Composable
fun TickRow(layout: OverviewLayout, window: BarWindow, playhead: Int?, rowHeight: Float) {
    val color = LightThemeTokens.colors.content
    val width = layout.dot(com.stephenmorris.recorder.core.Level.ACCENT, rowHeight)
    Row(modifier = Modifier.fillMaxWidth().height(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(NAME_WIDTH).height(2.dp)) {}
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val index = playhead?.minus(window.firstStep) ?: return@Canvas
            if (index !in layout.gaps.indices) return@Canvas
            val centre = layout.centres()[index].dp.toPx()
            drawRect(color, Offset(centre - width.dp.toPx() / 2, 0f), Size(width.dp.toPx(), size.height))
        }
    }
}

/** One sound's row: its name, then a circular dot per step in view. Tapping anywhere on it opens the sound. */
@Composable
fun OverviewRow(
    sound: Sound,
    steps: StepGrid,
    layout: OverviewLayout,
    window: BarWindow,
    rowHeight: Float,
    onOpen: () -> Unit,
) {
    val color = LightThemeTokens.colors.content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight.dp)
            .lightClickable(onClickLabel = "Edit ${sound.displayName}", onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(sound.displayName, LightTextVariant.Detail, modifier = Modifier.width(NAME_WIDTH))
        Canvas(Modifier.fillMaxWidth().height(rowHeight.dp)) {
            layout.centres().forEachIndexed { index, centre ->
                val diameter = layout.dot(steps[sound, window.firstStep + index], rowHeight).dp.toPx()
                drawCircle(color, radius = diameter / 2, center = Offset(centre.dp.toPx(), size.height / 2))
            }
        }
    }
}
