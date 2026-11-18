package com.stephenmorris.recorder.platform

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.stephenmorris.recorder.engine.MixdownJob
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Writes the export (spec §13): the mixdown as 320 kbps stereo AAC in an `.m4a`, through `MediaCodec` and `MediaMuxer`. */
object M4aEncoder {
    private const val BIT_RATE = 320_000
    private const val CHUNK_FRAMES = 32_768
    private const val TIMEOUT_MICROS = 10_000L

    /**
     * Mixes [job] a chunk at a time and encodes it into [target], replacing it. [progress] is told the whole export's
     * share, from [MixdownJob.encodeStart] to 1. Returns `false`, with no file left, if it's cancelled or fails.
     */
    fun write(job: MixdownJob, target: File, isCancelled: () -> Boolean, progress: (Double) -> Unit): Boolean {
        target.parentFile?.mkdirs()
        target.delete()
        val rate = job.sampleRate.roundToInt()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_FRAMES * 2 * Short.SIZE_BYTES)
        }
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val finished = try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mixdown = job.mixdown
            val left = FloatArray(CHUNK_FRAMES)
            val right = FloatArray(CHUNK_FRAMES)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var framesQueued = 0L
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (isCancelled()) {
                    break
                }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_MICROS)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.clear()
                        val room = minOf(CHUNK_FRAMES, buffer.capacity() / (2 * Short.SIZE_BYTES))
                        val count = mixdown.render(left, right, room)
                        val time = framesQueued * 1_000_000L / rate
                        if (count == 0) {
                            codec.queueInputBuffer(index, 0, 0, time, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (frame in 0 until count) {
                                buffer.putShort(toShort(left[frame]))
                                buffer.putShort(toShort(right[frame]))
                            }
                            codec.queueInputBuffer(index, 0, count * 2 * Short.SIZE_BYTES, time, 0)
                            framesQueued += count
                            val share = mixdown.position.toDouble() / maxOf(1, mixdown.frameCount)
                            progress(job.encodeStart + (1 - job.encodeStart) * share)
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_MICROS)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        val buffer = codec.getOutputBuffer(index)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && muxerStarted) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            if (outputDone && muxerStarted) {
                // Writes the file's index; if that fails (the disk full, say), the export failed.
                muxerStarted = false
                muxer.stop()
            }
            outputDone
        } catch (exception: Exception) {
            Log.e(StereoTrackOutput.TAG, "export failed", exception)
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
        if (!finished) {
            target.delete()
        }
        return finished
    }

    private fun toShort(sample: Float): Short = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
}
