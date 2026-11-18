package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.tape.MixEvent
import com.stephenmorris.recorder.tape.MixRenderer
import com.stephenmorris.recorder.tape.PassBounds
import com.stephenmorris.recorder.tape.Punch
import com.stephenmorris.recorder.tape.ScheduledEvent
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeScheduler
import com.stephenmorris.recorder.tape.gains
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays the tape and records takes (spec §4, §5). The tick loop calls [tick] every 20 ms,
 * which keeps events placed [LOOKAHEAD] ahead of the render clock and reads the clock back for the playhead.
 *
 * Audio runs only while a song is open and the tool is in front. The mic opens only while a Microphone track is armed
 * or a take is under way. Track *i* plays its recordings on mix slot *i* and its hits on drum bus *i*, at its volume and
 * pan; pads, clicks and auditions play on the unity bus. Main thread only.
 */
class Transport(
    private val model: SongModel,
    private val output: AudioOutput,
    private val input: AudioInput,
    private val clips: Clips,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val _isPlaying = MutableStateFlow(false)
    private val _isClipping = MutableStateFlow(false)
    private val _isMicRefused = MutableStateFlow(false)

    /** The tape, or a pattern on its own, is playing (or counting down to a take). Keep the screen on meanwhile. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** The input clipped within the last 2 s. */
    val isClipping: StateFlow<Boolean> = _isClipping.asStateFlow()

    /** The last Microphone track armed couldn't open the mic: its Input reads "microphone not allowed". */
    val isMicRefused: StateFlow<Boolean> = _isMicRefused.asStateFlow()

    val sampleRate = output.sampleRate.toDouble()
    private val mix = output.mix

    private var scheduler: TapeScheduler? = null

    /** Each pass the tape has started: from where on it, and when. */
    private val passes = mutableListOf<Pair<Double, Long>>()

    /** Where the playhead goes back to when the tape stops: where the last take of this playing began. */
    private var returnTime: Double? = null
    private val sentTracks = arrayOfNulls<MixEvent>(Song.TRACK_COUNT)
    private val sentDrums = arrayOfNulls<MixEvent>(Song.TRACK_COUNT)
    private val inputMeter = PeakMeter()
    private val trackMeters = List(Song.TRACK_COUNT) { PeakMeter() }
    private var lastClipNanos: Long? = null

    /** Left: audio stops, and stays stopped until [resume]. */
    private var isPaused = false

    /** The Microphone track whose mic wouldn't open, so it isn't tried again on every tick. */
    private var refusedTrack: UUID? = null

    init {
        model.onOpen += { song ->
            clips.load(song)
            prepare()
        }
    }

    private fun frames(seconds: Double): Long = (seconds * sampleRate).roundToLong()

    /** The frame being heard now. */
    private val heardFrame: Long
        get() = mix.renderedFrames - output.latencyFrames

    // Starting audio

    /** Starts audio if it should be running. */
    fun prepare() = updateAudio()

    /** Brings the output and the mic into line with what's wanted. Runs on every tick, so it's cheap when nothing changes. */
    private fun updateAudio() {
        val wantsOutput = !isPaused && model.isSongOpen
        if (wantsOutput && !output.isRunning) {
            output.start()
        } else if (!wantsOutput && output.isRunning) {
            stop()
            output.suspend()
        }
        val armed = model.arming.value.audio
        if (armed == null) {
            refusedTrack = null
        }
        val wantsInput = wantsOutput && (isTakeUnderWay || (armed != null && armed != refusedTrack))
        if (wantsInput && !input.isOpen) {
            if (!input.open()) {
                refusedTrack = armed
            }
        } else if (!wantsInput && input.isOpen && !isTakeUnderWay) {
            input.close()
        }
    }

    /** Leaving the tool (spec §4.4): stops playback, ends a take keeping what it has, and closes the output and mic. */
    fun pause() {
        stop()
        isPaused = true
        output.suspend()
        input.close()
    }

    /** Back in front: audio may start again. */
    fun resume() {
        isPaused = false
        updateAudio()
    }

    /**
     * Arms a track to record, or disarms it; not during a take. Arming a Microphone track opens the mic; if it won't
     * open (the permission refused), the track doesn't stay armed.
     */
    fun toggleArm(id: UUID) {
        val track = model.track(id) ?: return
        if (isTakeUnderWay) {
            return
        }
        model.arming.value = model.arming.value.toggling(track)
        if (model.arming.value.audio != id) {
            return
        }
        refusedTrack = null
        val opened = input.isOpen || input.open()
        _isMicRefused.value = !opened
        if (!opened) {
            refusedTrack = id
            model.arming.value = model.arming.value.removing(id)
        }
    }

    // Playing

    /** Play or ■. ■ stops at once, ending any take under way. */
    fun togglePlay() {
        model.isScrubbingPlayhead = false
        if (_isPlaying.value) {
            stop()
            return
        }
        model.isFollowing = true
        prepare()
        playTape(Punch.playStart(model.song, model.playhead.value))
    }

    /**
     * Playing the tape with no take under way, plays on from the playhead instead: a tap or scrub while playing. At the
     * end it plays from 0. Moving on, it doesn't return to the last take.
     */
    fun seek() {
        if (!_isPlaying.value || isTakeUnderWay) {
            return
        }
        returnTime = null
        stop()
        prepare()
        val playhead = model.playhead.value
        playTape(if (playhead >= model.song.length) 0.0 else playhead)
    }

    private fun playTape(time: Double) {
        model.playhead.value = time
        beginPlaying(time, mix.renderedFrames + frames(START_DELAY))
    }

    private fun beginPlaying(time: Double, frame: Long) {
        scheduler = TapeScheduler(sampleRate, time, frame)
        passes.clear()
        _isPlaying.value = true
    }

    /** Stops playback where it is, without touching a take, for a take that starts it again. */
    private fun haltPlayback() {
        mix.send(MixEvent.StopClips)
        mix.stopVoices()
        scheduler = null
    }

    fun stop() {
        model.isScrubbingPlayhead = false
        mix.send(MixEvent.StopClips)
        mix.stopVoices()
        scheduler = null
        passes.clear()
        _isPlaying.value = false
        returnTime?.let { time ->
            model.playhead.value = time
            model.updateSong { it.copy(view = it.view.following(time, it.length)) }
        }
        returnTime = null
    }

    // The loop

    fun tick() {
        updateAudio()
        if (!model.isSongOpen) {
            return
        }
        syncTracks()
        val now = mix.renderedFrames
        val heard = now - output.latencyFrames
        val horizon = now + frames(LOOKAHEAD)

        if (_isPlaying.value && scheduler != null) {
            scheduleTape(now, horizon)
            val finish = scheduler?.finishFrame
            if (finish != null && heard >= finish) {
                stop()
            } else if (!isTakeUnderWay) {
                val pass = passes.lastOrNull { it.second <= heard }
                val time = tapeTime(heard)
                if (pass != null && time != null && Punch.hasRunPastEnd(model.song, pass.first, time)) {
                    // The scheduler ignores a stop it has already passed, as when the song gets shorter while playing.
                    stop()
                }
            }
        }
        updatePlayhead(heard)
        if (input.meter.takeClipped()) {
            lastClipNanos = nanoTime()
        }
        _isClipping.value = lastClipNanos?.let { nanoTime() - it < CLIP_HOLD_NANOS } ?: false
    }

    /** The tape's events up to [horizon]. */
    private fun scheduleTape(now: Long, horizon: Long) {
        val scheduler = scheduler ?: return
        val song = model.song
        handle(scheduler.advance(horizon, now, song, passBounds(song), silence()))
    }

    /** The stretch of tape each take is recording over, under its track. */
    private fun silence(): Map<UUID, OpenEndRange<Double>> = emptyMap()

    /** An audio or pattern take is under way. */
    val isTakeUnderWay: Boolean
        get() = false

    /** With no take, playback stops at the song's end. */
    private fun passBounds(song: Song): PassBounds = PassBounds(listOf(song.length))

    /** Where on the tape [frame] falls, in the pass it's in. */
    private fun tapeTime(frame: Long): Double? {
        val pass = passes.lastOrNull { it.second <= frame } ?: return null
        return pass.first + (frame - pass.second) / sampleRate
    }

    private fun handle(events: List<ScheduledEvent>) {
        for (event in events) {
            when (event) {
                is ScheduledEvent.Drum -> {
                    // A track's hits on its own bus, at its volume and pan; an audition's at unity.
                    val bus = event.track?.let(::index) ?: MixRenderer.UNITY_BUS
                    mix.trigger(bus, RecorderKit.voice(event.sound), event.frame, event.level.gain)
                }
                is ScheduledEvent.Clip -> clips.index(event.clip)?.let { index ->
                    mix.send(
                        MixEvent.PlayClip(
                            slot = slot(event.track),
                            clip = index,
                            sourceFrame = (event.sourceTime * sampleRate).roundToInt(),
                            startFrame = event.startFrame,
                            frameCount = (event.endFrame - event.startFrame).toInt(),
                        ),
                    )
                }
                is ScheduledEvent.Click -> mix.trigger(MixRenderer.UNITY_BUS, RecorderKit.clickVoice(event.kind), event.frame, 1f)
                is ScheduledEvent.Pass -> passes += event.time to event.frame
            }
        }
    }

    private fun updatePlayhead(heard: Long) {
        if (!_isPlaying.value || scheduler == null) {
            return
        }
        val pass = passes.lastOrNull { it.second <= heard } ?: return
        passes.removeAll { it.second < pass.second }
        // A finger scrubbing the playhead holds it until it lifts, and playback seeks there.
        if (model.isScrubbingPlayhead) {
            return
        }
        model.playhead.value = pass.first + (heard - pass.second) / sampleRate
        model.follow()
    }

    // Tracks in the mix

    /** Where the track with [id] is in the song, which is its mix slot and its drum bus. */
    private fun index(id: UUID): Int? = model.song.tracks.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    private fun slot(track: UUID): Int = index(track) ?: 0

    /** Every track's vol and pan, on its slot and on its drum bus, sent when they change. */
    private fun syncTracks() {
        model.song.tracks.forEachIndexed { index, track ->
            val gains = track.gains
            val event = MixEvent.SetTrack(index, gains.left, gains.right)
            if (sentTracks[index] != event) {
                sentTracks[index] = event
                mix.send(event)
            }
            val drums = MixEvent.SetDrums(index, gains.left, gains.right)
            if (sentDrums[index] != drums) {
                sentDrums[index] = drums
                mix.send(drums)
            }
        }
    }

    // Meters

    /** The mic's level now, 0…1 on a decibel scale down to −60 dB. Read once a frame, by the input meter. */
    fun inputLevel(): Double = inputMeter.level(input.meter.takePeak(), nanoTime())

    /** What the track with [track] is playing now, on the input meter's scale. Read once a frame. */
    fun playbackLevel(track: UUID): Double {
        val index = index(track) ?: return 0.0
        return trackMeters[index].level(mix.takeTrackPeak(index), nanoTime())
    }

    /** The passes heard of so far, as (time, frame), for tests. */
    internal fun passesForTest(): List<Pair<Double, Long>> = passes.toList()

    companion object {
        /** How far ahead of the render clock events are placed, in seconds. */
        const val LOOKAHEAD = 0.12

        /** Before the first event, so that it isn't squeezed into a render that's already under way. */
        const val START_DELAY = 0.05

        private const val CLIP_HOLD_NANOS = 2_000_000_000L
    }
}
