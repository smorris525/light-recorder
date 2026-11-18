package com.stephenmorris.recorder.store

import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.Span
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongStoreTest {
    private val root = createTempDirectory("songs").toFile()
    private val store = SongStore(root) { it.run() }

    private fun songUsing(clip: UUID): Song {
        val song = Song.new("s")
        val tracks = song.tracks.toMutableList()
        tracks[1] = tracks[1].copy(spans = listOf(Span(0.0, 1.0, 0.0, clip)))
        return song.copy(tracks = tracks)
    }

    @Test
    fun songsComeBackAsSaved() {
        val songs = listOf(Song.new("a"), Song.new("b"))
        store.save(songs)
        assertEquals(songs, store.loadSongs())
    }

    @Test
    fun noFileIsNoSongs() {
        assertTrue(store.loadSongs().isEmpty())
    }

    @Test
    fun aDamagedSongsFileIsNoSongs() {
        File(root, "songs.json").writeText("{nope")
        assertTrue(store.loadSongs().isEmpty())
    }

    @Test
    fun aClipComesBackAtItsRate() {
        val id = UUID.randomUUID()
        store.saveClip(id, floatArrayOf(0.1f, 0.2f), 48_000.0)
        assertContentEquals(floatArrayOf(0.1f, 0.2f), store.loadClip(id, 48_000.0))
    }

    @Test
    fun aClipRecordedAtAnotherRateIsResampled() {
        val id = UUID.randomUUID()
        store.saveClip(id, FloatArray(44_100), 44_100.0)
        assertEquals(48_000, assertNotNull(store.loadClip(id, 48_000.0)).size)
    }

    @Test
    fun aMissingOrDamagedClipIsNothing() {
        assertNull(store.loadClip(UUID.randomUUID(), 48_000.0))
        val id = UUID.randomUUID()
        File(root, "Clips").mkdirs()
        File(root, "Clips/$id.clip").writeBytes(ByteArray(5))
        assertNull(store.loadClip(id, 48_000.0))
    }

    @Test
    fun aMixComesBackAsSaved() {
        val song = UUID.randomUUID()
        val levels = MixLevels(48_000.0, floatArrayOf(0.5f, 1.2f))
        store.saveMix(levels, song)
        assertEquals(levels, store.loadMix(song))
    }

    @Test
    fun aDamagedMixIsNothing() {
        val song = UUID.randomUUID()
        File(root, "Mix").mkdirs()
        File(root, "Mix/$song.mix").writeBytes(ByteArray(3))
        assertNull(store.loadMix(song))
    }

    @Test
    fun removeUnusedKeepsOnlyWhatSongsUse() {
        val kept = UUID.randomUUID()
        val unused = UUID.randomUUID()
        val song = songUsing(kept)
        val goneSong = UUID.randomUUID()
        store.saveClip(kept, floatArrayOf(1f), 48_000.0)
        store.saveClip(unused, floatArrayOf(1f), 48_000.0)
        store.saveMix(MixLevels(48_000.0, floatArrayOf(1f)), song.id)
        store.saveMix(MixLevels(48_000.0, floatArrayOf(1f)), goneSong)

        store.removeUnused(listOf(song))

        assertTrue(File(root, "Clips/$kept.clip").exists())
        assertFalse(File(root, "Clips/$unused.clip").exists())
        assertTrue(File(root, "Mix/${song.id}.mix").exists())
        assertFalse(File(root, "Mix/$goneSong.mix").exists())
    }

    @Test
    fun aFailedWriteLeavesTheOldFile() {
        val songs = listOf(Song.new("a"))
        store.save(songs)
        File(root, "songs.json.tmp").mkdirs()

        store.save(listOf(Song.new("b")))

        assertEquals(songs, store.loadSongs())
    }

    // Review: a song that can't be read keeps its recordings, and the file it was in is kept aside.
    @Test
    fun aSongThatFailsToDecodeKeepsItsRecordings() {
        val clip = UUID.randomUUID()
        store.saveClip(clip, floatArrayOf(1f), 48_000.0)
        val good = Song.new("good")
        val goodJson = com.stephenmorris.recorder.tape.SongCoding.encode(listOf(good)).removePrefix("[").removeSuffix("]")
        val text = """[$goodJson,
            {"id":"not a uuid","name":"lost","tracks":[{"id":"${UUID.randomUUID()}","number":2,
            "spans":[{"start":0.0,"end":1.0,"clip":"$clip"}]}]}]"""
        File(root, "songs.json").writeText(text)

        val loaded = store.loadSongs()
        store.removeUnused(loaded)

        assertEquals(listOf(good.id), loaded.map { it.id })
        assertTrue(File(root, "Clips/$clip.clip").exists())
        assertTrue(root.listFiles()!!.any { it.name.startsWith("songs.json.") && it.name.endsWith(".bak") })
    }
}
