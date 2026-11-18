package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.audio.Voice
import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.Bounce
import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.Mixdown
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.Track
import com.stephenmorris.recorder.tape.applyingBounce
import com.stephenmorris.recorder.tape.canBounce
import com.stephenmorris.recorder.tape.gains
import com.stephenmorris.recorder.tape.volumeGain
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The song as it's heard, ready to encode: [encodeStart] is where encoding's share of the progress begins. */
data class MixdownJob(val mixdown: Mixdown, val sampleRate: Double, val encodeStart: Double)

/**
 * Measures the open song's mix for the lane, bounces, and mixes down for Export.
 * The heavy work runs on [background]; everything else on the main thread.
 *
 * Each track with hits keeps its stem (its audio before volume and pan) while what it's made from is unchanged, so a
 * fader move only sums again. A result is dropped if the song it was made from has closed or changed meanwhile.
 */
class MixMeasurer(
    private val model: SongModel,
    private val transport: Transport,
    private val clips: Clips,
    private val store: SongStore,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _mixLevels = MutableStateFlow<MixLevels?>(null)

    /** The open song's mix, measured for the lane; `null` until it first has been. */
    val mixLevels: StateFlow<MixLevels?> = _mixLevels.asStateFlow()

    private val rate = transport.sampleRate

    /** What [mixLevels] was measured from. */
    private var measuredKey: MixLevels.Key? = null

    /** Each track with hits: its stem, and what it was rendered from. */
    private val stems = mutableMapOf<UUID, Pair<MixLevels.StemKey, FloatArray>>()

    /** The kit the measuring renderer plays, built on first need, off the main thread. */
    @Volatile
    private var voices: List<Voice>? = null

    /** Stems rendered so far, for tests. */
    internal var stemsRendered = 0
        private set

    init {
        model.onClose += { forget() }
    }

    /** Forgets the measured mix and its stems, when the song closes, so the next song's lane doesn't show this one's. */
    fun forget() {
        _mixLevels.value = null
        measuredKey = null
        stems.clear()
        transport.dropLive()
    }

    /**
     * Measures [song]'s mix for the lane. Opening, the waveform saved last time shows at once while the stems render.
     * Stale stems are rendered after a 100 ms pause for things to stop changing, except when nothing's on screen yet;
     * then every track is summed at its volume and pan. Does nothing when this mix has been measured already. Cancelling
     * the caller stops the work, keeping any stems already finished.
     */
    suspend fun measure(song: Song) {
        val key = MixLevels.Key(song)
        if (measuredKey == key) {
            return
        }
        val isFirst = _mixLevels.value == null
        if (isFirst) {
            store.loadMix(song.id)?.takeIf { it.sampleRate == rate }?.let { _mixLevels.value = it }
        }
        // A track that's gone, or no longer has hits, mustn't be summed from its old stem.
        val current = song.tracks.filter(MixLevels::needsStem).map { it.id }.toSet()
        stems.keys.retainAll(current)
        val stale = song.tracks.filter { MixLevels.needsStem(it) && stems[it.id]?.first != stemKey(it, song) }
        if (stale.isNotEmpty() && !isFirst) {
            delay(STALE_PAUSE_MILLIS)
        }
        val kept = stems.mapValues { it.value.second }
        // Taken here, on the main thread: Clips changes as takes land and songs open.
        val clipSamples = clips.allSamples.toMap()
        val indexes = clips.indexes.toMap()
        val job = currentCoroutineContext()[Job]
        val (levels, rendered) = withContext(NonCancellable + background) {
            val rendered = mutableMapOf<UUID, FloatArray>()
            for (track in stale) {
                val stem = renderStem(track, song.length, indexes) { job?.isActive == false } ?: return@withContext null to rendered
                rendered[track.id] = stem
            }
            MixLevels.sum(song, kept + rendered, clipSamples, rate) { job?.isActive == false } to rendered
        }
        for ((id, stem) in rendered) {
            song.tracks.firstOrNull { it.id == id }?.let { stems[id] = stemKey(it, song) to stem }
        }
        if (levels == null || job?.isActive == false || model.openSongId.value != song.id) {
            return
        }
        _mixLevels.value = levels
        measuredKey = key
        // The finished takes are in the mix now, so they're drawn from there.
        transport.clearLive(song)
        store.saveMix(levels, song.id)
    }

    /**
     * Mixes [sources] (track numbers from 1) onto [destination], as spec §15 has it: each source at its volume (never
     * its pan), mono, as playback would play it, never limited, without the silence after its last sound. Refused during
     * a take; playback stops first. The song is left as it was, returning `false`, if there's nothing to hear, the bounce
     * can't be kept, or the song or its sources changed while it rendered.
     */
    suspend fun bounce(sources: Set<Int>, destination: Int): Boolean {
        if (transport.isTakeUnderWay || !model.isSongOpen || sources.isEmpty()) {
            return false
        }
        if (transport.isPlaying.value) {
            transport.stop()
        }
        val song = model.song
        val length = song.length
        val frameCount = (length * rate).toInt()
        val tracks = sources.sorted().mapNotNull { song.tracks.getOrNull(it - 1) }
        val kept = keptStems(tracks, song)
        val indexes = clips.indexes.toMap()
        val mix = withContext(background) {
            var mix = FloatArray(frameCount)
            for (track in tracks) {
                val stem = kept[track.id] ?: renderStem(track, length, indexes) { false } ?: FloatArray(0)
                mix = Bounce.accumulate(stem, track.volumeGain, mix)
            }
            mix
        }
        val now = model.song
        if (!model.isSongOpen || transport.isTakeUnderWay || now.id != song.id || now.length != length ||
            !sameSources(sources, now, song)
        ) {
            return false
        }
        val trimmed = Bounce.trimmedLength(mix)
        if (trimmed == 0) {
            return false
        }
        val samples = if (trimmed == mix.size) mix else mix.copyOf(trimmed)
        val clip = clips.add(samples) ?: return false
        // The last take may have been bounced away; the measured mix draws the bounce.
        transport.dropLive()
        model.updateSong { it.applyingBounce(sources, destination, clip, samples.size / rate) }
        transport.replayClips()
        return true
    }

    /**
     * The song as it's heard, for Export: every track with something on it at its volume and pan through the limiters,
     * from 0:00 to its last sound. [progress] is told, 0…1 and only on a new whole percent, as the whole export goes:
     * each track to render is one share and encoding 0.2 of one, which starts at [MixdownJob.encodeStart]. It may be
     * told on a background thread. `null` during a take, with nothing recorded, or once the caller is cancelled.
     */
    suspend fun mixdown(progress: (Double) -> Unit): MixdownJob? {
        val song = model.song
        if (transport.isTakeUnderWay || !model.isSongOpen || !song.canBounce) {
            return null
        }
        val tracks = song.tracks.filter { it.spans.isNotEmpty() || it.hits.isNotEmpty() }
        val kept = keptStems(tracks, song)
        val indexes = clips.indexes.toMap()
        val shares = tracks.count { kept[it.id] == null } + ENCODE_SHARE
        var lastPercent = -1
        val report = { fraction: Double ->
            val percent = (fraction * 100).toInt()
            if (percent > lastPercent) {
                lastPercent = percent
                progress(fraction)
            }
        }
        val job = currentCoroutineContext()[Job]
        var rendered = 0.0
        val parts = mutableListOf<Mixdown.Stem>()
        for (track in tracks) {
            val stem = kept[track.id] ?: run {
                val done = rendered
                withContext(background) {
                    renderStem(track, song.length, indexes, { fraction -> report((done + fraction) / shares) }) {
                        job?.isActive == false
                    }
                }?.also { rendered += 1 }
            } ?: return null
            parts += Mixdown.Stem(stem, track.gains)
        }
        val mixdown = Mixdown(parts, rate)
        if (job?.isActive == false || mixdown.frameCount == 0) {
            return null
        }
        return MixdownJob(mixdown, rate, rendered / shares)
    }

    private fun stemKey(track: Track, song: Song) = MixLevels.StemKey(track, song.length, rate)

    /** The stems kept for [tracks] that are still up to date. */
    private fun keptStems(tracks: List<Track>, song: Song): Map<UUID, FloatArray> =
        tracks.mapNotNull { track ->
            stems[track.id]?.takeIf { it.first == stemKey(track, song) }?.let { track.id to it.second }
        }.toMap()

    /** Background only. */
    private fun renderStem(
        track: Track,
        songLength: Double,
        indexes: Map<UUID, Int>,
        progress: (Double) -> Unit = {},
        isCancelled: () -> Boolean,
    ): FloatArray? {
        val kit = if (MixLevels.needsStem(track)) voices ?: RecorderKit.voices(rate).also { voices = it } else emptyList()
        stemsRendered += 1
        return MixLevels.renderStem(
            track, songLength, clips.clipStore, indexes, kit, rate, isCancelled, progress,
        )
    }

    /** Whether each of [sources] holds the same recordings and hits at the same volume in [song] as in [before]. */
    private fun sameSources(sources: Set<Int>, song: Song, before: Song): Boolean = sources.all { number ->
        val now = song.tracks.getOrNull(number - 1)
        val then = before.tracks.getOrNull(number - 1)
        if (now == null || then == null) {
            now == null && then == null
        } else {
            now.id == then.id && now.volume == then.volume && now.spans == then.spans && now.hits == then.hits
        }
    }

    private companion object {
        const val STALE_PAUSE_MILLIS = 100L

        /** How long mixing and encoding the export takes, as a share of rendering one track's stem. */
        const val ENCODE_SHARE = 0.2
    }
}
