package com.stephenmorris.recorder.core

/**
 * Sounds whose next automatic playback on a step is skipped once (spec §10: a late tap lands on the next step, which
 * mustn't sound it a second time). Reaching a step clears all its skips, even for a sound no longer there (the hit was
 * erased), so a skip never lingers into a later pass.
 */
class SuppressedHits {
    private val entries = mutableMapOf<Int, MutableSet<Sound>>()

    fun insert(step: Int, sound: Sound) {
        entries.getOrPut(step) { mutableSetOf() }.add(sound)
    }

    fun removeAll() = entries.clear()

    /** The sounds to play on [step], using up its skips. */
    fun soundsToPlay(step: Int, sounds: List<Sound>): List<Sound> {
        val skipped = entries.remove(step) ?: return sounds
        return sounds.filter { it !in skipped }
    }
}
