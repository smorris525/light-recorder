package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.FakeDelayer
import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.Arming
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongModelTest {
    private val store = SongStore(createTempDirectory("songs").toFile()) { it.run() }
    private val delayer = FakeDelayer()
    private val first = Song.new("a")
    private val second = Song.new("b")

    private fun model(): SongModel {
        store.save(listOf(first, second))
        return SongModel(store, delayer)
    }

    @Test
    fun songsLoadFromTheStore() {
        assertEquals(listOf(first, second), model().songs.value)
    }

    @Test
    fun openingStartsAtZeroWithNothingArmed() {
        val model = model()
        model.open(second.id)
        model.updateSong { it.copy(view = TapeView(start = 12.0)) }
        model.playhead.value = 9.0
        model.arming.value = Arming().toggling(model.song.tracks[1])

        model.open(second.id)

        assertEquals(second.id, model.song.id)
        assertEquals(0.0, model.playhead.value)
        assertEquals(0.0, model.song.view.start)
        assertTrue(model.arming.value.isEmpty)
    }

    @Test
    fun updatingATrackChangesOnlyTheOpenSongAndSchedulesASave() {
        val model = model()
        model.open(second.id)
        val track = model.song.tracks[2]

        model.updateTrack(track.id) { it.copy(volume = -6) }

        assertEquals(-6, model.songs.value[1].tracks[2].volume)
        assertEquals(first, model.songs.value[0])
        delayer.fire()
        assertEquals(-6, store.loadSongs()[1].tracks[2].volume)
    }

    @Test
    fun updatingAPatternChangesIt() {
        val model = model()
        model.open(first.id)
        val pattern = model.song.patterns[0]

        model.updatePattern(pattern.id) { it.copy(bpm = 92) }

        assertEquals(92, model.pattern(pattern.id)?.bpm)
    }

    @Test
    fun withNoSongOpenChangesGoNowhere() {
        val model = model()
        assertFalse(model.isSongOpen)
        model.updateSong { it.copy(name = "changed") }
        assertEquals(listOf(first, second), model.songs.value)
        assertEquals(0, delayer.waiting)
    }

    @Test
    fun followPagesTheViewAlongWithThePlayhead() {
        val model = model()
        model.open(first.id)
        model.isFollowing = true
        model.playhead.value = 31.0

        model.follow()

        assertEquals(TapeView(start = 31.0), model.song.view)
    }

    @Test
    fun followDoesNothingWhenNotFollowing() {
        val model = model()
        model.open(first.id)
        model.playhead.value = 31.0

        model.follow()

        assertEquals(0.0, model.song.view.start)
    }

    @Test
    fun closingDisarms() {
        val model = model()
        model.open(first.id)
        model.arming.value = Arming().toggling(model.song.tracks[0])

        model.close()

        assertTrue(model.arming.value.isEmpty)
        assertFalse(model.isSongOpen)
    }

    @Test
    fun openAndCloseAreAnnounced() {
        val model = model()
        val heard = mutableListOf<String>()
        model.onOpen += { heard += "open ${it.name}" }
        model.onClose += { heard += "close" }

        model.open(first.id)
        model.close()

        assertEquals(listOf("close", "open a", "close"), heard)
    }
}
