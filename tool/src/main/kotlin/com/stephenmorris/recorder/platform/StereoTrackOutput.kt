package com.stephenmorris.recorder.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.engine.AudioOutput
import com.stephenmorris.recorder.engine.InterfaceRoute
import com.stephenmorris.recorder.engine.OutputClock
import com.stephenmorris.recorder.engine.Stamp
import com.stephenmorris.recorder.tape.ClipStore
import com.stephenmorris.recorder.tape.MixRenderer

/**
 * Streams the mix to the speaker through one stereo, float, low-latency [AudioTrack] at the device's native rate (spec
 * §4.1), from a writer thread at audio priority, as Drums' `TrackAudioOutput` does in mono. Build it off the main thread:
 * [create] renders the kit. While it plays, it tells [route] which device it plays to.
 */
class StereoTrackOutput private constructor(
    override val sampleRate: Int,
    clips: ClipStore,
    private val route: InterfaceRoute,
) : AudioOutput {
    override val mix = MixRenderer(sampleRate.toDouble(), RecorderKit.voices(sampleRate.toDouble()), BLOCK_FRAMES, clips)
    private val timestamp = AudioTimestamp()
    private val clock = OutputClock()
    private var track: AudioTrack? = null
    private var writer: Thread? = null
    private var bufferFrames = BLOCK_FRAMES * 2

    @Volatile
    private var running = false

    @Volatile
    private var trackStartFrame = 0L
    private var loggedLatency = false
    private val routing = AudioRouting.OnRoutingChangedListener { reportRoute(it) }

    override val isRunning: Boolean
        get() = running && writer?.isAlive == true

    override val latencyFrames: Long
        get() {
            val track = track ?: return bufferFrames.toLong()
            if (!running || !track.getTimestamp(timestamp)) {
                return bufferFrames.toLong()
            }
            val frames = clock.latency(
                mix.renderedFrames,
                timestamp.framePosition,
                timestamp.nanoTime,
                System.nanoTime(),
                sampleRate,
            )
            if (!loggedLatency) {
                loggedLatency = true
                Log.i(TAG, "output: rate $sampleRate Hz, buffer $bufferFrames frames, latency ${frames * 1000 / sampleRate} ms")
            }
            return frames
        }

    override fun outputStamp(): Stamp? {
        val track = track ?: return null
        if (!running || !track.getTimestamp(timestamp)) {
            return null
        }
        return Stamp(trackStartFrame + timestamp.framePosition, timestamp.nanoTime)
    }

    override fun start(): Boolean {
        if (isRunning) {
            return true
        }
        // A writer that died on a write error (the audio server restarted, say) leaves a dead track: start afresh.
        suspend()
        val built = build() ?: return false
        track = built
        return try {
            built.play()
            trackStartFrame = mix.renderedFrames
            clock.trackStarted(mix.renderedFrames)
            running = true
            loggedLatency = false
            writer = Thread(::writeLoop, "RecorderAudio").apply { start() }
            reportRoute(built)
            built.addOnRoutingChangedListener(routing, null)
            true
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "AudioTrack wouldn't play", exception)
            false
        }
    }

    override fun suspend() {
        if (!running && writer == null && track == null) {
            return
        }
        track?.removeOnRoutingChangedListener(routing)
        route.outputRouted(null)
        running = false
        writer?.join()
        writer = null
        track?.let {
            Log.i(TAG, "output suspended, underruns ${it.underrunCount}")
            it.stop()
            it.release()
        }
        track = null
    }

    private fun reportRoute(router: AudioRouting) {
        val device = router.routedDevice
        Log.i(TAG, "output routed to ${device?.productName} (type ${device?.type})")
        route.outputRouted(routeKind(device?.type))
    }

    private fun writeLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val left = FloatArray(BLOCK_FRAMES)
        val right = FloatArray(BLOCK_FRAMES)
        val interleaved = FloatArray(BLOCK_FRAMES * 2)
        val track = track ?: return
        while (running) {
            mix.render(left, right, BLOCK_FRAMES)
            for (frame in 0 until BLOCK_FRAMES) {
                interleaved[frame * 2] = left[frame]
                interleaved[frame * 2 + 1] = right[frame]
            }
            if (track.write(interleaved, 0, interleaved.size, AudioTrack.WRITE_BLOCKING) < 0) {
                Log.e(TAG, "AudioTrack write failed")
                return
            }
        }
    }

    private fun build(): AudioTrack? {
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        bufferFrames = maxOf(minimum / (Float.SIZE_BYTES * 2), BLOCK_FRAMES * 2)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferFrames * Float.SIZE_BYTES * 2)
                .build()
                .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (exception: Exception) {
            Log.e(TAG, "AudioTrack build failed", exception)
            null
        }
    }

    companion object {
        const val TAG = "RecorderAudio"

        /** Frames rendered per write. */
        const val BLOCK_FRAMES = 192

        /** The device's native output rate, which the output and every recording run at. */
        fun nativeRate(): Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)

        /** Renders the kit at [sampleRate]. Slow: call off the main thread. */
        fun create(sampleRate: Int, clips: ClipStore, route: InterfaceRoute): StereoTrackOutput =
            StereoTrackOutput(sampleRate, clips, route)
    }
}
