package com.stephenmorris.recorder.core

/**
 * Taken when Settings opens (spec §5). Every meter and bar change while it's open is computed from this snapshot, so
 * exploring (4/4 -> 7/8 -> 4/4, or 4 bars -> 2 -> 4) loses nothing until Settings closes.
 */
class SettingsSnapshot(val meter: MeterId, bars: Int, val steps: StepGrid) {
    val bars: Int = MeterLayout.clampedBars(bars)

    /**
     * The pattern's steps for the meter and bar count now chosen: remapped at the original bar count, then resized.
     * Hits outside the result are already gone, so committing is just storing it.
     */
    fun steps(meter: MeterId, bars: Int): StepGrid = steps
        .remapped(this.meter, meter, this.bars)
        .resized(meter.layout.stepsPerBar, this.bars, MeterLayout.clampedBars(bars))
}
