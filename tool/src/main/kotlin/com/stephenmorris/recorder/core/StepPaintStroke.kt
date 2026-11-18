package com.stephenmorris.recorder.core

/**
 * One drag across the step editor (spec §9). Its first cell decides whether every cell it visits is turned on (as a
 * hit, keeping existing accents) or erased. Each cell is handled once, including when the drag reverses.
 */
class StepPaintStroke(firstLevel: Level?) {
    val turnsOn: Boolean = firstLevel == null

    var hasChanges: Boolean = false
        private set

    private val visited = mutableMapOf<Sound, MutableSet<Int>>()

    /** [grid] with this stroke applied to [sound]'s [step]: the same instance when nothing changes. */
    fun apply(sound: Sound, step: Int, grid: StepGrid): StepGrid {
        if (!visited.getOrPut(sound) { mutableSetOf() }.add(step)) {
            return grid
        }
        val next = if (turnsOn) {
            grid.recordingHit(sound, step) ?: grid
        } else if (grid[sound, step] != null) {
            grid.with(sound, step, null)
        } else {
            grid
        }
        if (next !== grid) {
            hasChanges = true
        }
        return next
    }
}
