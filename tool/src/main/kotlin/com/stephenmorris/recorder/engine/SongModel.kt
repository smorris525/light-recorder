package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.store.Delayer
import com.stephenmorris.recorder.store.SaveScheduler
import com.stephenmorris.recorder.store.SongStore
import com.stephenmorris.recorder.tape.Arming
import com.stephenmorris.recorder.tape.DrumPattern
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TapeView
import com.stephenmorris.recorder.tape.Track
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A step of a pattern being heard. */
data class PatternStep(val pattern: UUID, val step: Int)

/**
 * The songs, the open one, and what every screen and the transport share about it. The
 * songs are saved half a second after they stop changing (spec §3), and at once on [saveNow]. The playhead lives apart
 * from the songs, so that only what shows it redraws. Main thread only.
 */
class SongModel(private val store: SongStore, delayer: Delayer) {
    private val _songs = MutableStateFlow(store.loadSongs())
    private val _openSongId = MutableStateFlow<UUID?>(null)
    private val saver = SaveScheduler(delayer) { store.save(_songs.value) }

    /** Every song, oldest first. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isOpening = MutableStateFlow(false)

    /** A song is being opened ([openPreloaded]): its recordings are being read. */
    val isOpening: StateFlow<Boolean> = _isOpening.asStateFlow()

    /** The open song's id, or `null` on the library. */
    val openSongId: StateFlow<UUID?> = _openSongId.asStateFlow()

    /** In seconds on the tape. */
    val playhead = MutableStateFlow(0.0)

    /** The step being heard, while a pattern plays. */
    val playingStep = MutableStateFlow<PatternStep?>(null)

    /** The tracks ● records into. Not saved: a song opens with nothing armed. */
    val arming = MutableStateFlow(Arming())

    /** A finger is moving the playhead: the transport leaves it alone until it lifts. */
    var isScrubbingPlayhead = false

    /** The pattern whose screens are on show, which play loops on its own; set by those screens. */
    var auditionPattern: UUID? = null

    /** Whether a pattern playing alone clicks: only while Pads shows with the click on (spec §3), so off to start. */
    var clicks = false

    /** The view pages along with the playhead during playback. */
    var isFollowing = false

    /** Told the song that's just opened, as it opens: the transport loads its recordings. */
    val onOpen = mutableListOf<(Song) -> Unit>()

    /** Told just before the open song closes, while it's still open: the transport ends its takes there. */
    val onBeforeClose = mutableListOf<() -> Unit>()

    /**
     * Told when a song closes, with the song opening in its place, if one is: the measured mix is forgotten, and the
     * recordings the next song doesn't play are let go.
     */
    val onClose = mutableListOf<(Song?) -> Unit>()

    private val openIndex: Int
        get() = _openSongId.value?.let { id -> _songs.value.indexOfFirst { it.id == id } } ?: -1

    val isSongOpen: Boolean
        get() = openIndex >= 0

    /** The open song, or an empty stand-in whose changes go nowhere. */
    val song: Song
        get() = _songs.value.getOrNull(openIndex) ?: NO_SONG

    /** Opens a song at 0:00, at the start of the tape, with nothing armed. */
    fun open(id: UUID) {
        // With none open there's nothing to close, and closing would let go of recordings just preloaded for this one.
        if (isSongOpen) {
            close(next = _songs.value.firstOrNull { it.id == id })
        } else {
            arming.value = Arming()
        }
        if (_songs.value.none { it.id == id }) {
            return
        }
        _openSongId.value = id
        playhead.value = 0.0
        isFollowing = false
        updateSong { it.copy(view = TapeView()) }
        onOpen.forEach { it(song) }
    }

    /**
     * Opens a song once [preload] has run on it (reading its recordings off the main thread). Nothing happens while
     * another song is opening, so a second tap meanwhile doesn't open two.
     */
    suspend fun openPreloaded(id: UUID, preload: suspend (Song) -> Unit) {
        if (_isOpening.value) {
            return
        }
        val target = _songs.value.firstOrNull { it.id == id } ?: return
        _isOpening.value = true
        try {
            preload(target)
            open(id)
        } finally {
            _isOpening.value = false
        }
    }

    /** Leaves the open song: its tracks are disarmed. */
    fun close() = close(next = null)

    private fun close(next: Song?) {
        if (isSongOpen) {
            onBeforeClose.forEach { it() }
        }
        arming.value = Arming()
        _openSongId.value = null
        onClose.forEach { it(next) }
    }

    /** Changes the open song; nothing happens with none open. */
    fun updateSong(change: (Song) -> Song) {
        val index = openIndex
        if (index < 0) {
            return
        }
        val list = _songs.value.toMutableList()
        val changed = change(list[index])
        if (changed == list[index]) {
            return
        }
        list[index] = changed
        _songs.value = list
        saver.changed()
    }

    fun track(id: UUID): Track? = song.tracks.firstOrNull { it.id == id }

    fun updateTrack(id: UUID, change: (Track) -> Track) = updateSong { song ->
        song.copy(tracks = song.tracks.map { if (it.id == id) change(it) else it })
    }

    fun pattern(id: UUID): DrumPattern? = song.pattern(id)

    fun updatePattern(id: UUID, change: (DrumPattern) -> DrumPattern) = updateSong { song ->
        song.copy(patterns = song.patterns.map { if (it.id == id) change(it) else it })
    }

    /** Removes a song; the open one should be closed first. */
    fun removeSong(id: UUID) {
        val list = _songs.value.filter { it.id != id }
        if (list.size != _songs.value.size) {
            replaceSongs(list)
        }
    }

    /** Replaces every song, as the library's own operations do. */
    fun replaceSongs(songs: List<Song>) {
        _songs.value = songs
        saver.changed()
    }

    /**
     * Pages the view along with the playhead, while following. The transport calls it only while playing. A take running
     * past the song's end pages on past it.
     */
    fun follow() {
        if (!isFollowing) {
            return
        }
        updateSong { song ->
            val length = maxOf(song.length, playhead.value + song.view.secondsAcross)
            song.copy(view = song.view.following(playhead.value, length))
        }
    }

    fun saveNow() = saver.saveNow()

    private companion object {
        val NO_SONG = Song.new("")
    }
}
