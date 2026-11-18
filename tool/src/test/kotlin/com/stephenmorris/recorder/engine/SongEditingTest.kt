package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.core.StepGrid
import com.stephenmorris.recorder.tape.Span
import com.stephenmorris.recorder.tape.TrackInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongEditingTest {
    private val fixture = TransportFixture()
    private val model = fixture.model
    private val transport = fixture.transport
    private val editing = SongEditing(model, transport, fixture.store)
    private val drums = fixture.track(0).id
    private val mic = fixture.track(1).id

    @Test
    fun newSongsAreNamedUniquely() {
        val first = editing.newSong()
        val second = editing.newSong()

        val names = model.songs.value.associate { it.id to it.name }
        assertEquals("New Song", names[first])
        assertEquals("New Song 2", names[second])
    }

    @Test
    fun deletingTheOpenSongClosesItAndKeepsItsTake() {
        val open = model.song.id
        transport.toggleArm(mic)
        transport.record()
        fixture.advance(4_000)
        assertNotNull(transport.take.value)

        editing.delete(open)

        assertFalse(model.isSongOpen)
        assertNull(transport.take.value)
        assertTrue(fixture.store.loadSongs().none { it.id == open })
    }

    @Test
    fun deletingRemovesRecordingsNoSongUses() {
        val clip = assertNotNull(fixture.clips.add(FloatArray(10) { 0.5f }))
        model.updateTrack(mic) { it.copy(spans = listOf(Span(0.0, 1.0, 0.0, clip))) }
        val open = model.song.id

        editing.delete(open)

        assertNull(fixture.store.loadClip(clip, 48_000.0))
    }

    @Test
    fun duplicatingOpensACopyWithAUniqueName() {
        val original = model.song

        editing.duplicateOpenSong()

        assertNotEquals(original.id, model.song.id)
        assertEquals("Test 2", model.song.name)
        assertEquals(original.tracks, model.song.tracks)
        assertEquals(2, model.songs.value.size)
    }

    @Test
    fun renameIgnoresAnEmptyName() {
        editing.rename("  ")
        assertEquals("Test", model.song.name)
        editing.rename(" Verse ")
        assertEquals("Verse", model.song.name)
    }

    @Test
    fun notesAreSavedTrimmed() {
        editing.setNotes("chorus twice\n\n")
        assertEquals("chorus twice", model.song.notes)
    }

    @Test
    fun inputCantChangeWhileTheTrackRecords() {
        transport.toggleArm(mic)
        transport.record()
        fixture.advance(4_000)
        assertTrue(editing.isRecording(on = mic))

        editing.setInput(TrackInput.DRUMS, on = mic)

        assertEquals(TrackInput.MIC, model.track(mic)?.input)
    }

    @Test
    fun changingInputDisarmsTheTrack() {
        transport.toggleArm(drums)
        assertTrue(model.arming.value.isArmed(drums))

        editing.setInput(TrackInput.MIC, on = drums)

        assertFalse(model.arming.value.isArmed(drums))
        assertEquals(TrackInput.MIC, model.track(drums)?.input)
    }

    @Test
    fun addingAPatternSelectsIt() {
        editing.addPattern()

        val added = model.song.patterns.last()
        assertEquals("B", added.name)
        assertEquals(added.id, model.song.selectedPattern)
        assertEquals(StepGrid.EMPTY, added.steps)
    }

    @Test
    fun addingAPatternDuringATakeQueuesIt() {
        transport.toggleArm(drums)
        transport.record()
        fixture.advance(3_200)

        editing.addPattern()

        assertEquals(model.song.patterns.last().id, transport.patternTake.value?.queued)
    }

    @Test
    fun renamingAPatternTrimsItsName() {
        val pattern = model.song.patterns.first().id

        editing.renamePattern(pattern, " Intro ")

        assertEquals("Intro", model.pattern(pattern)?.name)
    }
}
