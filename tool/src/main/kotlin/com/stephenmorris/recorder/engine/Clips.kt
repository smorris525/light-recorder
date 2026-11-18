package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.ClipStore
import com.stephenmorris.recorder.tape.Song
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The recordings in memory, at the output's [sampleRate]: the store both renderers read (playback's and the one
 * measuring the mix), where each recording is in it, and its samples. A recording's samples are one array, shared by
 * the store and [samples], never copied. Main thread only.
 */
class Clips(private val store: SongStore, val sampleRate: Double) {
    val clipStore = ClipStore()
    private val clipIndexes = mutableMapOf<UUID, Int>()
    private val clipSamples = mutableMapOf<UUID, FloatArray>()

    val indexes: Map<UUID, Int>
        get() = clipIndexes

    val allSamples: Map<UUID, FloatArray>
        get() = clipSamples

    fun index(clip: UUID): Int? = clipIndexes[clip]

    fun samples(clip: UUID): FloatArray? = clipSamples[clip]

    /** A new recording: playable at once, and saved. `null` when the store is full, which saves nothing. */
    fun add(samples: FloatArray): UUID? {
        val index = clipStore.add(samples) ?: return null
        val id = UUID.randomUUID()
        clipIndexes[id] = index
        clipSamples[id] = samples
        store.saveClip(id, samples, sampleRate)
        return id
    }

    /**
     * Lets the recordings go as a song closes, except those [keeping] plays (the song opening next, a duplicate say),
     * which would only be read back from disk; the next song's others are read as it opens.
     */
    fun release(keeping: Song? = null) {
        val kept = keeping?.let(::uses).orEmpty()
        for (clip in clipIndexes.keys - kept) {
            clipIndexes.remove(clip)?.let(clipStore::remove)
            clipSamples.remove(clip)
        }
    }

    /** Reads [song]'s recordings that aren't in memory yet, as it opens; missing ones are skipped. */
    fun load(song: Song) {
        for (clip in missing(song)) {
            store.loadClip(clip, sampleRate)?.let { keep(clip, it) }
        }
    }

    /**
     * Reads [song]'s recordings that aren't in memory yet on [io], then keeps them on the caller's thread, so a song
     * with long recordings opens without holding up the screen; [load] then finds them in memory.
     */
    suspend fun preload(song: Song, io: CoroutineDispatcher) {
        val wanted = missing(song)
        if (wanted.isEmpty()) {
            return
        }
        val read = withContext(io) { wanted.mapNotNull { clip -> store.loadClip(clip, sampleRate)?.let { clip to it } } }
        for ((clip, samples) in read) {
            if (clip !in clipIndexes) {
                keep(clip, samples)
            }
        }
    }

    private fun missing(song: Song): Set<UUID> = uses(song) - clipIndexes.keys

    private fun uses(song: Song): Set<UUID> = song.tracks.flatMap { track -> track.spans.mapNotNull { it.clip } }.toSet()

    private fun keep(clip: UUID, samples: FloatArray) {
        val index = clipStore.add(samples) ?: return
        clipIndexes[clip] = index
        clipSamples[clip] = samples
    }
}
