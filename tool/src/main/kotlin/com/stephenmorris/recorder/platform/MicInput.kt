package com.stephenmorris.recorder.platform

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.stephenmorris.recorder.engine.AudioInput
import com.stephenmorris.recorder.engine.InputMeter
import com.stephenmorris.recorder.engine.Stamp
import com.stephenmorris.recorder.tape.CaptureRing

/**
 * The mic (spec §4.2): a plain [AudioRecord] at the output's rate, 16-bit mono, read on its own thread at audio priority
 * into [meter] and a 30 s lock-free ring that the transport drains. Android's default input: the phone's mic, or a
 * plugged-in headset's. Opening fails, returning `false`, without `RECORD_AUDIO`.
 */
class MicInput(private val sampleRate: Int) : AudioInput {
    override val meter = InputMeter()
    private val ring = CaptureRing(sampleRate * RING_SECONDS)
    private val timestamp = AudioTimestamp()
    private var record: AudioRecord? = null
    private var reader: Thread? = null

    @Volatile
    private var running = false
    private var loggedLatency = false

    override var framesDrained = 0L
        private set

    override val isOpen: Boolean
        get() = record != null

    // RECORD_AUDIO is asked for before a Microphone track arms; without it AudioRecord won't initialise, which is checked.
    @SuppressLint("MissingPermission")
    override fun open(): Boolean {
        if (record != null) {
            return true
        }
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
        try {
            built.startRecording()
        } catch (exception: IllegalStateException) {
            built.release()
            return false
        }
        if (built.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            built.release()
            return false
        }
        // Anything left in the ring is from before: the frames count from this opening.
        val scrap = FloatArray(READ_FRAMES)
        while (ring.read(scrap) > 0) Unit
        framesDrained = 0
        loggedLatency = false
        record = built
        running = true
        reader = Thread({ readLoop(built) }, "RecorderMic").apply { start() }
        return true
    }

    override fun close() {
        val built = record ?: return
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

    private fun readLoop(built: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val shorts = ShortArray(READ_FRAMES)
        val floats = FloatArray(READ_FRAMES)
        while (running) {
            val count = built.read(shorts, 0, READ_FRAMES)
            if (count <= 0) {
                if (count < 0) {
                    Log.e(TAG, "mic read failed: $count")
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
