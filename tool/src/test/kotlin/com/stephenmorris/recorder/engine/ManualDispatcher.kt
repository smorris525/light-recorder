package com.stephenmorris.recorder.engine

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/** A dispatcher that runs nothing until a test calls [runAll], so the test can look between steps. */
class ManualDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runAll() {
        while (queue.isNotEmpty()) {
            queue.removeFirst().run()
        }
    }
}
