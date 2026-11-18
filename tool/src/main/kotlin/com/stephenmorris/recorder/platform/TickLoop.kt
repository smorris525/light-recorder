package com.stephenmorris.recorder.platform

import com.stephenmorris.recorder.engine.SongModel
import com.stephenmorris.recorder.engine.Transport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Ticks the transport: every [BUSY_TICK_MS] while a song is open, every [IDLE_TICK_MS] otherwise. Opening a song wakes
 * it at once.
 */
object TickLoop {
    const val BUSY_TICK_MS = 20L
    const val IDLE_TICK_MS = 1_000L

    /** Runs until cancelled, on the main thread. */
    suspend fun run(transport: Transport, model: SongModel) {
        model.openSongId.collectLatest { open ->
            while (true) {
                transport.tick()
                delay(if (open != null) BUSY_TICK_MS else IDLE_TICK_MS)
            }
        }
    }
}
