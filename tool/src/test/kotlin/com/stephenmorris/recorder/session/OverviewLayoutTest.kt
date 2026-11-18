package com.stephenmorris.recorder.session

import com.stephenmorris.recorder.core.BarWindow
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverviewLayoutTest {
    private fun layout(width: Float, meter: MeterId, bars: Int = 1) =
        OverviewLayout(width, BarWindow.of(meter, bars, 0), meter)

    @Test
    fun phoneFourFour() {
        val overview = layout(270f, MeterId.FOUR_FOUR)
        assertEquals(15f, overview.cell)
        assertEquals(10f, overview.beatGap)
        assertEquals(listOf(0f, 0f, 0f, 10f, 0f, 0f, 0f, 10f, 0f, 0f, 0f, 10f, 0f, 0f, 0f, 0f), overview.gaps)
    }

    @Test
    fun phoneSixEightPair() {
        val overview = layout(270f, MeterId.SIX_EIGHT, bars = 2)
        assertEquals(19f, overview.cell)
        assertEquals(10f, overview.beatGap)
        assertEquals(22f, overview.barGap)
        assertEquals(listOf(0f, 0f, 10f, 0f, 0f, 22f, 0f, 0f, 10f, 0f, 0f, 0f), overview.gaps)
    }

    @Test
    fun phoneSevenEightWidensBeatGaps() {
        val overview = layout(270f, MeterId.SEVEN_EIGHT)
        assertEquals(33f, overview.cell)
        assertEquals(19f, overview.beatGap)
        assertEquals(listOf(0f, 19f, 0f, 19f, 0f, 0f, 0f), overview.gaps)
    }

    // The gaps and cells fill the row exactly when the leftovers divide evenly, and never overflow it.
    @Test
    fun fillsTheRow() {
        for (meter in MeterId.entries) {
            for (bars in listOf(1, 2)) {
                val overview = layout(270f, meter, bars)
                val used = overview.cell * overview.gaps.size + overview.gaps.sum()
                assertTrue(used <= 270f, "${meter.raw} x $bars")
                assertTrue(used > 270f - overview.gaps.size, "${meter.raw} x $bars")
            }
        }
    }

    // Dots keep 10 : 6 : 2 proportions, grow with the row, and stay circular within a cell (spec §8).
    @Test
    fun dotsStayCircularAndGrow() {
        val overview = layout(270f, MeterId.FOUR_FOUR).copy(cell = 18f)
        assertEquals(12f, overview.dot(Level.ACCENT, 35f))
        assertEquals(7f, overview.dot(Level.HIT, 35f))
        assertEquals(2f, overview.dot(null, 35f))
        assertEquals(7f, overview.dot(Level.ACCENT, 10f))
    }
}
