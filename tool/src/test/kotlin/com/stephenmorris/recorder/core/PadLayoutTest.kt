package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PadLayoutTest {
    @Test
    fun orderIsTwoByFour() {
        assertEquals(
            listOf(
                listOf(Sound.TOM, Sound.SHAKER),
                listOf(Sound.SIDESTICK, Sound.RIDE),
                listOf(Sound.SNARE, Sound.OPEN_HAT),
                listOf(Sound.KICK, Sound.HAT),
            ),
            PadLayout.ORDER,
        )
    }

    @Test
    fun everySoundOnce() {
        assertEquals(8, PadLayout.ORDER.flatten().size)
        assertEquals(Sound.entries.toSet(), PadLayout.ORDER.flatten().toSet())
    }
}
