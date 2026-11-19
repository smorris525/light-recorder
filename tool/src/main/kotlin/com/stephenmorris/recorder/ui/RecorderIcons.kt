package com.stephenmorris.recorder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** An icon drawn on a square [grid], scaled to whatever size it's given, in [color]. As Drums' `DrawnIcon`. */
private class DrawnIcon(
    private val grid: Float,
    private val color: Color,
    /** Whether the icon keeps room for an underline, drawn or not, so it doesn't move. */
    private val underlinable: Boolean = false,
    private val underlined: Boolean = false,
    private val draw: DrawScope.(Color) -> Unit,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        // An underlinable icon is given a box UNDERLINABLE_SIZE_UNITS across and draws itself at its neighbours' size in
        // the middle of it, with room for the underline below whether it's drawn or not, so nothing moves.
        val box = minOf(size.width, size.height)
        val side = if (underlinable) box * BAR_ICON_UNITS / RecorderIcons.UNDERLINABLE_SIZE_UNITS else box
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        translate(left, top) {
            scale(side / grid, pivot = Offset.Zero) { draw(color) }
        }
        if (underlined) {
            val gap = (size.height - side) / 2
            drawRect(color, Offset(left, top + side + gap / 2 - 1.dp.toPx()), Size(side, 2.dp.toPx()))
        }
    }

    private companion object {
        /** A bar icon's size, in grid units: the SDK's. */
        const val BAR_ICON_UNITS = 2f
    }
}

/**
 * Recorder's own icons, beside the SDK's `LightIcons`. The SDK draws a disabled icon as an
 * enabled one, so these take [dimmed] and draw in the theme's secondary colour, as the SDK dims text.
 */
object RecorderIcons {
    /** The box, in grid units, for an underlinable icon in a bar: the SDK's 2 units, plus room for the underline. */
    const val UNDERLINABLE_SIZE_UNITS = 2.8f

    @Composable
    private fun tint(dimmed: Boolean): Color =
        if (dimmed) LightThemeTokens.colors.contentSecondary else LightThemeTokens.colors.content

    /** Drums: a bass drum from the front on two splayed legs, with the pedal's beater on its head, on a 40 grid. */
    @Composable
    fun drums(): Painter {
        val color = tint(false)
        return remember(color) {
            DrawnIcon(40f, color) { tint ->
                val stroke = Stroke(3f)
                drawCircle(tint, 16f, Offset(20f, 20f), style = stroke)
                val lines = Path().apply {
                    moveTo(20f, 24f)
                    lineTo(20f, 36f)
                    moveTo(8.7f, 31.3f)
                    lineTo(5f, 36f)
                    moveTo(31.3f, 31.3f)
                    lineTo(35f, 36f)
                }
                drawPath(lines, tint, style = stroke)
                drawCircle(tint, 4.4f, Offset(20f, 21.5f))
            }
        }
    }

