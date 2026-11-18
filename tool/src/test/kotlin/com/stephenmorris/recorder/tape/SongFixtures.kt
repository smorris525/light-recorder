package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound

/** A song for tests: eight loops of a rock beat at 92 BPM, recorded on Track 1. */
val Song.Companion.sample: Song
    get() {
        val song = Song.new("Back Porch")
        val pattern = song.patterns[0].copy(bpm = 92)
        val track = song.tracks[0].copy(hits = pattern.hits(from = 0.0, loops = 8))
        return song.copy(patterns = listOf(pattern)).withTrack(0, track)
    }

/** A hit at [time], any sound at full level. */
fun hit(at: Double): Hit = Hit(at, Sound.KICK, Level.ACCENT)

/** This song with the track at [index] replaced. */
fun Song.withTrack(index: Int, track: Track): Song =
    copy(tracks = tracks.toMutableList().also { it[index] = track })

/** This song with the pattern at [index] changed by [change]. */
fun Song.withPattern(index: Int, change: (DrumPattern) -> DrumPattern): Song =
    copy(patterns = patterns.toMutableList().also { it[index] = change(it[index]) })
