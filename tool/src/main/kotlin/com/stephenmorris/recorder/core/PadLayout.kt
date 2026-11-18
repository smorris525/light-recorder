package com.stephenmorris.recorder.core

/** The Pads screen's fixed 2 x 4 grid (spec §10): rows top to bottom, two pads each. */
object PadLayout {
    val ORDER: List<List<Sound>> = listOf(
        listOf(Sound.TOM, Sound.SHAKER),
        listOf(Sound.SIDESTICK, Sound.RIDE),
        listOf(Sound.SNARE, Sound.OPEN_HAT),
        listOf(Sound.KICK, Sound.HAT),
    )
}
