package com.stephenmorris.recorder.core

/** A sound to start, and how loud. */
data class Trigger(val sound: Sound, val gain: Float)

/**
 * What the scheduler needs from a pattern at one moment (spec §4, §5). Clamps the tempo and bar count (stored values
 * may be corrupt), ignores hits outside the pattern, plays each hit at its level's gain, and leaves out the open hat
 * on a step that has a hat, which chokes it.
 */
class PatternPlayback(bpm: Int, meter: MeterId, bars: Int, swing: Swing, steps: StepGrid) {
    private val layout = meter.layout

    /** Seconds per step. */
    val stepDuration: Double = layout.stepDuration(bpm)

    val stepsPerBar: Int = layout.stepsPerBar

    val totalSteps: Int = layout.stepsPerBar * MeterLayout.clampedBars(bars)

    /** How late each step of the bar lands, in steps. */
    val swingDelays: List<Double> = (0 until layout.stepsPerBar).map { layout.swingDelay(swing, it) }

    /** Sounds to start at each step, in sound order. Steps with no hits are absent. */
    val triggersByStep: Map<Int, List<Trigger>> = buildMap<Int, MutableList<Trigger>> {
        for (sound in Sound.entries) {
            for ((step, level) in steps.hits(sound).toSortedMap()) {
                if (step !in 0 until totalSteps) continue
                val choked = Sound.entries.any { sound in it.chokes && steps[it, step] != null }
                if (choked) continue
                getOrPut(step) { mutableListOf() }.add(Trigger(sound, level.gain))
            }
        }
    }

    /** The beat click due on each step of the bar. */
    val clicks: Map<Int, ClickKind> = layout.beatClicks.associate { it.step to it.kind }
}
