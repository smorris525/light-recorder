package com.stephenmorris.recorder.platform

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.stephenmorris.recorder.engine.AudioInput
import com.stephenmorris.recorder.engine.InputMeter
import com.stephenmorris.recorder.engine.InterfaceRoute
import com.stephenmorris.recorder.engine.Stamp
import com.stephenmorris.recorder.tape.CaptureRing

/**
 * The mic (spec §4.2): a plain [AudioRecord] at the output's rate, 16-bit mono, read on its own thread at audio priority
 * into [meter] and a 30 s lock-free ring that the transport drains. Android's default input: the phone's mic, or a
 * plugged-in headset's, or an interface's. Opening fails, returning `false`, without `RECORD_AUDIO`. While open, it tells
 * [route] which device it records from.
 */
class MicInput(private val sampleRate: Int, private val route: InterfaceRoute) : AudioInput {
    override val meter = InputMeter()
    private val ring = CaptureRing(sampleRate * RING_SECONDS)
    private val timestamp = AudioTimestamp()
    private var record: AudioRecord? = null
    private var reader: Thread? = null

    @Volatile
    private var running = false

    /** The reader stopped on an error: the mic reads as closed until it's opened again. */
    @Volatile
    private var failed = false
    private var loggedLatency = false
    // A change queued for a recorder since closed is ignored, so it can't clear the open one's route.
    private val routing = AudioRouting.OnRoutingChangedListener { if (it === record) reportRoute(it) }

    override var framesDrained = 0L
        private set

    override val isOpen: Boolean
        get() = record != null && !failed

    // RECORD_AUDIO is asked for before a Mic/Line track arms; without it AudioRecord won't initialise, as is checked.
    @SuppressLint("MissingPermission")
    override fun open(): Boolean {
        if (record != null && !failed) {
            return true
        }
        close()
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) {
            return false
        }
        val built = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, READ_FRAMES * 4 * Short.SIZE_BYTES),
            )
        } catch (exception: Exception) {
            Log.w(TAG, "mic wouldn't open: $exception")
            return false
        }
        if (built.state != AudioRecord.STATE_INITIALIZED) {
            built.release()
            Log.w(TAG, "mic wouldn't initialise (no permission?)")
            return false
        }
        // Listening before it starts, so no change of route is missed between starting and the first read.
        built.addOnRoutingChangedListener(routing, null)
        try {
            built.startRecording()
        } catch (exception: IllegalStateException) {
            discard(built)
            return false
        }
        if (built.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            discard(built)
            return false
        }
        // Anything left in the ring is from before: the frames count from this opening.
        val scrap = FloatArray(READ_FRAMES)
        while (ring.read(scrap) > 0) Unit
        framesDrained = 0
        loggedLatency = false
        record = built
        failed = false
        running = true
        reader = Thread({ readLoop(built) }, "RecorderMic").apply { start() }
        reportRoute(built)
        return true
    }

    override fun close() {
        val built = record ?: return
        built.removeOnRoutingChangedListener(routing)
        route.inputRouted(null)
        running = false
        built.stop()
        reader?.join()
        reader = null
        built.release()
        record = null
    }

    override fun drain(into: FloatArray): Int = ring.read(into).also { framesDrained += it }

    override fun inputStamp(): Stamp? {
        val built = record ?: return null
        if (built.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) != AudioRecord.SUCCESS) {
            return null
        }
        if (!loggedLatency) {
            loggedLatency = true
            val ahead = (System.nanoTime() - timestamp.nanoTime) / 1_000_000
            Log.i(TAG, "input: rate $sampleRate Hz, stamp frame ${timestamp.framePosition}, $ahead ms old")
        }
        return Stamp(timestamp.framePosition, timestamp.nanoTime)
    }

    private fun discard(built: AudioRecord) {
        built.removeOnRoutingChangedListener(routing)
        built.release()
    }

    private fun reportRoute(router: AudioRouting) {
        val device = router.routedDevice
        Log.i(TAG, "input routed to ${device?.productName} (type ${device?.type})")
        route.inputRouted(routeKind(device?.type))
    }

    private fun readLoop(built: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val shorts = ShortArray(READ_FRAMES)
        val floats = FloatArray(READ_FRAMES)
        while (running) {
            val count = built.read(shorts, 0, READ_FRAMES)
            if (count <= 0) {
                if (count < 0) {
                    Log.e(TAG, "mic read failed: $count")
                    failed = true
                }
                break
            }
            for (index in 0 until count) {
                floats[index] = shorts[index] / 32_768f
            }
            meter.record(floats, count)
            ring.write(floats, count)
        }
    }

    private companion object {
        const val TAG = StereoTrackOutput.TAG
        const val READ_FRAMES = 480
        const val RING_SECONDS = 30
    }
}
