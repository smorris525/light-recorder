package com.stephenmorris.recorder.store

/** A [Delayer] whose waits end only when a test says so. */
class FakeDelayer : Delayer {
    private val pending = mutableListOf<() -> Unit>()

    val waiting: Int
        get() = pending.size

    override fun after(millis: Long, action: () -> Unit): () -> Unit {
        pending += action
        return { pending.remove(action) }
    }

    /** Ends every wait still going. */
    fun fire() {
        val actions = pending.toList()
        pending.clear()
        actions.forEach { it() }
    }
}
