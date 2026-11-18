package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.Swing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrumPatternTest {
    @Test
    fun rockIsTheSpecDefault() {
        val pattern = DrumPattern.rock()

        assertEquals("A", pattern.name)
        assertEquals(100, pattern.bpm)
        assertEquals(MeterId.FOUR_FOUR, pattern.meter)
        assertEquals(1, pattern.bars)
        assertEquals(Swing.OFF, pattern.swing)
        assertEquals(Level.ACCENT, pattern.steps[Sound.KICK, 0])
    }

    @Test
    fun hitsLoopAtTheLoopDuration() {
        val pattern = DrumPattern.rock().copy(bpm = 120)

        val hits = pattern.hits(from = 10.0, loops = 2)
        val kicks = hits.filter { it.sound == Sound.KICK && it.level == Level.ACCENT }

        assertEquals(2.0, pattern.loopDuration)
        assertEquals(listOf(10.0, 12.0), kicks.map { it.time })
        assertEquals(hits.map { it.time }.sorted(), hits.map { it.time })
    }

    @Test
    fun swingDelaysOffbeats() {
        var pattern = DrumPattern.rock().copy(swing = Swing.SIXTEENTH_HEAVY)

        assertEquals(0.0, pattern.swingOffset(0))
        assertTrue(pattern.swingOffset(1) > 0)

        pattern = pattern.copy(steps = pattern.steps.with(Sound.HAT, 1, Level.HIT))
        val hit = pattern.hits(from = 0.0, loops = 1)
            .firstOrNull { it.sound == Sound.HAT && it.time > 0 && it.time < pattern.stepDuration * 2 }
        assertNotNull(hit)
        assertTrue(hit.time > pattern.stepDuration)

        pattern = pattern.copy(meter = MeterId.SIX_EIGHT)
        assertFalse(pattern.meter.layout.swingAllowed)
        assertEquals(0.0, pattern.swingOffset(1))
    }

    @Test
    fun chokedSoundsAreLeftOut() {
        val pattern = DrumPattern(name = "A").copy(
            steps = DrumPattern(name = "A").steps
                .with(Sound.HAT, 2, Level.HIT)
                .with(Sound.OPEN_HAT, 2, Level.HIT)
                .with(Sound.OPEN_HAT, 4, Level.HIT),
        )

        assertEquals(listOf(Sound.HAT), pattern.sounding(2).map { it.first })
        assertEquals(listOf(Sound.OPEN_HAT), pattern.sounding(4).map { it.first })
    }

    @Test
    fun aLoopHoldsItsBarsOfSteps() {
        val pattern = DrumPattern(name = "A", bars = 2, meter = MeterId.THREE_FOUR)

        assertEquals(24, pattern.stepsPerLoop)
    }

    @Test
    fun lengtheningABeatCopiesIntoEmptyBarsOnly() {
        var pattern = DrumPattern(name = "A")
        pattern = pattern.copy(steps = pattern.steps.with(Sound.KICK, 0, Level.ACCENT).with(Sound.SNARE, 20, Level.HIT))

        pattern = pattern.resized(3)

        assertEquals(Level.ACCENT, pattern.steps[Sound.KICK, 32])
        assertNull(pattern.steps[Sound.KICK, 16])
        assertEquals(3, pattern.bars)
    }

    @Test
    fun shorteningABeatKeepsItsHiddenSteps() {
        var pattern = DrumPattern(name = "A", bars = 2)
        pattern = pattern.copy(steps = pattern.steps.with(Sound.KICK, 20, Level.HIT))

        pattern = pattern.resized(1)

        assertEquals(1, pattern.bars)
        assertEquals(Level.HIT, pattern.steps[Sound.KICK, 20])
    }

    @Test
    fun resizingUsesItsOwnMeter() {
        var pattern = DrumPattern(name = "A", meter = MeterId.THREE_FOUR)
        pattern = pattern.copy(steps = pattern.steps.with(Sound.KICK, 0, Level.ACCENT))

        pattern = pattern.resized(2)

        assertEquals(Level.ACCENT, pattern.steps[Sound.KICK, 12])
    }
}
