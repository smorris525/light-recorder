package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.ClipStore
import com.stephenmorris.recorder.tape.Song
import java.util.UUID

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

    /** Reads [song]'s recordings that aren't in memory yet, as it opens; missing ones are skipped. */
    fun load(song: Song) {
        val wanted = song.tracks.flatMap { track -> track.spans.mapNotNull { it.clip } }.toSet() - clipIndexes.keys
        for (clip in wanted) {
            val samples = store.loadClip(clip, sampleRate) ?: continue
            val index = clipStore.add(samples) ?: continue
            clipIndexes[clip] = index
            clipSamples[clip] = samples
        }
    }
}
