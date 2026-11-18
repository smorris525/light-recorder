package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRowsTest {
    @Test
    fun bpmStepsAndClamps() {
        assertEquals(101, SettingsRows.bpm(100, 1))
        assertEquals(300, SettingsRows.bpm(300, 5))
        assertEquals(30, SettingsRows.bpm(32, -5))
    }

    @Test
    fun barsStepAndClamp() {
        assertEquals(1, SettingsRows.bars(1, -1))
        assertEquals(8, SettingsRows.bars(8, 1))
        assertEquals(4, SettingsRows.bars(3, 1))
    }

    @Test
    fun lengthSummary() {
        assertEquals("1 bar", SettingsRows.length(1))
        assertEquals("2 bars", SettingsRows.length(2))
        assertEquals("8 bars", SettingsRows.length(20))
    }

    @Test
    fun tempoSummary() {
        assertEquals("108 BPM", SettingsRows.tempo(108))
        assertEquals("300 BPM", SettingsRows.tempo(400))
    }

    // Swing is hidden in 6/8, 7/8 and 12/8.
    @Test
    fun swingShownOnlyInQuarterMeters() {
        assertEquals(
            listOf(true, true, true, false, false, false),
            MeterId.entries.map { SettingsRows.swingShown(it) },
        )
    }
}
