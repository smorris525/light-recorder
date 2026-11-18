package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuppressedHitsTest {
    @Test
    fun aSkipAppliesOnce() {
        val hits = SuppressedHits().apply { insert(4, Sound.KICK) }
        assertEquals(listOf(Sound.SNARE), hits.soundsToPlay(4, listOf(Sound.KICK, Sound.SNARE)))
        assertEquals(listOf(Sound.KICK, Sound.SNARE), hits.soundsToPlay(4, listOf(Sound.KICK, Sound.SNARE)))
    }

    // The hit was erased before its step came round; re-recorded later, it must play.
    @Test
    fun aStepClearsItsSkipsEvenWhenTheHitIsGone() {
        val hits = SuppressedHits().apply { insert(4, Sound.KICK) }
        assertEquals(listOf(Sound.SNARE), hits.soundsToPlay(4, listOf(Sound.SNARE)))
        assertEquals(listOf(Sound.KICK), hits.soundsToPlay(4, listOf(Sound.KICK)))
    }

    @Test
    fun otherStepsAreUntouched() {
        val hits = SuppressedHits().apply { insert(4, Sound.KICK) }
        assertEquals(listOf(Sound.KICK), hits.soundsToPlay(5, listOf(Sound.KICK)))
        assertTrue(hits.soundsToPlay(4, listOf(Sound.KICK)).isEmpty())
    }

    @Test
    fun removeAllClearsEverything() {
        val hits = SuppressedHits().apply {
            insert(4, Sound.KICK)
            removeAll()
        }
        assertEquals(listOf(Sound.KICK), hits.soundsToPlay(4, listOf(Sound.KICK)))
    }
}
