package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.ClipStore
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.Span
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ClipsTest {
    private val store = SongStore(createTempDirectory("songs").toFile()) { it.run() }
    private val rate = 48_000.0

    private fun songUsing(vararg clips: UUID): Song {
        val song = Song.new("s")
        val tracks = song.tracks.toMutableList()
        tracks[1] = tracks[1].copy(spans = clips.mapIndexed { index, clip -> Span(index.toDouble(), index + 1.0, 0.0, clip) })
        return song.copy(tracks = tracks)
    }

    @Test
    fun aNewRecordingIsPlayableAndSaved() {
        val clips = Clips(store, rate)
        val samples = floatArrayOf(0.1f, 0.2f)

        val id = assertNotNull(clips.add(samples))

        assertSame(samples, clips.samples(id))
        assertEquals(0, clips.index(id))
        assertContentEquals(samples, store.loadClip(id, rate))
    }

    @Test
    fun loadingASongReadsOnlyWhatIsMissing() {
        val clip = UUID.randomUUID()
        store.saveClip(clip, floatArrayOf(0.5f), rate)
        val clips = Clips(store, rate)

        clips.load(songUsing(clip))
        val first = clips.samples(clip)
        clips.load(songUsing(clip))

        assertSame(first, clips.samples(clip))
        assertEquals(0, clips.index(clip))
        assertEquals(1, clips.add(floatArrayOf(1f))?.let { clips.index(it) })
    }

    @Test
    fun aMissingFileIsSkipped() {
        val missing = UUID.randomUUID()
        val present = UUID.randomUUID()
        store.saveClip(present, floatArrayOf(0.5f), rate)
        val clips = Clips(store, rate)

        clips.load(songUsing(missing, present))

        assertNull(clips.index(missing))
        assertNotNull(clips.index(present))
        assertEquals(setOf(present), clips.indexes.keys)
    }

    @Test
    fun aFullStoreTakesNothingAndSavesNothing() {
        val clips = Clips(store, rate)
        repeat(ClipStore.CAPACITY) { assertNotNull(clips.add(FloatArray(1))) }

        assertNull(clips.add(floatArrayOf(1f)))
        assertEquals(ClipStore.CAPACITY, clips.allSamples.size)
    }

    @Test
    fun preloadReadsOnTheGivenDispatcher() = runBlocking {
        val clip = UUID.randomUUID()
        store.saveClip(clip, floatArrayOf(0.5f, 0.25f), rate)
        val clips = Clips(store, rate)
        val io = ManualDispatcher()

        val job = launch { clips.preload(songUsing(clip), io) }
        yield()
        assertNull(clips.samples(clip))

        io.runAll()
        job.join()
        assertContentEquals(floatArrayOf(0.5f, 0.25f), clips.samples(clip))
        assertEquals(0, clips.index(clip))
    }

    @Test
    fun loadAfterPreloadReadsNothing() = runBlocking {
        val clip = UUID.randomUUID()
        store.saveClip(clip, floatArrayOf(0.5f), rate)
        val clips = Clips(store, rate)
        clips.preload(songUsing(clip), kotlinx.coroutines.Dispatchers.Unconfined)
        val preloaded = clips.samples(clip)
        // With the file gone, a read would find nothing; the preloaded samples stay.
        store.removeUnused(emptyList())

        clips.load(songUsing(clip))

        assertSame(preloaded, clips.samples(clip))
    }
}
