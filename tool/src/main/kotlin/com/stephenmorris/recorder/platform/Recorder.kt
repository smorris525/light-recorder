package com.stephenmorris.recorder.platform

import com.stephenmorris.recorder.engine.Clips
import com.stephenmorris.recorder.engine.MixMeasurer
import com.stephenmorris.recorder.engine.SongEditing
import com.stephenmorris.recorder.engine.SongModel
import com.stephenmorris.recorder.engine.Transport
import com.stephenmorris.recorder.store.ClickSetting
import com.stephenmorris.recorder.store.Delayer
import com.stephenmorris.recorder.store.SongStore
import com.thelightphone.sdk.SealedLightContext
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The tool's process-wide parts: the songs, the transport, the mix measurer and the tick loop. Built once, off the main
 * thread (reading the songs and rendering the kit are slow), as Drums' `Drums` object is.
 */
object Recorder {
    /** Everything a screen needs, once it's built. */
    class Ready(
        val model: SongModel,
        val transport: Transport,
        val measurer: MixMeasurer,
        val clips: Clips,
        val store: SongStore,
        val click: ClickSetting,
        val editing: SongEditing,
    )

    private val scope = MainScope()
    private val mutex = Mutex()

    @Volatile
    private var ready: Ready? = null

    /**
     * The parts, building them on first call. Every caller gets the same ones; later callers wait for the first. A
     * cancelled caller doesn't cancel the build.
     */
    suspend fun prepare(context: SealedLightContext): Ready {
        ready?.let { return it }
        return withContext(NonCancellable) {
            mutex.withLock { ready ?: build(context).also { ready = it } }
        }
    }

    private suspend fun build(context: SealedLightContext): Ready {
        val writer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "RecorderStore") }
        val store = SongStore(File(context.filesDir, "Songs")) { writer.execute(it) }
        val delayer = Delayer { millis, action ->
            val job = scope.launch {
                delay(millis)
                action()
            }
            ({ job.cancel() })
        }
        val click = ClickSetting.open(context)
        val clicks = click.load()
        return withContext(Dispatchers.Default) {
            val model = SongModel(store, delayer)
            store.removeUnused(model.songs.value)
            // The output's mix and the measurer read the same recordings, at the output's rate.
            val rate = StereoTrackOutput.nativeRate()
            val clips = Clips(store, rate.toDouble())
            val output = StereoTrackOutput.create(rate, clips.clipStore)
            val input = MicInput(rate)
            val transport = Transport(model, output, input, clips)
            val measurer = MixMeasurer(model, transport, clips, store)
            model.clicks = clicks
            Ready(model, transport, measurer, clips, store, click, SongEditing(model, transport, store))
        }.also { built -> scope.launch { TickLoop.run(built.transport, built.model) } }
    }
}
