package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.FakeDelayer
import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.Song
import kotlin.io.path.createTempDirectory

/**
 * A transport over fakes, with one song open ("Test", a new song). Time moves only through [advance], 20 ms a tick, as
 * the tick loop moves it; the mic is fed [inputLevel] for each 20 ms while it's open.
 */
class TransportFixture(song: Song = Song.new("Test")) {
    val rate = 48_000
    val store = SongStore(createTempDirectory("songs").toFile()) { it.run() }
    val delayer = FakeDelayer()
    val output = FakeOutput(rate)
    val mix: FakeMix
        get() = output.mix
    val input = FakeInput(rate) { output.mix.renderedFrames }
    var nanos = 0L
    var inputLevel = 0.5f
    val model: SongModel
    val clips = Clips(store, rate.toDouble())
    val transport: Transport

    init {
        store.save(listOf(song))
        model = SongModel(store, delayer)
        transport = Transport(model, output, input, clips) { nanos }
        model.open(song.id)
    }

    val song: Song
        get() = model.song

    fun frames(seconds: Double): Long = Math.round(seconds * rate)

    /** Moves the clock on by [millis] (a multiple of 20), ticking every 20 ms. */
    fun advance(millis: Int, feedInput: Boolean = true) {
        repeat(millis / TICK_MS) {
            mix.renderedFrames += TICK_FRAMES
            nanos += TICK_MS * 1_000_000L
            if (feedInput) {
                input.feed(FloatArray(TICK_FRAMES) { inputLevel })
            }
            transport.tick()
        }
    }

    /** Moves the clock on by [millis] without ticking, as a stalled main thread would, while the mic keeps capturing. */
    fun stall(millis: Int) {
        repeat(millis / TICK_MS) {
            mix.renderedFrames += TICK_FRAMES
            nanos += TICK_MS * 1_000_000L
            input.feed(FloatArray(TICK_FRAMES) { inputLevel })
        }
    }

    /** The open song's track at [index]. */
    fun track(index: Int) = song.tracks[index]

    companion object {
        const val TICK_MS = 20
        const val TICK_FRAMES = 960
    }
}
