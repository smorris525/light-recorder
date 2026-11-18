package com.stephenmorris.recorder.core

/**
 * For a meter change (spec §5): each hit keeps its bar and beat number and moves to
 * `round(offset x newBeatLength / oldBeatLength)` within the beat. Hits that round past the end of their beat, or sit
 * in beats the new meter doesn't have, or lie outside the pattern, are dropped. Where two hits land on one step, the
 * louder level wins. A corrupt [bars] is clamped to 1..8.
 */
fun StepGrid.remapped(from: MeterId, to: MeterId, bars: Int): StepGrid {
    val old = from.layout
    val new = to.layout
    val length = old.stepsPerBar * MeterLayout.clampedBars(bars)
    return mapSteps { step ->
        if (step !in 0 until length) {
            return@mapSteps null
        }
        val bar = step / old.stepsPerBar
        val position = old.position(step % old.stepsPerBar)
        if (position.beat >= new.groups.size) {
            return@mapSteps null
        }
        val oldBeat = old.groups[position.beat]
        val newBeat = new.groups[position.beat]
        val offset = Math.round(position.offset.toDouble() * newBeat / oldBeat).toInt()
        if (offset >= newBeat) null else bar * new.stepsPerBar + new.starts[position.beat] + offset
    }
}

/**
 * For a bar-count change (spec §5): added bars repeat the existing bars in order (step `i`
 * copies step `i mod oldLength`); fewer bars drops bars from the end. Hits outside the old pattern are dropped.
 * Corrupt bar counts are clamped to 1..8.
 */
fun StepGrid.resized(stepsPerBar: Int, fromBars: Int, toBars: Int): StepGrid {
    val oldLength = stepsPerBar * MeterLayout.clampedBars(fromBars)
    val newLength = stepsPerBar * MeterLayout.clampedBars(toBars)
    return mapHits { hits ->
        buildMap {
            for ((step, level) in hits) {
                if (step !in 0 until oldLength) continue
                var copy = step
                while (copy < newLength) {
                    put(copy, level)
                    copy += oldLength
                }
            }
        }
    }
}

/** Moves every hit to `transform(step)`, dropping it where that's `null`; collisions keep the louder level. */
private fun StepGrid.mapSteps(transform: (Int) -> Int?): StepGrid = mapHits { hits ->
    buildMap {
        for ((step, level) in hits) {
            val target = transform(step) ?: continue
            val existing = get(target)
            put(target, if (existing != null && existing > level) existing else level)
        }
    }
}
