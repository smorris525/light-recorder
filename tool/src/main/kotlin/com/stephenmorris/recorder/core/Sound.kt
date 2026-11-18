package com.stephenmorris.recorder.core

/**
 * One of the eight sounds every pattern has (spec §4). Patterns store [raw], so it must never change. Entry order is
 * the order of the overview's rows.
 */
enum class Sound(val raw: String) {
    KICK("kick"),
    SNARE("snare"),
    SIDESTICK("sidestick"),
    HAT("hat"),
    OPEN_HAT("openHat"),
    RIDE("ride"),
    TOM("tom"),
    SHAKER("shaker");

    /** The name shown on screen, in lowercase. */
    val displayName: String
        get() = if (this == OPEN_HAT) "open hat" else raw

    /** Other sounds this sound cuts off when it plays. Every sound also cuts off itself. */
    val chokes: Set<Sound>
        get() = if (this == HAT) setOf(OPEN_HAT) else emptySet()

    companion object {
        /** The sound stored as [raw], or `null` for a sound this version doesn't know. */
        fun fromRaw(raw: String): Sound? = entries.firstOrNull { it.raw == raw }
    }
}

/** How loud a step plays. A step with no level is a rest. Patterns store [raw]. */
enum class Level(val raw: Int) {
    HIT(1),
    ACCENT(2);

    /** Playback gain (spec §4). */
    val gain: Float
        get() = if (this == ACCENT) 1.0f else 0.6f

    companion object {
        /** The level stored as [raw]. Unknown or missing values read as a hit, so one bad entry loses nothing. */
        fun fromRaw(raw: Int?): Level = entries.firstOrNull { it.raw == raw } ?: HIT
    }
}
