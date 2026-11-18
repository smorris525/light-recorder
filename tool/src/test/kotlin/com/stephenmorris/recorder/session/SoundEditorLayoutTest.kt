package com.stephenmorris.recorder.session

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundEditorLayoutTest {
    @Test
    fun fourFourOnTheLightScreen() {
        val layout = SoundEditorLayout(340f, 280f, MeterId.FOUR_FOUR, 1)
        assertEquals(64f, layout.stepSize)
        assertEquals(34f, layout.accentDot)
        assertEquals(18f, layout.hitDot)
        assertEquals(4f, layout.dot(null))
        assertEquals(34f, layout.dot(Level.ACCENT))
    }

    @Test
    fun moreThanFourBeatsCapAtFiftySix() {
        val layout = SoundEditorLayout(400f, 600f, MeterId.FIVE_FOUR, 1)
        assertEquals(56f, layout.stepSize)
        assertEquals(30f, layout.accentDot)
    }

    // Every meter, at every span the bar window produces, fits the screen without scrolling.
    @Test
    fun twelveEightFitsTheScreen() {
        val cases = listOf(MeterId.TWELVE_EIGHT to 1, MeterId.FIVE_FOUR to 1, MeterId.SIX_EIGHT to 1, MeterId.SIX_EIGHT to 2)
        for ((meter, span) in cases) {
            val layout = SoundEditorLayout(340f, 280f, meter, span)
            assertTrue(layout.height <= 280f, "${meter.raw} x $span: ${layout.height}")
            assertTrue(layout.width <= 340f, "${meter.raw} x $span: ${layout.width}")
            assertTrue(layout.stepSize >= 24f, "${meter.raw} x $span")
        }
    }

    @Test
    fun rowsListEachBeatOfEachBarInView() {
        val layout = SoundEditorLayout(340f, 280f, MeterId.SIX_EIGHT, 2)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), layout.rows.map { it.bar to it.beat })
        assertEquals(listOf(0, 3, 6, 9), layout.rows.map { it.firstStep })
    }
}
