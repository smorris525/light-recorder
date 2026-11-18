package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.Bounce
import com.stephenmorris.recorder.tape.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One visit to Bounce (spec §15): the tracks bounced From (numbers from 1) and the one
 * To. Bounce, From and To share it. Opens with nothing From and Track 1 To. Main thread only.
 */
class BounceChoice {
    private val _sources = MutableStateFlow<Set<Int>>(emptySet())
    val sources: StateFlow<Set<Int>> = _sources.asStateFlow()

    private val _destination = MutableStateFlow(1)
    val destination: StateFlow<Int> = _destination.asStateFlow()

    private val _isBouncing = MutableStateFlow(false)
    val isBouncing: StateFlow<Boolean> = _isBouncing.asStateFlow()

    /** From has a track other than just To, and no bounce is under way. */
    val canBounce: Boolean
        get() = !_isBouncing.value && _sources.value.isNotEmpty() && _sources.value != setOf(_destination.value)

    /** Picks or unpicks Track [number] From; a track with nothing recorded can't be picked. */
    fun toggle(number: Int, song: Song) {
        val sources = _sources.value
        if (number in sources) {
            _sources.value = sources - number
        } else if ((song.tracks.getOrNull(number - 1)?.contentEnd ?: 0.0) > 0) {
            _sources.value = sources + number
        }
    }

    fun pickDestination(number: Int) {
        _destination.value = number
    }

    /** From, as a phrase: "None", "Track 1", "Tracks 1 and 2". */
    fun summary(): String = Bounce.summary(_sources.value.sorted())

    /**
     * Whether the bounce throws away To's recording: it has one and isn't bounced from, so the bounce records over it.
     * With nothing From there's no bounce yet, so nothing to warn of.
     */
    fun replacesRecording(song: Song): Boolean {
        val destination = _destination.value
        val track = song.tracks.getOrNull(destination - 1) ?: return false
        return _sources.value.isNotEmpty() && track.contentEnd > 0 && destination !in _sources.value
    }

    /** BOUNCE: mixes From onto To. Nothing unless [canBounce]; `true` once it's on the tape. */
    suspend fun bounce(measurer: MixMeasurer): Boolean {
        if (!canBounce) {
            return false
        }
        _isBouncing.value = true
        try {
            return measurer.bounce(_sources.value, _destination.value)
        } finally {
            _isBouncing.value = false
        }
    }
}
