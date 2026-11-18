package com.stephenmorris.recorder.store

/** Runs an action after a wait. Returns a function that cancels it, if it hasn't run yet. */
fun interface Delayer {
    fun after(millis: Long, action: () -> Unit): () -> Unit
}

/**
 * Saves the songs once they stop changing for [delayMillis] (0.5 s, spec §3), or at once on [saveNow], as when the tool
 * is paused. Main thread only.
 */
class SaveScheduler(
    private val delayer: Delayer,
    private val delayMillis: Long = 500,
    private val save: () -> Unit,
) {
    private var cancel: (() -> Unit)? = null
    private var isDirty = false

    /** Something changed: saves after the pause, which starts again. */
    fun changed() {
        isDirty = true
        cancel?.invoke()
        cancel = delayer.after(delayMillis) {
            cancel = null
            saveNow()
        }
    }

    /** Saves now, if anything has changed since the last save. */
    fun saveNow() {
        cancel?.invoke()
        cancel = null
        if (isDirty) {
            isDirty = false
            save()
        }
    }
}
