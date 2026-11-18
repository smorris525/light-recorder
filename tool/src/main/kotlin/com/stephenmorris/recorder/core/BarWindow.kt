package com.stephenmorris.recorder.core

/**
 * The bars in view on the Pattern, Sound and Pads screens (spec §8): one bar, or two
 * in a meter with two beats or fewer (6/8) once there are two bars. The window stays full, so it never starts past
 * `bars - span`.
 */
data class BarWindow private constructor(
    /** The first bar in view, from 0. */
    val firstBar: Int,
    /** How many bars are in view: 1 or 2. */
    val span: Int,
    val bars: Int,
    val stepsPerBar: Int,
) {
    /** The bars in view. */
    val barRange: IntRange
        get() = firstBar until firstBar + span

    /** The absolute steps in view. */
    val stepRange: IntRange
        get() = firstBar * stepsPerBar until (firstBar + span) * stepsPerBar

    /** The first step in view: where Play and Record start (spec §8). */
    val firstStep: Int
        get() = firstBar * stepsPerBar

    fun bar(step: Int): Int = step / stepsPerBar

    /** The window moved to start at [bar], clamped to stay full. */
    fun startingAt(bar: Int): BarWindow = copy(firstBar = bar.coerceIn(0, bars - span))

    companion object {
        /** A window starting at [firstBar], clamped to stay full. A corrupt [bars] is clamped to 1..8. */
        fun of(meter: MeterId, bars: Int, firstBar: Int): BarWindow {
            val layout = meter.layout
            val clamped = MeterLayout.clampedBars(bars)
            val span = if (layout.groups.size <= 2 && clamped > 1) 2 else 1
            return BarWindow(firstBar.coerceIn(0, clamped - span), span, clamped, layout.stepsPerBar)
        }
    }
}

/**
 * Whether the window follows the playhead (spec §8). Play turns it on. While it's on, when
 * the playhead leaves the window, the window jumps to start at the playing bar. Editing a step, or tapping a bar dot,
 * turns it off until the next Play, except tapping the playing bar's dot, which turns it back on.
 */
data class Follow(val isOn: Boolean = false) {
    fun played(): Follow = Follow(true)

    fun edited(): Follow = Follow(false)

    /** A bar dot was tapped. [playingBar] is the bar the playhead is in, or `null` when stopped. */
    fun tappedBar(bar: Int, playingBar: Int?): Follow = Follow(bar == playingBar)

    /**
     * Where the window goes as the playhead reaches [followingStep]: moved to the playing bar if following and the
     * playhead has left the window; otherwise unchanged.
     */
    fun window(window: BarWindow, followingStep: Int): BarWindow {
        val bar = window.bar(followingStep)
        return if (isOn && bar !in window.barRange) window.startingAt(bar) else window
    }
}
