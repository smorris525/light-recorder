package com.stephenmorris.recorder.core

/** Where a recorded hit lands. */
data class HitPlacement(
    val step: Int,
    /**
     * Whether that step has already been sent to the renderer. If not, its automatic playback of the hit's sound must
     * be skipped once, or the hit sounds twice.
     */
    val isScheduled: Boolean,
) {
    /** The first step of playback that hasn't been scheduled yet (the end of a count-in). */
    data class Upcoming(val step: Int, val frame: Long)
}

/** Recently scheduled steps, used to find the step sounding at a given frame of the audio clock. */
class PlayheadHistory {
    private val entries = ArrayDeque<Pair<Long, Int>>()

    /** [steps] must come in clock order, after any already recorded. */
    fun record(steps: List<PlaybackStep>) {
        steps.forEach { entries.addLast(it.frame to it.step) }
        while (entries.size > CAPACITY) {
            entries.removeFirst()
        }
    }

    fun reset() = entries.clear()

    /** The last step that started at or before [frame], or `null` if none has. */
    fun step(frame: Long): Int? = entries.lastOrNull { it.first <= frame }?.second

    /**
     * The step a hit heard at [frame] lands on (spec §10): the step sounding, or the next one if the hit came in the
     * second half of it. A hit less than half a step before the first step lands on that step, whether it's already
     * scheduled or still [upcoming] (slow tempos). `null` before that, or when nothing has played.
     */
    fun placement(frame: Long, stepLength: Double, totalSteps: Int, upcoming: HitPlacement.Upcoming? = null): HitPlacement? {
        val index = entries.indexOfLast { it.first <= frame }
        if (index < 0) {
            val first = entries.firstOrNull()
            if (first != null) {
                return if (first.first - frame <= stepLength / 2) HitPlacement(first.second, true) else null
            }
            if (upcoming == null || upcoming.frame - frame > stepLength / 2) {
                return null
            }
            return HitPlacement(upcoming.step, false)
        }
        val (currentFrame, currentStep) = entries[index]
        val elapsed = (frame - currentFrame).toDouble()
        if (index + 1 < entries.size) {
            val (nextFrame, nextStep) = entries[index + 1]
            val isLate = elapsed >= (nextFrame - currentFrame) / 2.0
            return HitPlacement(if (isLate) nextStep else currentStep, true)
        }
        if (elapsed >= stepLength / 2) {
            return HitPlacement((currentStep + 1) % maxOf(1, totalSteps), false)
        }
        return HitPlacement(currentStep, true)
    }

    companion object {
        const val CAPACITY = 64
    }
}
