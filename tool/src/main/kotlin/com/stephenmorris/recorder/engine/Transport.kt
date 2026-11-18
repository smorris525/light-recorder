package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.tape.MixEvent
import com.stephenmorris.recorder.tape.MixRenderer
import com.stephenmorris.recorder.tape.PassBounds
import com.stephenmorris.recorder.tape.PatternTake
import com.stephenmorris.recorder.tape.Punch
import com.stephenmorris.recorder.tape.ScheduledEvent
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.Span
import com.stephenmorris.recorder.tape.TakeLevels
import com.stephenmorris.recorder.tape.TapeScheduler
import com.stephenmorris.recorder.tape.HitLevels
import com.stephenmorris.recorder.tape.fadeEdges
import com.stephenmorris.recorder.tape.gains
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An audio recording under way, or waiting for its start. */
data class AudioTake(
    val track: UUID,
    /** Where on the tape recording begins. */
    val recordTime: Double,
    val recordFrame: Long,
    /** Where it stops by itself: the out point. */
    val outFrame: Long?,
    /** Where on the tape it ends, once it's been asked to. */
    val endTime: Double? = null,
    val seed: Double = Random.nextDouble(0.0, 10.0),
    val isRecording: Boolean = false,
    val isEnding: Boolean = false,
    /** How far it's drawn, in seconds on the tape. */
    val end: Double,
)

/** A take's real level, drawn on the lane from the moment it records until the mix is measured with it in. */
data class LiveTake(
    val track: UUID,
    val levels: TakeLevels,
    /** Its recording, once it's finished. */
    val clip: UUID? = null,
)

/**
 * A pattern take's hits, drawn on the lane from the moment they're scheduled until the mix is measured with them in.
 * Not a data class: [levels] changes in place, and each new value is a new drawing.
 */
class LiveHits(
    val track: UUID,
    val levels: HitLevels,
    /** Whether the take has ended and its hits are on its track. */
    val isWritten: Boolean = false,
)

