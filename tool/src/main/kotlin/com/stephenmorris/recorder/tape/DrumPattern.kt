package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepGrid
import com.stephenmorris.recorder.core.Swing
import com.stephenmorris.recorder.core.resized
import java.util.UUID

/** A loop of drum steps, with the tempo, meter and swing it plays at. */
data class DrumPattern(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val bpm: Int = 100,
    val meter: MeterId = MeterId.FOUR_FOUR,
    val swing: Swing = Swing.OFF,
    /** How long the pattern runs before it repeats. */
    val bars: Int = 1,
    val steps: StepGrid = StepGrid.EMPTY,
) {
    val stepsPerLoop: Int
        get() = bars * meter.layout.stepsPerBar

    /** Seconds per step. */
    val stepDuration: Double
        get() = meter.layout.stepDuration(bpm)

    val loopDuration: Double
        get() = stepsPerLoop * stepDuration

    /** How late [step] lands under the swing, in seconds; none in meters that don't swing. */
    fun swingOffset(step: Int): Double {
        val layout = meter.layout
        if (!layout.swingAllowed) {
            return 0.0
        }
        return layout.swingDelay(swing, step % layout.stepsPerBar) * stepDuration
    }

    /** What plays on [step]: its hits, less any another hit on the step chokes. */
    fun sounding(step: Int): List<Pair<Sound, Level>> {
        val playing = Sound.entries.mapNotNull { sound -> steps[sound, step]?.let { sound to it } }
        return playing.filter { (sound, _) -> playing.none { (other, _) -> sound in other.chokes } }
    }

    /** The pattern's hits for [loops] passes [from] a time, swing included, in time order. */
    fun hits(from: Double, loops: Int): List<Hit> {
        val hits = mutableListOf<Hit>()
        for (loop in 0 until maxOf(0, loops)) {
            val loopStart = from + loop * loopDuration
            for (step in 0 until stepsPerLoop) {
                val at = loopStart + step * stepDuration + swingOffset(step)
                for ((sound, level) in sounding(step)) {
                    hits += Hit(at, sound, level)
                }
            }
        }
        return hits.sortedBy { it.time }
    }

    /** Resized as Light Drums does: new bars repeat the existing bars in order, and shortening drops bars from the end. */
    fun resized(bars: Int): DrumPattern =
        copy(bars = bars, steps = steps.resized(meter.layout.stepsPerBar, this.bars, bars))

    companion object {
        /** A plain rock beat in 4/4. */
        fun rock(name: String = "A"): DrumPattern {
            var steps = StepGrid.EMPTY
                .with(Sound.KICK, 0, Level.ACCENT)
                .with(Sound.KICK, 8, Level.HIT)
                .with(Sound.SNARE, 4, Level.HIT)
                .with(Sound.SNARE, 12, Level.ACCENT)
            for (step in 0 until 16 step 2) {
                steps = steps.with(Sound.HAT, step, if (step % 4 == 0) Level.ACCENT else Level.HIT)
            }
            return DrumPattern(name = name, steps = steps)
        }
    }
}
