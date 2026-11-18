package com.stephenmorris.recorder.pads

import com.stephenmorris.recorder.core.Sound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PadGridTest {
    // Two fingers on two pads each find their own pad; gaps find none.
    @Test
    fun padAtMapsTheTwoByFourGrid() {
        assertEquals(Sound.TOM, PadGrid.padAt(50f, 50f, 300f, 400f, 10f))
        assertEquals(Sound.SHAKER, PadGrid.padAt(250f, 50f, 300f, 400f, 10f))
        assertEquals(Sound.HAT, PadGrid.padAt(250f, 390f, 300f, 400f, 10f))
        assertEquals(Sound.SNARE, PadGrid.padAt(50f, 250f, 300f, 400f, 10f))
        assertNull(PadGrid.padAt(150f, 50f, 300f, 400f, 10f))
        assertNull(PadGrid.padAt(50f, 97f, 300f, 400f, 10f))
    }

    // Only a touch's down plays a pad: sliding from one pad onto another doesn't.
    @Test
    fun slidingDoesNotTrigger() {
        val touches = PadTouches { x, y -> PadGrid.padAt(x, y, 300f, 400f, 10f) }
        assertEquals(Sound.TOM, touches.down(0, 50f, 50f))
        assertNull(touches.move(0, 250f, 50f))
        assertEquals(Sound.SHAKER, touches.down(1, 250f, 50f))
    }

    @Test
    fun fittingTextSizeShrinksForSidestick() =
        assertEquals(20f, PadGrid.fittingTextSize({ it * 5 }, padWidth = 116f, start = 24f))

    @Test
    fun fittingTextSizeKeepsStartWhenItFits() =
        assertEquals(24f, PadGrid.fittingTextSize({ it * 5 }, padWidth = 200f, start = 24f))

    @Test
    fun fittingTextSizeStopsAtTheMinimum() =
        assertEquals(8f, PadGrid.fittingTextSize({ it * 50 }, padWidth = 100f, start = 24f))
}
