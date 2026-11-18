package com.stephenmorris.recorder.core

import kotlin.math.floor

/** One step placed on the audio clock. */
data class PlaybackStep(
    val step: Int,
    val frame: Long,
    val triggers: List<Trigger>,
    /** The beat click due here, for the transport to play or not as the click mode says. */
    val click: ClickKind?,
)

/**
 * Places pattern steps on the render clock ahead of playback. Call [advance] regularly with a horizon a little ahead
 * of the clock; it returns each step that starts before the horizon exactly once. The pattern is read on every call,
 * so tempo, swing and hit edits apply from the next unscheduled step. A shorter pattern (fewer bars, another meter)
 * carries on from the same step number, wrapped into the new length.
 */
class PlaybackScheduler(val sampleRate: Double, startStep: Int, startFrame: Long) {
    private var nextStep: Int = maxOf(0, startStep)

    /** The unswung start of [nextStep], in frames. Fractional, so step lengths don't drift. */
    private var nextStepTime: Double = startFrame.toDouble()

    fun advance(horizon: Long, now: Long, playback: PatternPlayback): List<PlaybackStep> {
        nextStep %= playback.totalSteps
        if (nextStepTime < now - LATE_LIMIT * sampleRate) {
            nextStepTime = now.toDouble()
        }
        val length = playback.stepDuration * sampleRate
        val earliest = roundHalfAway(now - LATE_TOLERANCE * sampleRate)
        val steps = mutableListOf<PlaybackStep>()
        while (true) {
            val stepInBar = nextStep % playback.stepsPerBar
            val frame = roundHalfAway(nextStepTime + playback.swingDelays[stepInBar] * length)
            if (frame >= horizon) {
                break
            }
            if (frame >= earliest) {
                steps += PlaybackStep(
                    nextStep,
                    frame,
                    playback.triggersByStep[nextStep].orEmpty(),
                    playback.clicks[stepInBar],
                )
            }
            nextStepTime += length
            nextStep = (nextStep + 1) % playback.totalSteps
        }
        return steps
    }

    companion object {
        /** How far behind the clock the next step may fall, in seconds, before the schedule restarts from now. */
        const val LATE_LIMIT = 0.2

        /**
         * How late a step may be, in seconds, and still play. Later steps are skipped, so a short stall drops the
         * missed steps instead of stacking them into one flam.
         */
        const val LATE_TOLERANCE = 0.02
    }
}

/** Rounds half away from zero. */
internal fun roundHalfAway(value: Double): Long =
    if (value >= 0) floor(value + 0.5).toLong() else -floor(-value + 0.5).toLong()
