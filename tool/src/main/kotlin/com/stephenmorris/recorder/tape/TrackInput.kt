package com.stephenmorris.recorder.tape

/**
 * Where a track records from: the mic or line in (an interface's input), or the drum machine. Songs store [raw], so it
 * must never change.
 */
enum class TrackInput(val raw: String) {
    MIC("mic"),
    DRUMS("drums");

    /** The name shown on screen. */
    val displayName: String
        get() = when (this) {
            MIC -> "Mic/Line"
            DRUMS -> "Drums"
        }

    companion object {
        /** The input stored as [raw], or `null` for one this version doesn't know. */
        fun fromRaw(raw: String?): TrackInput? = entries.firstOrNull { it.raw == raw }
    }
}
