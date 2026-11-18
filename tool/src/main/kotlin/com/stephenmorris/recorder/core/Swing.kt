package com.stephenmorris.recorder.core

/**
 * How far the off-beat 16ths are pushed late (spec §5). Applies to x/4 meters only. Patterns store [raw], so it must
 * never change. Entry order is the Swing page's order.
 */
enum class Swing(val raw: String) {
    OFF("off"),
    EIGHTH_LIGHT("eighthLight"),
    EIGHTH_HEAVY("eighthHeavy"),
    SIXTEENTH_LIGHT("sixteenthLight"),
    SIXTEENTH_HEAVY("sixteenthHeavy");

    /** The choice on the Swing page (spec §13). */
    val optionName: String
        get() = when (this) {
            OFF -> "Off"
            EIGHTH_LIGHT -> "1/8 light"
            EIGHTH_HEAVY -> "1/8 heavy"
            SIXTEENTH_LIGHT -> "1/16 light"
            SIXTEENTH_HEAVY -> "1/16 heavy"
        }

    /** Where the swung eighth falls within the beat; `null` when off. */
    private val ratio: Double?
        get() = when (this) {
            OFF -> null
            EIGHTH_LIGHT, SIXTEENTH_LIGHT -> 0.58
            EIGHTH_HEAVY, SIXTEENTH_HEAVY -> 0.66
        }

    /**
     * How late a step lands, in steps, given its place in a four-step beat. Steps
     * outside `0 until 4` don't swing.
     */
    fun delay(stepInBeat: Int): Double {
        val r = ratio ?: return 0.0
        if (stepInBeat !in 0 until 4) {
            return 0.0
        }
        val positions = when (this) {
            EIGHTH_LIGHT, EIGHTH_HEAVY -> listOf(0.0, r / 2, r, r + (1 - r) / 2)
            else -> listOf(0.0, r / 2, 0.5, 0.5 + r / 2)
        }
        return (positions[stepInBeat] - stepInBeat / 4.0) * 4
    }

    companion object {
        /** The swing stored as [raw]. Unknown or missing values read as off. */
        fun fromRaw(raw: String?): Swing = entries.firstOrNull { it.raw == raw } ?: OFF
    }
}
