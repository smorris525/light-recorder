package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.NameEdit
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TrackInput
import com.stephenmorris.recorder.tape.uniqueName
import java.util.UUID

/**
 * What the screens change about songs, tracks and patterns. Main thread only.
 */
class SongEditing(private val model: SongModel, private val transport: Transport, private val store: SongStore) {
    /** ⊕ on the Library: "New Song" (then "New Song 2", …), not yet open. Returns its id. */
    fun newSong(): UUID {
        val song = Song.new(uniqueName("New Song", model.songs.value.map { it.name }))
        model.replaceSongs(model.songs.value + song)
        return song.id
    }

    /**
     * Deletes a song, closing it first if it's open (which stops playback and ends its takes), then removes the
     * recordings no song plays any more.
     */
    fun delete(id: UUID) {
        if (model.openSongId.value == id) {
            model.close()
        }
        model.removeSong(id)
        model.saveNow()
        store.removeUnused(model.songs.value)
    }

    /** Duplicate on the song's Actions: a copy under a new name, opened in its place. */
    fun duplicateOpenSong() {
        if (!model.isSongOpen) {
            return
        }
        transport.stop()
        val song = model.song
        val copy = song.copy(id = UUID.randomUUID(), name = uniqueName(song.name, model.songs.value.map { it.name }))
        model.replaceSongs(model.songs.value + copy)
        model.open(copy.id)
    }

    /** Renames the open song: the typed name, trimmed; an empty one changes nothing. */
    fun rename(typed: String) = model.updateSong { it.copy(name = NameEdit.renamed(it.name, typed)) }

    fun setNotes(typed: String) = model.updateSong { it.copy(notes = NameEdit.notes(typed)) }

    fun renamePattern(id: UUID, typed: String) = model.updatePattern(id) { it.copy(name = NameEdit.renamed(it.name, typed)) }

    /** An audio or pattern take is recording onto the track with [on]. */
    fun isRecording(on: UUID): Boolean {
        val pattern = transport.patternTake.value
        return transport.take.value?.track == on || (pattern?.records == true && pattern.track == on)
    }

    /**
     * Sets a track's input, and on Mic/Line which of an interface's inputs. Changing between Mic/Line and Drums disarms
     * it, as it may now be armed on the other; changing which input doesn't. Never while the track is recording; what's
     * on its tape stays as it is.
     */
    fun setInput(input: TrackInput, channel: Int = 1, on: UUID) {
        if (isRecording(on)) {
            return
        }
        if (model.track(on)?.input != input) {
            model.arming.value = model.arming.value.removing(on)
        }
        model.updateTrack(on) { it.copy(input = input, channel = if (input == TrackInput.MIC) channel else it.channel) }
    }

    /** ADD PATTERN on the Patterns page: the next pattern, selected (queued, during a take). */
    fun addPattern() {
        val (song, added) = model.song.addingPattern()
        model.updateSong { song }
        transport.select(added)
    }
}
