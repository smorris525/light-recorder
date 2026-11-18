package com.stephenmorris.recorder.core

/** A playback with test defaults: 120 bpm, one bar of 4/4, no swing, no hits. */
fun testPlayback(
    bpm: Int = 120,
    meter: MeterId = MeterId.FOUR_FOUR,
    bars: Int = 1,
    swing: Swing = Swing.OFF,
    steps: StepGrid = StepGrid.EMPTY,
): PatternPlayback = PatternPlayback(bpm, meter, bars, swing, steps)

/** A grid of hits. */
fun grid(vararg hits: Triple<Sound, Int, Level>): StepGrid =
    hits.fold(StepGrid.EMPTY) { grid, (sound, step, level) -> grid.with(sound, step, level) }
