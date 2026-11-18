package com.stephenmorris.recorder.tape

import java.util.UUID

/** A song: four tracks on a tape, its punch points, its notes and its drum machine's patterns. */
data class Song(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val notes: String = "",
    /** Where ● punches in, in seconds on the tape. */
    val inPoint: Double? = null,
    /** Where ● punches out, in seconds on the tape. */
    val outPoint: Double? = null,
    /** The part of the tape on screen. */
    val view: TapeView = TapeView(),
    val tracks: List<Track>,
    /** The song's drum machine: every track on Drums plays from these. */
    val patterns: List<DrumPattern>,
    val selectedPattern: UUID?,
) {
    fun pattern(id: UUID): DrumPattern? = patterns.firstOrNull { it.id == id }

    /** The selected pattern, else the last. */
    val currentPattern: DrumPattern?
        get() = selectedPattern?.let(::pattern) ?: patterns.lastOrNull()

    /**
     * Adds an empty pattern named for the first unused letter, with the selected pattern's tempo, meter and length,
     * and selects it. Returns the song and the new pattern's id.
     */
    fun addingPattern(): Pair<Song, UUID> {
        var pattern = DrumPattern(name = nextPatternName())
        currentPattern?.let { pattern = pattern.copy(bpm = it.bpm, meter = it.meter, bars = it.bars) }
        return copy(patterns = patterns + pattern, selectedPattern = pattern.id) to pattern.id
    }

    /**
     * Copies a pattern, steps and all, straight after it, and selects the copy. Returns the song and the copy's id, or
     * this song and [id] if there's no such pattern.
     */
    fun duplicatingPattern(id: UUID): Pair<Song, UUID> {
        val index = patterns.indexOfFirst { it.id == id }
        if (index < 0) {
            return this to id
        }
        val original = patterns[index]
        val duplicate = original.copy(id = UUID.randomUUID(), name = uniqueName(original.name, patterns.map { it.name }))
        val list = patterns.toMutableList().also { it.add(index + 1, duplicate) }
        return copy(patterns = list, selectedPattern = duplicate.id) to duplicate.id
    }

    /** Without a pattern, unless it's the last. Deleting the selected one selects its neighbour. */
    fun deletingPattern(id: UUID): Song {
        val index = patterns.indexOfFirst { it.id == id }
        if (patterns.size <= 1 || index < 0) {
            return this
        }
        val list = patterns.toMutableList().also { it.removeAt(index) }
        val selected = if (selectedPattern == id) list[minOf(index, list.size - 1)].id else selectedPattern
        return copy(patterns = list, selectedPattern = selected)
    }

    private fun nextPatternName(): String {
        val names = patterns.map { it.name }
        return ('A'..'Z').map { it.toString() }.firstOrNull { it !in names } ?: uniqueName("Z", names)
    }

    /** The latest anything is recorded, in seconds; 0 when the tape is blank. */
    val contentEnd: Double
        get() = tracks.maxOfOrNull { it.contentEnd } ?: 0.0

    /** The tape's length: what's recorded and blank tape to record on. */
    val length: Double
        get() = contentEnd + BLANK_TAPE

    /** Whether the in point may go at [time]: before the out point, if there is one. */
    fun canSetIn(time: Double): Boolean = outPoint?.let { time < it } ?: true

    /** Whether the out point may go at [time]: after 0:00, and after the in point, if there is one. */
    fun canSetOut(time: Double): Boolean = time > 0 && (inPoint?.let { time > it } ?: true)

    /** With the in point at [time], not before 0. Unchanged where [canSetIn] says no. */
    fun settingIn(time: Double): Song {
        val at = maxOf(time, 0.0)
        return if (canSetIn(at)) copy(inPoint = at) else this
    }

    /** With the out point at [time], not before 0. Unchanged where [canSetOut] says no. */
    fun settingOut(time: Double): Song {
        val at = maxOf(time, 0.0)
        return if (canSetOut(at)) copy(outPoint = at) else this
    }

    /** Without the in point. */
    fun clearingIn(): Song = copy(inPoint = null)

    /** Without the out point. */
    fun clearingOut(): Song = copy(outPoint = null)

    /** The library's second line: the recorded length, "2:48". */
    val meta: String
        get() {
            val seconds = contentEnd.toInt()
            return "${seconds / 60}:" + (seconds % 60).toString().padStart(2, '0')
        }

    companion object {
        /** The blank tape after the last recording, in seconds. */
        const val BLANK_TAPE = 30.0

        /** Every song has this many tracks. */
        const val TRACK_COUNT = 4

        /** A new song: Track 1 on Drums, the rest on Microphone, and a rock pattern, selected. */
        fun new(name: String): Song {
            val pattern = DrumPattern.rock()
            return Song(
                name = name,
                tracks = (1..TRACK_COUNT).map {
                    Track(number = it, input = if (it == 1) TrackInput.DRUMS else TrackInput.MIC)
                },
                patterns = listOf(pattern),
                selectedPattern = pattern.id,
            )
        }
    }
}