    /**
     * A jack, beside a Mic/Line track's number while it records from an interface (spec §8):
     * a 1/4" plug on a diagonal, tip to the upper right, its cable a lighter curl at the lower left, on a
     * 40 grid.
     */
    @Composable
    fun jack(): Painter {
        val color = tint(false)
        return remember(color) {
            DrawnIcon(40f, color) { tint ->
                // The plug drawn upright, tip up, then scaled, turned 45° and placed.
                val scale = 1.3f
                val turn = (PI / 4).toFloat()
                fun plug(x: Float, y: Float): Offset {
                    val px = x * scale
                    val py = y * scale
                    return Offset(21.5f + px * cos(turn) - py * sin(turn), 18.5f + px * sin(turn) + py * cos(turn))
                }
                fun Path.moveTo(point: Offset) = moveTo(point.x, point.y)
                fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)
                fun Path.curveTo(first: Offset, second: Offset, end: Offset) =
                    cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
                val round = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                val tip = Path().apply {
                    moveTo(plug(-2.1f, -14.8f))
                    lineTo(plug(-2.1f, -17.2f))
                    lineTo(plug(0f, -19.6f))
                    lineTo(plug(2.1f, -17.2f))
                    lineTo(plug(2.1f, -14.8f))
                    close()
                }
                drawPath(tip, tint)

                // A gap from the tip, as a plug's insulating ring.
                drawLine(tint, plug(0f, -13.4f), plug(0f, -7f), strokeWidth = 3.4f * scale, cap = StrokeCap.Butt)

                // The handle: a rounded outline, tapering into the cable.
                val half = 4.3f
                val handle = Path().apply {
                    moveTo(plug(-half, -6f))
                    lineTo(plug(half, -6f))
                    lineTo(plug(half, 4f))
                    curveTo(plug(half, 7f), plug(2f, 8.5f), plug(0f, 8.5f))
                    curveTo(plug(-2f, 8.5f), plug(-half, 7f), plug(-half, 4f))
                    close()
                }
                drawPath(handle, tint, style = round)

                // Lighter than the plug, so the plug leads.
                val cable = Path().apply {
                    moveTo(plug(0f, 8.5f))
                    curveTo(Offset(8f, 31f), Offset(6f, 37f), Offset(10.5f, 37.5f))
                    curveTo(Offset(15.5f, 39f), Offset(19f, 30f), Offset(26f, 38.5f))
                }
                drawPath(cable, tint, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }

    /** The in point, `[`, underlined while set. */
    @Composable
    fun inPoint(underlined: Boolean, dimmed: Boolean = false): Painter = bracket(opens = true, underlined, dimmed)

    /** The out point, `]`, underlined while set. */
    @Composable
    fun outPoint(underlined: Boolean, dimmed: Boolean = false): Painter = bracket(opens = false, underlined, dimmed)

    @Composable
    private fun bracket(opens: Boolean, underlined: Boolean, dimmed: Boolean): Painter {
        val color = tint(dimmed)
        return remember(color, opens, underlined) {
            DrawnIcon(24f, color, underlinable = true, underlined = underlined) { tint ->
                val upright = if (opens) 8.5f else 15.5f
                val foot = if (opens) 15.5f else 8.5f
                val path = Path().apply {
                    moveTo(foot, 2.5f)
                    lineTo(upright, 2.5f)
                    lineTo(upright, 21.5f)
                    lineTo(foot, 21.5f)
                }
                drawPath(path, tint, style = Stroke(1.5f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            }
        }
    }

    /** Arm, under a track's fader: a circle, underlined while armed. */
    @Composable
    fun arm(underlined: Boolean, dimmed: Boolean = false): Painter {
        val color = tint(dimmed)
        return remember(color, underlined) {
            DrawnIcon(24f, color, underlinable = true, underlined = underlined) { tint ->
                drawCircle(tint, 9f - 0.9f, Offset(12f, 12f), style = Stroke(1.8f))
            }
        }
    }

    /** Record: a solid circle, a little inside its 24 place, underlined while recording. */
    @Composable
    fun record(underlined: Boolean, dimmed: Boolean = false): Painter {
        val color = tint(dimmed)
        return remember(color, underlined) {
            DrawnIcon(24f, color, underlinable = true, underlined = underlined) { tint ->
                drawCircle(tint, 11f, Offset(12f, 12f))
            }
        }
    }

    /** Live pads: 2 x 2 rounded squares out to the edges of a 24 grid, as Drums'. */
    @Composable
    fun pads(): Painter {
        val color = tint(false)
        return remember(color) {
            DrawnIcon(24f, color) { tint ->
                for ((x, y) in listOf(0.9f to 0.9f, 13.5f to 0.9f, 0.9f to 13.5f, 13.5f to 13.5f)) {
                    drawRoundRect(tint, Offset(x, y), Size(9.6f, 9.6f), CornerRadius(2.5f), style = Stroke(1.8f))
                }
            }
        }
    }

    /** The step grid: the SDK dialpad's dots, 4 x 4, on a 40 grid, as Drums'. */
    @Composable
    fun stepGrid(): Painter {
        val color = tint(false)
        return remember(color) {
            DrawnIcon(40f, color) { tint ->
                val diameter = 6f
                val pitch = (40f - diameter) / 3
                for (row in 0 until 4) {
                    for (column in 0 until 4) {
                        drawCircle(tint, diameter / 2, Offset(column * pitch + diameter / 2, row * pitch + diameter / 2))
                    }
                }
            }
        }
    }

    /** The click, Drums': a tapering case, its arm swung out to the right, underlined while on. */
    @Composable
    fun metronome(underlined: Boolean): Painter {
        val color = tint(false)
        return remember(color, underlined) {
            DrawnIcon(40f, color, underlinable = true, underlined = underlined) { tint ->
                val case = Path().apply {
                    moveTo(11.5f, 1.5f)
                    lineTo(18.5f, 1.5f)
                    lineTo(28f, 38.5f)
                    lineTo(2f, 38.5f)
                    close()
                    moveTo(11f, 31f)
                    lineTo(33f, 7f)
                }
                drawPath(case, tint, style = Stroke(3f))
                drawCircle(tint, 5f, Offset(33f, 7f))
            }
        }
    }

    /** A beat of the lead-in's count, drawn in record's place, as Drums'. */
    @Composable
    fun countIn(number: Int): Painter {
        val color = tint(false)
        val measurer: TextMeasurer = rememberTextMeasurer()
        val style = LightThemeTokens.typography.heading.copy(color = color)
        return remember(number, color) {
            val layout = measurer.measure("$number", style)
            object : Painter() {
                override val intrinsicSize: Size = Size.Unspecified

                override fun DrawScope.onDraw() {
                    drawText(
                        layout,
                        topLeft = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2),
                    )
                }
            }
        }
    }
}
