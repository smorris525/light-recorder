package com.stephenmorris.recorder.core

/**
 * Every sound's hits in a pattern: absolute step to level; a step with no level is a rest (spec §3). Immutable: every
 * change returns a new grid. Keyed by the sound's raw value, so hits for a sound this version doesn't know survive
 * being saved again (they're never shown or played). Sounds and step maps are removed when they empty, so equal
 * grids compare equal.
 */
class StepGrid private constructor(val hitsBySound: Map<String, Map<Int, Level>>) {

    operator fun get(sound: Sound, step: Int): Level? = hitsBySound[sound.raw]?.get(step)

    /** This grid with [sound]'s [step] set to [level], or made a rest when [level] is `null`. */
    fun with(sound: Sound, step: Int, level: Level?): StepGrid {
        val hits = hitsBySound[sound.raw].orEmpty().toMutableMap()
        if (level == null) hits.remove(step) else hits[step] = level
        val sounds = hitsBySound.toMutableMap()
        if (hits.isEmpty()) sounds.remove(sound.raw) else sounds[sound.raw] = hits
        return StepGrid(sounds)
    }

    /** [sound]'s hits, step to level. */
    fun hits(sound: Sound): Map<Int, Level> = hitsBySound[sound.raw].orEmpty()

    val isEmpty: Boolean
        get() = hitsBySound.isEmpty()

    /** A tap: toggles between a rest and a normal hit. Tapping an accent clears it too. */
    fun toggled(sound: Sound, step: Int): StepGrid = with(sound, step, if (this[sound, step] == null) Level.HIT else null)

    /**
     * A recorded pad tap: adds [level] on a rest, or upgrades a hit to an accent. Softer taps never erase an existing
     * accent. Returns `null` when nothing changes.
     */
    fun recordingHit(sound: Sound, step: Int, level: Level = Level.HIT): StepGrid? {
        val current = this[sound, step]
        if (current != null && current >= level) {
            return null
        }
        return with(sound, step, level)
    }

    /** Without hits outside `0 until length`, for every sound including unknown ones. */
    fun trimmed(length: Int): StepGrid = mapHits { hits -> hits.filterKeys { it in 0 until length } }

    /** Applies [transform] to each sound's hits, dropping sounds left empty. */
    internal fun mapHits(transform: (Map<Int, Level>) -> Map<Int, Level>): StepGrid =
        StepGrid(hitsBySound.mapValues { transform(it.value) }.filterValues { it.isNotEmpty() })

    override fun equals(other: Any?): Boolean = other is StepGrid && other.hitsBySound == hitsBySound

    override fun hashCode(): Int = hitsBySound.hashCode()

    override fun toString(): String = "StepGrid($hitsBySound)"

    companion object {
        val EMPTY = StepGrid(emptyMap())

        /** A grid from stored hits, dropping empty maps. */
        internal fun of(hitsBySound: Map<String, Map<Int, Level>>): StepGrid =
            StepGrid(hitsBySound.filterValues { it.isNotEmpty() })
    }
}
