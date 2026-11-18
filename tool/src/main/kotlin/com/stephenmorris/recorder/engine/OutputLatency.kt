package com.stephenmorris.recorder.engine

/** How far the speaker is behind what's been rendered. */
object OutputLatency {
    /**
     * Frames written but not yet heard: [written] less the frames played, extrapolated from an output timestamp
     * ([timestampFrame] heard at [timestampNanos]) to [nowNanos]. Never negative.
     */
    fun frames(written: Long, timestampFrame: Long, timestampNanos: Long, nowNanos: Long, sampleRate: Int): Long {
        val played = timestampFrame + (nowNanos - timestampNanos) * sampleRate / 1_000_000_000L
        return maxOf(0L, written - played)
    }
}

/**
 * Latency against one output track. A new track's position starts at 0 while the render clock keeps counting across
 * restarts, so only frames rendered since [trackStarted] count as written to it.
 */
class OutputClock {
    @Volatile
    private var trackStartFrame = 0L

    /** Call as a new track starts, before its writer renders anything. */
    fun trackStarted(renderedFrames: Long) {
        trackStartFrame = renderedFrames
    }

    fun latency(renderedFrames: Long, timestampFrame: Long, timestampNanos: Long, nowNanos: Long, sampleRate: Int): Long =
        OutputLatency.frames(renderedFrames - trackStartFrame, timestampFrame, timestampNanos, nowNanos, sampleRate)
}
