package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.MixEvent
import com.stephenmorris.recorder.tape.MixSink

/** A trigger sent to a [FakeMix]. */
data class Trigger(val bus: Int, val voice: Int, val frame: Long, val gain: Float)

/** A mix that records what it's sent, with a clock the test moves. */
class FakeMix : MixSink {
    override var renderedFrames = 0L
    val events = mutableListOf<MixEvent>()
    val triggers = mutableListOf<Trigger>()
    var stopVoicesCount = 0
    val trackPeaks = FloatArray(4)

    override fun send(event: MixEvent): Boolean {
        events += event
        return true
    }

    override fun trigger(bus: Int, voice: Int, frame: Long, gain: Float) {
        triggers += Trigger(bus, voice, frame, gain)
    }

    override fun stopVoices() {
        stopVoicesCount += 1
    }

    override fun takeTrackPeak(slot: Int): Float = trackPeaks.getOrElse(slot) { 0f }.also {
        if (slot in trackPeaks.indices) {
            trackPeaks[slot] = 0f
        }
    }
}

/** An output over a [FakeMix], heard [latencyFrames] after it's rendered, whose timestamp is at 0 on both clocks. */
class FakeOutput(override val sampleRate: Int = 48_000) : AudioOutput {
    override val mix = FakeMix()
    override var latencyFrames = 0L
    override var isRunning = false
    var starts = 0
    var suspends = 0

    override fun start(): Boolean {
        if (!isRunning) {
            starts += 1
        }
        isRunning = true
        return true
    }

    override fun suspend() {
        if (isRunning) {
            suspends += 1
        }
        isRunning = false
    }

    override fun outputStamp(): Stamp = Stamp(frame = 0, nanos = 0)
}

/**
 * A mic whose input frame *k* was heard while render frame *opened + k + offsetFrames* played, against [FakeOutput]'s
 * timestamp. Tests [feed] it samples, which [drain] hands out in order.
 */
class FakeInput(
    private val sampleRate: Int = 48_000,
    var offsetFrames: Long = 0,
    /** The render frame playing now, so the input's first frame is placed where it opened. */
    private val clock: () -> Long = { 0 },
) : AudioInput {
    var granted = true
    var opens = 0
    var closes = 0
    override var isOpen = false
    override var channel = 1
    override val meter = InputMeter()
    override var framesDrained = 0L
    private val queued = ArrayDeque<Float>()
    private var openedAt = 0L

    override fun open(): Boolean {
        opens += 1
        isOpen = granted
        if (granted) {
            openedAt = clock()
            framesDrained = 0
        }
        return granted
    }

    override fun close() {
        closes += 1
        isOpen = false
        queued.clear()
    }

    /** The mic's reader dies (the audio server restarted, say): it reads as closed, and nothing more arrives. */
    fun die() {
        isOpen = false
    }

    /** Input arriving: metered, and queued for [drain] while open. */
    fun feed(samples: FloatArray) {
        if (!isOpen) {
            return
        }
        meter.record(samples, samples.size)
        samples.forEach { queued.addLast(it) }
    }

    override fun drain(into: FloatArray): Int {
        var count = 0
        while (count < into.size && queued.isNotEmpty()) {
            into[count] = queued.removeFirst()
            count += 1
        }
        framesDrained += count
        return count
    }

    override fun inputStamp(): Stamp = Stamp(frame = 0, nanos = (openedAt + offsetFrames) * 1_000_000_000L / sampleRate)
}
