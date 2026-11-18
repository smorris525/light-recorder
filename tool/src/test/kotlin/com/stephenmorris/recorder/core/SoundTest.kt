package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundTest {
    @Test
    fun soundsAreInOverviewOrder() {
        assertEquals(
            listOf(
                Sound.KICK, Sound.SNARE, Sound.SIDESTICK, Sound.HAT,
                Sound.OPEN_HAT, Sound.RIDE, Sound.TOM, Sound.SHAKER,
            ),
            Sound.entries,
        )
    }

    // Patterns store these strings; renaming one would orphan stored hits.
    @Test
    fun rawValuesAreStable() {
        assertEquals(
            listOf("kick", "snare", "sidestick", "hat", "openHat", "ride", "tom", "shaker"),
            Sound.entries.map { it.raw },
        )
    }

    @Test
    fun namesAreLowercase() {
        assertEquals(
            listOf("kick", "snare", "sidestick", "hat", "open hat", "ride", "tom", "shaker"),
            Sound.entries.map { it.displayName },
        )
    }

    @Test
    fun fromRawFindsKnownSoundsOnly() {
        assertEquals(Sound.OPEN_HAT, Sound.fromRaw("openHat"))
        assertNull(Sound.fromRaw("cowbell"))
    }

    @Test
    fun hatChokesOpenHat() {
        assertEquals(setOf(Sound.OPEN_HAT), Sound.HAT.chokes)
        assertTrue(Sound.OPEN_HAT.chokes.isEmpty())
        assertTrue(Sound.KICK.chokes.isEmpty())
    }

    @Test
    fun accentIsLouderThanHit() {
        assertEquals(1.0f, Level.ACCENT.gain)
        assertEquals(0.6f, Level.HIT.gain)
        assertTrue(Level.HIT < Level.ACCENT)
    }

    @Test
    fun unknownLevelDecodesAsHit() {
        assertEquals(
            listOf(Level.HIT, Level.ACCENT, Level.HIT, Level.HIT),
            listOf(1, 2, 7, null).map { Level.fromRaw(it) },
        )
    }
}