/** What the screens show of a pattern take or audition, as it was when it last changed. */
data class PatternTakeState(
    val playing: UUID,
    val queued: UUID?,
    val records: Boolean,
    val track: UUID?,
    val endFrame: Long?,
    val hitCount: Int,
)

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
    private val _take = MutableStateFlow<AudioTake?>(null)
    private val _countNumber = MutableStateFlow<Int?>(null)
    private val _patternTake = MutableStateFlow<PatternTakeState?>(null)
    private val _liveTake = MutableStateFlow<LiveTake?>(null)
    private val _liveHits = MutableStateFlow<LiveHits?>(null)
    private val _isPadRecording = MutableStateFlow(false)

    /** The tape, or a pattern on its own, is playing (or counting down to a take). Keep the screen on meanwhile. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** The input clipped within the last 2 s. */
    val isClipping: StateFlow<Boolean> = _isClipping.asStateFlow()

    /** The last Microphone track armed couldn't open the mic: its Input reads "microphone not allowed". */
    val isMicRefused: StateFlow<Boolean> = _isMicRefused.asStateFlow()

    /** The audio take under way, or waiting for its lead-in to end. */
    val take: StateFlow<AudioTake?> = _take.asStateFlow()

    /** The count through a take's lead-in: 3, 2, 1. */
    val countNumber: StateFlow<Int?> = _countNumber.asStateFlow()

    /** The pattern take or audition under way, for the screens; updated when what they show changes, not every tick. */
    val patternTake: StateFlow<PatternTakeState?> = _patternTake.asStateFlow()

    /** The pattern take being recorded, or the last one until the mix is measured with it in: its hits' level. */
    val liveHits: StateFlow<LiveHits?> = _liveHits.asStateFlow()

    /** ● on Pads: overdubbing into the pattern looping alone. */
    val isPadRecording: StateFlow<Boolean> = _isPadRecording.asStateFlow()

    /** The take being recorded, or the last one until the mix is measured with it in: its real level, for the lane. */
    val liveTake: StateFlow<LiveTake?> = _liveTake.asStateFlow()

    val sampleRate = output.sampleRate.toDouble()
    private val mix = output.mix

    /** The kit's voices, for measuring a pattern take's hits as the mix plays them. Rendered once (spec §6). */
    private val kitVoices = RecorderKit.voices(sampleRate)

    private var scheduler: TapeScheduler? = null

    /** Each pass the tape has started: from where on it, and when. */
    private val passes = mutableListOf<Pair<Double, Long>>()

    /** The pattern take or audition as it runs. */
    private var live: PatternTake? = null

    /** Where the playhead goes back to when the tape stops: where the last take of this playing began. */
    private var returnTime: Double? = null

    /** Where the takes of this ● stop by themselves, on the tape: the out point after their start. */
    private var takeEnd: Double? = null

    /** The frame recording begins at, after a lead-in, while that lead-in is under way. */
    private var leadInEnd: Long? = null

    /** A ● whose takes haven't all landed yet. */
    private var takesPending = false

    /** The audio take's samples, as they're captured. */
    private var recorder: TakeRecorder? = null

    /** Render frame less input frame, for the audio take under way: fixed once per take, so its samples stay contiguous. */
    private var captureOffset: Long? = null
    private val scratch = FloatArray(16_384)

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
        val wantsInput = wantsOutput && (_take.value != null || (armed != null && armed != refusedTrack))
        if (wantsInput && !input.isOpen) {
            if (!input.open()) {
                refusedTrack = armed
            }
        } else if (!wantsInput && input.isOpen) {
            input.close()
        }
    }

    /** Leaving the tool (spec §4.4): stops playback, ends a take keeping what it has, and closes the output and mic. */
    fun pause() {
        stop()
        // The take has been asked to end: it lands now, with what's been captured, as the mic is about to close.
        drainCapture()
        if (_take.value != null) {
            finishTake()
        }
        settleTakes()
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
        prepare()
        val audition = model.auditionPattern
        if (audition != null) {
            startAudition(audition)
        } else {
            model.isFollowing = true
            playTape(Punch.playStart(model.song, model.playhead.value))
        }
    }

    /**
     * Playing the tape with no take under way, plays on from the playhead instead: a tap or scrub while playing. At the
     * end it plays from 0. Moving on, it doesn't return to the last take.
     */
    fun seek() {
        if (!_isPlaying.value || isTakeUnderWay || live != null) {
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
        val current = _take.value
        if (current?.isRecording == true) {
            endTake(heardFrame)
        } else if (current != null) {
            abandonTake()
        }
        keepHeardHits()
        scheduler = null
        passes.clear()
        _isPlaying.value = false
        _isPadRecording.value = false
        live = null
        takeEnd = null
        leadInEnd = null
        publishPatternTake()
        model.playingStep.value = null
        returnTime?.let { time ->
            model.playhead.value = time
            model.updateSong { it.copy(view = it.view.following(time, it.length)) }
        }
        returnTime = null
        _countNumber.value = null
    }

    // Recording

    /**
     * ●: records every armed track together, the Microphone track from the mic and the song's selected pattern onto the
     * Drums track. From the in point, or from the playhead when stopped, the tape plays a 3 s lead-in first while the
     * count shows 3-2-1; playing with no in point, the takes punch in at once. With the out point after the start,
     * recording stops there, and so does the tape. ● again ends the takes. When the tape stops, the playhead returns to
     * where recording began. Without the mic there's no audio take, which would only be silence over the track.
     */
    fun record() {
        if (isTakeUnderWay) {
            endTakes()
            return
        }
        val arming = model.arming.value
        if (arming.isEmpty) {
            return
        }
        if (!_isPlaying.value) {
            model.isFollowing = true
        }
        prepare()
        val audioTrack = arming.audio?.takeIf { input.isOpen }?.let(model::track)
        val drumTrack = arming.drums?.let(model::track)
        val pattern = model.song.currentPattern
        if (audioTrack == null && (drumTrack == null || pattern == null)) {
            return
        }
        // A new take returns to its own start, not an earlier one's.
        returnTime = null
        if (live != null) {
            stop()
        }
        prepare()
        val song = model.song
        // A pattern take starts past everything already scheduled, so none of the tape's hits it replaces have played.
        val aheadFrame = mix.renderedFrames + frames(LOOKAHEAD)
        val heard = heardFrame
        val heardTime = tapeTime(heard)
        val aheadTime = tapeTime(aheadFrame)
        val audioStart: Pair<Double, Long>
        val patternStart: Pair<Double, Long>
        val isPunchingIn: Boolean
        if (_isPlaying.value && Punch.recordStart(song, model.playhead.value, isPlaying = true) == null &&
            heardTime != null && aheadTime != null
        ) {
            audioStart = heardTime to heard
            patternStart = aheadTime to aheadFrame
            isPunchingIn = true
        } else {
            isPunchingIn = false
            // Stopped, or from the in point (or the tape not yet heard): the lead-in, then both takes at once.
            val start = Punch.recordStart(song, model.playhead.value, isPlaying = false) ?: 0.0
            val lead = Punch.leadIn(start)
            haltPlayback()
            val recordFrame = mix.renderedFrames + frames(START_DELAY + Punch.LEAD_IN)
            model.playhead.value = lead.tapeFrom
            beginPlaying(lead.tapeFrom, recordFrame - frames(start - lead.tapeFrom))
            leadInEnd = recordFrame
            audioStart = start to recordFrame
            patternStart = audioStart
        }
        takeEnd = Punch.recordEnd(song, audioStart.first)
        returnTime = audioStart.first

        if (audioTrack != null) {
            val outFrame = takeEnd?.let { audioStart.second + frames(it - audioStart.first) }
            val levels = TakeLevels(audioStart.first, sampleRate)
            _liveTake.value = LiveTake(audioTrack.id, levels)
            recorder = TakeRecorder(audioStart.second, outFrame ?: Long.MAX_VALUE, levels)
            captureOffset = null
            _take.value = AudioTake(
                track = audioTrack.id,
                recordTime = audioStart.first,
                recordFrame = audioStart.second,
                outFrame = outFrame,
                end = audioStart.first,
            )
            mix.send(MixEvent.SilenceTrack(slot(audioTrack.id), audioStart.second, outFrame ?: Long.MAX_VALUE))
        }
        if (drumTrack != null && pattern != null) {
            live = PatternTake(
                sampleRate = sampleRate,
                pattern = pattern.id,
                startTime = patternStart.first,
                startFrame = patternStart.second,
                outTime = takeEnd,
                track = drumTrack.id,
            )
            publishPatternTake()
        }
        takesPending = true
        val pending = live
        val track = pending?.track
        if (isPunchingIn && pending != null && track != null && hasRecordings(track, pending.startTime)) {
            // The scheduler trims a track's recordings under its take only as it schedules them, and the Drums track's
            // were scheduled before the pattern take: so again, now, without what it records over.
            playClipsAgain()
        }
    }

    /**
     * ● during the takes. Inside the lead-in it abandons them, and playback stops; after it, the audio take ends at once
     * and the pattern take after the hits already on their way to being heard, and playback goes on.
     */
    private fun endTakes() {
        val heard = heardFrame
        val leadIn = leadInEnd
        if (leadIn != null && heard < leadIn) {
            if (_take.value != null) {
                abandonTake()
            }
            live = null
            // Nothing was recorded, so nothing to return to.
            returnTime = null
            stop()
            return
        }
        endTake(heard)
        val current = live
        if (current?.records == true) {
            current.requestStop(heard)
            publishPatternTake()
        }
    }

    /** Ends the audio take at [frame]. One that hasn't begun recording is abandoned. */
    private fun endTake(frame: Long) {
        val current = _take.value ?: return
        if (current.isEnding) {
            return
        }
        if (frame <= current.recordFrame) {
            abandonTake()
            return
        }
        _take.value = current.copy(isEnding = true, endTime = tapeTime(frame))
        recorder?.end(frame)
        mix.send(MixEvent.SilenceTrack(slot(current.track), current.recordFrame, frame))
    }

    /** Drops the audio take with nothing kept, and lets its track be heard again. */
    private fun abandonTake() {
        val current = _take.value ?: return
        mix.send(MixEvent.SilenceTrack(slot(current.track), 0, 0))
        _take.value = null
        recorder = null
        _liveTake.value = null
    }

    /**
     * Puts the finished take on the tape, and plays it from here on if the tape's still going. Its track's recordings
     * after it, left out while it ran, play again too, even when nothing was kept of it.
     */
    private fun finishTake() {
        val finished = _take.value ?: return
        val samples = recorder?.samples() ?: FloatArray(0)
        _take.value = null
        recorder = null
        try {
            if (samples.isEmpty()) {
                _liveTake.value = null
                return
            }
            fadeEdges(samples, (FADE_SECONDS * sampleRate).toInt())
            val clip = clips.add(samples)
            if (clip == null) {
                _liveTake.value = null
                return
            }
            _liveTake.value = _liveTake.value?.copy(clip = clip)
            val duration = samples.size / sampleRate
            val span = Span(finished.recordTime, finished.recordTime + duration, finished.seed, clip)
            model.updateTrack(finished.track) { it.recording(span) }
        } finally {
            playClipsAgain()
        }
    }

    /**
     * While the tape plays, its recordings from here on, scheduled again as the song and the takes now stand: the
     * scheduler trims a track's recordings under a take only as it schedules them.
     */
    private fun playClipsAgain() {
        val scheduler = scheduler
        if (!_isPlaying.value || scheduler == null || !model.isSongOpen) {
            return
        }
        mix.send(MixEvent.StopClips)
        val song = model.song
        handle(scheduler.clipsAgain(song, mix.renderedFrames + frames(START_DELAY), passBounds(song), silence()))
    }

    /** Whether [track] has recordings still to play after [time]. */
    private fun hasRecordings(track: UUID, time: Double): Boolean =
        model.track(track)?.spans?.any { it.end > time } == true

    // Pattern takes

    /**
     * Selects one of the song's patterns; during a pattern take it plays once the current loop ends (or, in the lead-in,
     * the take starts with it).
     */
    fun select(pattern: UUID) {
        model.updateSong { it.copy(selectedPattern = pattern) }
        val current = live
        if (current != null && current.records && current.endFrame == null) {
            current.queue(pattern, heardFrame)
            publishPatternTake()
        }
    }

    /** Loops a pattern alone, without the tape, for its screens: on the unity bus, as it's no track's. */
    private fun startAudition(pattern: UUID) {
        live = PatternTake(
            sampleRate = sampleRate,
            pattern = pattern,
            startTime = 0.0,
            startFrame = mix.renderedFrames + frames(START_DELAY),
            outTime = null,
            track = null,
            records = false,
        )
        _isPlaying.value = true
        publishPatternTake()
    }

    /**
     * Writes a finished pattern take onto its track once its end has been heard, so the lane doesn't change early. If
     * the tape plays on, the track's recordings past the take, left out while it ran, play again.
     */
    private fun finishPatternTake(heard: Long) {
        val current = live ?: return
        val endFrame = current.endFrame ?: return
        val endTime = current.endTime ?: return
        val track = current.track ?: return
        if (!current.records || heard < endFrame) {
            return
        }
        if (endTime > current.startTime) {
            model.updateTrack(track) { it.recording(current.hits, current.startTime..<endTime) }
        }
        live = null
        publishPatternTake()
        if (hasRecordings(track, endTime)) {
            playClipsAgain()
        }
    }

    /** When playback stops in the middle of a pattern take, what was heard of it stays. */
    private fun keepHeardHits() {
        val current = live ?: return
        val track = current.track ?: return
        if (!current.records) {
            return
        }
        val heard = current.startTime + (heardFrame - current.startFrame) / sampleRate
        val end = minOf(current.endTime ?: Double.POSITIVE_INFINITY, heard)
        if (end <= current.startTime) {
            return
        }
        model.updateTrack(track) { it.recording(current.hits.filter { hit -> hit.time < end }, current.startTime..<end) }
    }

    private fun publishPatternTake() {
        val current = live
        val state = current?.let {
            PatternTakeState(it.playing, it.queued, it.records, it.track, it.endFrame, it.hits.size)
        }
        if (state != _patternTake.value) {
            _patternTake.value = state
            publishLiveHits()
        }
    }

    /**
     * The lane draws a pattern take's hits as they're played, up to [horizon] (the frame the take is scheduled to; by
     * default, as far as the next tick will schedule it). Once it ends, what landed on its track stays drawn until the
     * mix is measured with it in; what didn't (a take abandoned in its lead-in) goes.
     */
    private fun publishLiveHits(horizon: Long? = null) {
        val current = live
        val track = current?.track
        val shown = _liveHits.value
        if (current != null && current.records && track != null) {
            // From the take's level so far, so each tick plays only the moment since the last.
            val levels = shown?.takeIf { it.track == track && !it.isWritten }?.levels ?: HitLevels(kitVoices, sampleRate)
            val frame = horizon ?: (mix.renderedFrames + frames(LOOKAHEAD))
            levels.play(current.hits, current.startTime + (frame - current.startFrame) / sampleRate)
            _liveHits.value = LiveHits(track, levels)
        } else if (shown != null && !shown.isWritten) {
            val recorded = model.track(shown.track)?.hits.orEmpty().toHashSet()
            shown.levels.hits = shown.levels.hits.filter { it in recorded }
            _liveHits.value = if (shown.levels.hits.isEmpty()) null else LiveHits(shown.track, shown.levels, isWritten = true)
        }
    }

    /**
     * Once the mix has been measured from [measured], drops the live drawings it now holds: the finished audio take, and
     * a written pattern take whose last hit is on its track.
     */
    fun clearLive(measured: Song) {
        val take = _liveTake.value
        val clip = take?.clip
        if (clip != null && measured.tracks.any { track -> track.spans.any { it.clip == clip } }) {
            _liveTake.value = null
        }
        val hits = _liveHits.value
        val last = hits?.levels?.hits?.lastOrNull()
        if (hits != null && hits.isWritten && last != null &&
            measured.tracks.any { it.id == hits.track && last in it.hits }
        ) {
            _liveHits.value = null
        }
    }

    // Pads

    /**
     * ● on Pads: overdub on or off, into the pattern only. Unless the pattern is already looping alone, whatever's
     * playing stops and the pattern starts. During a pattern take it does nothing, so the take isn't cut short.
     */
    fun recordIntoPattern(pattern: UUID) {
        if (live?.records == true) {
            return
        }
        if (_isPadRecording.value) {
            _isPadRecording.value = false
            return
        }
        val isAuditioning = live?.let { !it.records && it.playing == pattern } == true
        if (!isAuditioning) {
            if (_isPlaying.value) {
                stop()
            }
            prepare()
            startAudition(pattern)
        }
        _isPadRecording.value = live?.records == false
    }

    /** A pad tap: sounds at once, on the unity bus, and, while overdubbing, lands on the pattern's nearest step. */
    fun hit(sound: Sound, level: Level) {
        val gain = if (level == Level.ACCENT) level.gain else PAD_GAIN
        mix.trigger(MixRenderer.UNITY_BUS, RecorderKit.voice(sound), mix.renderedFrames, gain)
        val current = live ?: return
        if (!_isPadRecording.value || current.records) {
            return
        }
        val step = current.nearestStep(heardFrame) ?: return
        model.updatePattern(current.playing) { pattern ->
            pattern.steps.recordingHit(sound, step, level)?.let { pattern.copy(steps = it) } ?: pattern
        }
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

        val audition = live
        if (audition != null && !audition.records && model.auditionPattern != audition.playing) {
            // The pattern's screens are gone.
            stop()
        }
        live?.let { current ->
            handle(current.advance(horizon, now, model.song.patterns, model.clicks))
            publishPatternTake()
            if (current.records) {
                publishLiveHits(horizon)
            }
        }
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
        updateCount(heard)
        updateTake(heard)
        finishPatternTake(heard)
        drainCapture()
        settleTakes()
        if (input.meter.takeClipped()) {
            lastClipNanos = nanoTime()
        }
        _isClipping.value = lastClipNanos?.let { nanoTime() - it < CLIP_HOLD_NANOS } ?: false
    }

    /** The tape's events up to [horizon]. A take silences what's on its track under it while it runs. */
    private fun scheduleTape(now: Long, horizon: Long) {
        val scheduler = scheduler ?: return
        val song = model.song
        handle(scheduler.advance(horizon, now, song, passBounds(song), silence()))
    }

    /**
     * The stretch of tape each take is recording over, under its track: from its start to where it ends, or on to the
     * end while that isn't known. The tape doesn't play what's there, which the take replaces.
     */
    private fun silence(): Map<UUID, OpenEndRange<Double>> {
        val silence = mutableMapOf<UUID, OpenEndRange<Double>>()
        val current = live
        val track = current?.track
        if (current != null && current.records && track != null) {
            val end = current.endTime ?: current.outTime ?: Double.POSITIVE_INFINITY
            silence[track] = current.startTime..<maxOf(current.startTime, end)
        }
        _take.value?.let { take ->
            val end = take.endTime ?: takeEnd ?: Double.POSITIVE_INFINITY
            silence[take.track] = take.recordTime..<maxOf(take.recordTime, end)
        }
        return silence
    }

    /** An audio or pattern take is under way. */
    val isTakeUnderWay: Boolean
        get() = _take.value != null || live?.records == true

    /** With no take, playback stops at the song's end; a take stops only where it ends by itself, if it does. */
    private fun passBounds(song: Song): PassBounds {
        if (isTakeUnderWay) {
            return PassBounds(listOfNotNull(takeEnd))
        }
        return PassBounds(listOf(song.length))
    }

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
        val step = live?.position(heard)?.let { (pattern, step) -> PatternStep(pattern, step) }
        if (model.playingStep.value != step) {
            model.playingStep.value = step
        }
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

    private fun updateCount(heard: Long) {
        val leadIn = leadInEnd
        _countNumber.value = if (leadIn != null && heard < leadIn) {
            val left = (leadIn - heard) / sampleRate
            minOf(Punch.LEAD_IN.toInt(), maxOf(1, ceil(left).toInt()))
        } else {
            null
        }
    }

    private fun updateTake(heard: Long) {
        var current = _take.value ?: return
        if (!current.isRecording && heard >= current.recordFrame) {
            current = current.copy(isRecording = true)
            _take.value = current
        }
        if (!current.isRecording || current.isEnding) {
            return
        }
        // Often enough to draw it growing, without redrawing the screen on every tick.
        if (model.playhead.value - current.end > 0.03) {
            _take.value = current.copy(end = model.playhead.value)
        }
    }

    /** Once every take of a ● has landed, or been dropped, forgets where they were to stop. */
    private fun settleTakes() {
        if (!takesPending || isTakeUnderWay) {
            return
        }
        takesPending = false
        takeEnd = null
        leadInEnd = null
    }

    /**
     * Moves what the mic has captured into the audio take, placed on the render clock by the timestamps; with no take,
     * it's let go. The take lands once the mic has passed its end.
     */
    private fun drainCapture() {
        if (!input.isOpen) {
            return
        }
        while (true) {
            val first = input.framesDrained
            val count = input.drain(scratch)
            if (count == 0) {
                break
            }
            recorder?.let { recorder ->
                val offset = captureOffset ?: alignmentOffset(first + count - 1).also { captureOffset = it }
                recorder.append(scratch, count, first + offset)
            }
            if (count < scratch.size) {
                break
            }
        }
        val current = _take.value
        if (current != null && current.isRecording && recorder?.isComplete == true) {
            finishTake()
        }
    }

    /**
     * Render frame less input frame, from the timestamps (spec §4.3), measured at [inputFrame]; until both clocks have
     * one, the last frame drained is taken as heard now.
     */
    private fun alignmentOffset(inputFrame: Long): Long {
        val inputStamp = input.inputStamp()
        val outputStamp = output.outputStamp()
        val offset = if (inputStamp != null && outputStamp != null) {
            CaptureAlignment.renderFrame(inputFrame, inputStamp, outputStamp, output.sampleRate) - inputFrame
        } else {
            heardFrame - inputFrame
        }
        println(
            "RecorderAudio: take aligned by ${if (inputStamp != null && outputStamp != null) "timestamps" else "the heard frame"}, " +
                "output latency ${output.latencyFrames} frames, offset $offset frames",
        )
        return offset
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

        /** A pad's normal hit; an accent plays at its own gain. */
        const val PAD_GAIN = 0.85f

        /** Each end of a take fades over this, so replacing part of a track never clicks. */
        const val FADE_SECONDS = 0.005

        private const val CLIP_HOLD_NANOS = 2_000_000_000L
    }
}
