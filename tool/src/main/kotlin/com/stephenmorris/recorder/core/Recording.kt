package com.stephenmorris.recorder.core

/** Where a take is (spec §10). */
enum class TakePhase {
    /** Clicking in before recording from stopped. */
    COUNTING_IN,
    RECORDING,
}

/** What the Record button, and leaving the Pads screen, do (spec §10). */
object RecordControl {
    enum class Action {
        /** From stopped: count in one bar, then play and record. */
        COUNT_IN,

        /** While playing: record from now on. */
        RECORD_NOW,

        /** During a count-in: cancel it and stop. */
        CANCEL_COUNT_IN,

        /** While recording: stop recording and keep playing. */
        STOP_RECORDING,
    }

    /** Leaving Pads: a take ends but playback carries on; a count-in that hasn't reached recording stops. */
    enum class LeavingPads { NOTHING, END_TAKE, STOP }

    fun tapped(phase: TakePhase?, isPlaying: Boolean): Action = when (phase) {
        TakePhase.COUNTING_IN -> Action.CANCEL_COUNT_IN
        TakePhase.RECORDING -> Action.STOP_RECORDING
        null -> if (isPlaying) Action.RECORD_NOW else Action.COUNT_IN
    }

    fun leavingPads(phase: TakePhase?): LeavingPads = when (phase) {
        TakePhase.COUNTING_IN -> LeavingPads.STOP
        TakePhase.RECORDING -> LeavingPads.END_TAKE
        null -> LeavingPads.NOTHING
    }
}

/**
 * A one-bar count-in placed on the audio clock: its clicks, when each sounds, and [endFrame], where the pattern's
 * first step plays. Count-in clicks fall on beats, so swing doesn't move them.
 */
class CountInSchedule(layout: MeterLayout, startFrame: Long, stepFrames: Double) {
    data class Click(val frame: Long, val kind: ClickKind, val number: Int)

    val clicks: List<Click> = layout.countInClicks().map {
        Click(startFrame + roundHalfAway(it.step * stepFrames), it.kind, it.number)
    }

    val endFrame: Long = startFrame + roundHalfAway(layout.stepsPerBar * stepFrames)

    /** The count showing at [frame] (spec §10): the last click heard, `null` before the first and once the pattern has started. */
    fun number(frame: Long): Int? {
        if (frame >= endFrame) {
            return null
        }
        return clicks.lastOrNull { it.frame <= frame }?.number
    }
}

/**
 * One recorded pad tap (spec §10): what it does to the grid, and whether its step's own playback of the sound must be
 * skipped once.
 */
object Take {
    data class Hit(
        /** The grid gained a hit, or an existing hit was upgraded to an accent. */
        val changed: Boolean,
        /**
         * The tap landed on a step not yet scheduled; the tap already sounded it, so that step's playback of the sound
         * is skipped once, whether or not the grid changed (no doubles).
         */
        val suppressesStep: Boolean,
    )

    /** [grid] with the tap recorded, and what the tap did. */
    fun record(sound: Sound, level: Level, placement: HitPlacement, grid: StepGrid): Pair<StepGrid, Hit> {
        val recorded = grid.recordingHit(sound, placement.step, level)
        return (recorded ?: grid) to Hit(changed = recorded != null, suppressesStep = !placement.isScheduled)
    }
}
