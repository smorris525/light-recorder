package com.stephenmorris.recorder.tape

/**
 * What the transport sends the mix: [MixRenderer], or a fake in tests. [send], [trigger] and [stopVoices] are the main
 * thread's; [renderedFrames] is the render clock every frame refers to.
 */
interface MixSink {
    val renderedFrames: Long

    fun send(event: MixEvent): Boolean

    fun trigger(bus: Int, voice: Int, frame: Long, gain: Float)

    fun stopVoices()

    fun takeTrackPeak(slot: Int): Float
}
