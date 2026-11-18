package com.stephenmorris.recorder.tape

/** How a typed name or notes are kept (spec §§11–12). */
object NameEdit {
    /** The typed name, trimmed; an empty one keeps [old]. */
    fun renamed(old: String, typed: String): String = typed.trim().ifEmpty { old }

    /** The typed notes, without trailing whitespace. */
    fun notes(typed: String): String = typed.trimEnd()
}
