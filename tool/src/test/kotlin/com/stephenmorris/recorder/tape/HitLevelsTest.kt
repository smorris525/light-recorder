package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.RecorderKit
import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HitLevelsTest {
    private val rate = 48_000.0
    private val peak = TakeLevels.FRAMES_PER_PEAK / rate
    private val voices = RecorderKit.voices(rate)

    /** The sample song's hits, on one track at 0 dB and centre. */
    private fun drumSong(): Song {
        val song = Song.sample
        val hits = song.tracks.flatMap { it.hits }.sortedBy { it.time }
        val tracks = song.tracks.map { it.copy(hits = emptyList(), spans = emptyList()) }.toMutableList()
        tracks[0] = tracks[0].copy(hits = hits, volume = 0, pan = 0)
        return song.copy(tracks = tracks)
    }

    private fun times(until: Double): List<Double> = generateSequence(0.0) { it + peak }.takeWhile { it < until }.toList()

    /** The point of it: a pattern take is drawn as tall while it records as once the mix is measured with it in. */
    @Test
    fun matchesTheMeasuredMix() {
        val song = drumSong()
        val mix = assertNotNull(MixLevels.render(song, ClipStore(), emptyMap(), voices, rate))
        val levels = HitLevels(voices, rate)
        levels.hits = song.tracks[0].hits

        val pairs = times(song.length).map { (levels.level(it, it + peak) ?: 0f) to (mix.level(it, it + peak) ?: 0f) }
        assertTrue(pairs.isNotEmpty())
        for ((live, measured) in pairs) {
            assertTrue(abs(live - measured) < 0.002f, "live $live, measured $measured")
        }
    }

    /** As a take records: a tick every 20 ms, each with the hits scheduled so far and how far they've been scheduled. */
    @Test
    fun playingOnAsATakeIsScheduledMatchesPlayingItAtOnce() {
        val hits = drumSong().tracks[0].hits
        val whole = HitLevels(voices, rate)
        whole.hits = hits
        val growing = HitLevels(voices, rate)
        val end = hits.last().time + 2
        var horizon = 0.02
        while (horizon <= end) {
            growing.play(hits.filter { it.time < horizon }, through = horizon)
            horizon += 0.02
        }

        for (time in times(hits.last().time + 1)) {
            val expected = whole.level(time, time + peak) ?: 0f
            val actual = growing.level(time, time + peak) ?: 0f
            assertTrue(abs(expected - actual) < 0.002f)
        }
    }

    @Test
    fun addingHitsOneAtATimeMatchesAddingThemAtOnce() {
        val hits = drumSong().tracks[0].hits
        val whole = HitLevels(voices, rate)
        whole.hits = hits
        val growing = HitLevels(voices, rate)
        for (count in 1..hits.size) {
            growing.hits = hits.take(count)
        }

        for (time in times(hits.last().time + 1)) {
            val expected = whole.level(time, time + peak) ?: 0f
            val actual = growing.level(time, time + peak) ?: 0f
            assertTrue(abs(expected - actual) < 0.002f)
        }
    }

    /** A take that starts well into the song plays only from its first hit, not from 0:00. */
    @Test
    fun aTakeLateInTheSongIsQuickToStart() {
        val levels = HitLevels(voices, rate)
        val start = System.nanoTime()
        levels.play(listOf(Hit(600.0, Sound.SNARE, Level.HIT)), through = 600.1)
        assertTrue((System.nanoTime() - start) / 1e9 < 0.25)
        assertNotNull(levels.level(600.0, 600 + peak))
    }

    @Test
    fun droppingHitsMatchesNeverHavingThem() {
        val hits = drumSong().tracks[0].hits
        val kept = hits.take(hits.size / 2)
        val fresh = HitLevels(voices, rate)
        fresh.hits = kept
        val dropped = HitLevels(voices, rate)
        dropped.hits = hits
        dropped.hits = kept

        for (time in times(hits.last().time + 1)) {
            assertEquals(fresh.level(time, time + peak), dropped.level(time, time + peak))
        }
    }

    @Test
    fun noHitIsHeardBeforeTheFirstOrAfterTheLast() {
        val levels = HitLevels(voices, rate)
        levels.hits = listOf(Hit(1.0, Sound.SNARE, Level.HIT))

        assertNotNull(levels.level(1.0, 1 + peak))
        assertNull(levels.level(0.5, 0.6))
        assertNull(levels.level(20.0, 21.0))
    }

    @Test
    fun noHitsHaveNoLevel() {
        assertNull(HitLevels(voices, rate).level(0.0, 10.0))
    }
}
