package com.stephenmorris.recorder.platform

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.stephenmorris.recorder.engine.AudioInput
import com.stephenmorris.recorder.engine.InputMeter
import com.stephenmorris.recorder.engine.InputShapes
import com.stephenmorris.recorder.engine.InterfaceRoute
import com.stephenmorris.recorder.engine.RoutedDevice
import com.stephenmorris.recorder.engine.Stamp
import com.stephenmorris.recorder.engine.deinterleave
import com.stephenmorris.recorder.tape.CaptureRing
import com.stephenmorris.recorder.tape.recordedChannel

/**
 * The mic (spec §4.2): a plain [AudioRecord] at the output's rate, 16-bit, read on its own thread at audio priority
 * into [meter] and a 30 s lock-free ring that the transport drains. Android's default input: the phone's mic, or a
 * plugged-in headset's, or an interface's. An interface with more than one input is opened with all of them (as
 * [InputShapes] says) and only [channel] is kept, so what's metered and drained is always mono. Opening fails, returning
 * `false`, without `RECORD_AUDIO`. While open, it tells [route] which device it records from and with how many inputs.
 */
class MicInput(private val sampleRate: Int, private val route: InterfaceRoute) : AudioInput {
    override val meter = InputMeter()
    private val ring = CaptureRing(sampleRate * RING_SECONDS)
    private val timestamp = AudioTimestamp()
    private var record: AudioRecord? = null
    private var reader: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    override var channel = 1

    /** How many inputs the open recorder reads: 1 unless it's an interface with more. */
    private var channels = 1
    private val shapes = InputShapes()

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

    override fun open(): Boolean {
        if (record != null && !failed) {
            return true
        }
        close()
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) {
            return false
        }
        // Mono first, as everywhere: only once it's routed is there a device to ask how many inputs it has.
        var built = started(mono(minimum)) ?: return false
        var count = 1
        val device = built.routedDevice
        val wanted = device?.let { inputsOf(it) } ?: 1
        if (wanted > 1 && device != null) {
            discard(built)
            var all = started(multi(minimum, wanted, device))
            // Started, but on another device: the interface's inputs aren't to be had together.
            if (all != null && all.routedDevice?.id != device.id) {
                discard(all)
                all = null
            }
            if (all != null) {
                built = all
                count = wanted
            } else {
                Log.w(TAG, "${device.productName}'s $wanted inputs wouldn't open together: mono")
                shapes.refuse(device.id)
                built = started(mono(minimum)) ?: return false
            }
        }
        // Anything left in the ring is from before: the frames count from this opening.
        val scrap = FloatArray(READ_FRAMES)
        while (ring.read(scrap) > 0) Unit
        framesDrained = 0
        loggedLatency = false
        channels = count
        record = built
        failed = false
        running = true
        val deviceId = built.routedDevice?.id
        reader = Thread({ readLoop(built, count, deviceId) }, "RecorderMic").apply { start() }
        Log.i(TAG, "input: $count channel(s) from ${built.routedDevice?.productName}")
        reportRoute(built)
        return true
    }

    private fun inputsOf(device: AudioDeviceInfo): Int = shapes.inputsOf(routeKind(device.type), device.id, device.channelCounts)

    // RECORD_AUDIO is asked for before a Mic/Line track arms; without it AudioRecord won't initialise, as built() checks.
    @SuppressLint("MissingPermission")
    private fun mono(minimum: Int): AudioRecord? = built {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, READ_FRAMES * 4 * Short.SIZE_BYTES),
        )
    }

    // As mono().
    @SuppressLint("MissingPermission")
    private fun multi(minimum: Int, count: Int, device: AudioDeviceInfo): AudioRecord? = built {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelIndexMask((1 shl count) - 1)
            .build()
        AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimum * count, READ_FRAMES * 4 * count * Short.SIZE_BYTES))
            .build()
            .also { it.preferredDevice = device }
    }

    /** The recorder [make] builds, or `null` if it throws or won't initialise (no permission, or a shape refused). */
    private fun built(make: () -> AudioRecord): AudioRecord? {
        val built = try {
            make()
        } catch (exception: Exception) {
            Log.w(TAG, "mic wouldn't open: $exception")
            return null
        }
        if (built.state != AudioRecord.STATE_INITIALIZED) {
            built.release()
            Log.w(TAG, "mic wouldn't initialise (no permission?)")
            return null
        }
        return built
    }

    /** [built], recording, with its route listened to; `null` (and released) if it won't start. */
    private fun started(built: AudioRecord?): AudioRecord? {
        built ?: return null
        // Listening before it starts, so no change of route is missed between starting and the first read.
        built.addOnRoutingChangedListener(routing, null)
        try {
            built.startRecording()
        } catch (exception: IllegalStateException) {
            discard(built)
            return null
        }
        if (built.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            discard(built)
            return null
        }
        return built
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
        if (built.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            built.stop()
        }
        built.release()
    }

    private fun reportRoute(router: AudioRouting) {
        val device = router.routedDevice
        Log.i(TAG, "input routed to ${device?.productName} (type ${device?.type})")
        route.inputRouted(routeKind(device?.type), channels)
        // Opened for another device's inputs (an interface pulled out, or plugged in while mono): it reads as closed,
        // so the transport opens it again in the right shape.
        val routed = device?.let { RoutedDevice(routeKind(it.type), it.id, it.channelCounts) }
        if (shapes.needsReopen(channels, routed)) {
            Log.i(TAG, "input reopens: $channels channel(s) open on ${device?.productName}")
            failed = true
        }
    }

    private fun readLoop(built: AudioRecord, count: Int, deviceId: Int?) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val shorts = ShortArray(READ_FRAMES * count)
        val floats = FloatArray(READ_FRAMES)
        var delivered = 0L
        while (running) {
            val read = built.read(shorts, 0, shorts.size)
            if (read <= 0) {
                if (read < 0) {
                    Log.e(TAG, "mic read failed: $read")
                    // An interface's inputs that started together but never delivered aren't tried together again.
                    if (count > 1 && delivered == 0L && deviceId != null) {
                        shapes.refuse(deviceId)
                    }
                    failed = true
                }
                break
            }
            val frames = read / count
            deinterleave(shorts, frames, count, recordedChannel(channel, count), floats)
            delivered += frames
            meter.record(floats, frames)
            ring.write(floats, frames)
        }
    }

    private companion object {
        const val TAG = StereoTrackOutput.TAG
        const val READ_FRAMES = 480
        const val RING_SECONDS = 30
    }
}
