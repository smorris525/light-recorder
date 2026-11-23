package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.MixSink

/** A moment on one audio clock: [frame] was heard (or captured) at [nanos] on `System.nanoTime`. */
data class Stamp(val frame: Long, val nanos: Long)

/** Where the mix's sound goes (spec §4.1): the device's output, or a fake in tests. Main thread only. */
interface AudioOutput {
    val sampleRate: Int

    /** The mix this output plays. */
    val mix: MixSink

    /** Frames between the render clock and the speaker. */
    val latencyFrames: Long

    val isRunning: Boolean

    /** Starts sound if it isn't already going; `false` if it can't. */
    fun start(): Boolean

    /** Stops sound and releases the output. */
    fun suspend()

    /** The output's last timestamp, its frame on the render clock; `null` until it has one. */
    fun outputStamp(): Stamp?
}

/**
 * The mic (spec §4.2): open only while it's needed, its samples metered and kept in order for the transport to [drain].
 * Main thread only, but for [meter], which the input's thread writes.
 */
interface AudioInput {
    val isOpen: Boolean

    /** Opens the mic; `false` if the permission is missing or it won't open. */
    fun open(): Boolean

    fun close()

    /** Which input to keep, from 1, on an interface with more than one; set before or while open. */
    var channel: Int

    val meter: InputMeter

    /** The samples captured since the last drain, in order, as many as fit. Returns how many it copied. */
    fun drain(into: FloatArray): Int

    /** The input frame of the next sample [drain] returns, counted from when the mic opened. */
    val framesDrained: Long

    /** The input's last timestamp; `null` until it has one. */
    fun inputStamp(): Stamp?
}
