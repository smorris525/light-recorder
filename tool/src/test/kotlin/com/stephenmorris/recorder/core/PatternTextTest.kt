package com.stephenmorris.recorder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatternTextTest {
    @Test
    fun sanitizedTitle() {
        assertEquals("Verse", PatternText.sanitizedTitle("  Verse  "))
        assertNull(PatternText.sanitizedTitle("   "))
        assertNull(PatternText.sanitizedTitle("\n"))
        assertEquals(60, PatternText.sanitizedTitle("b".repeat(60))?.length)
    }

    @Test
    fun metaLine() {
        assertEquals("68 BPM · 4/4 · 4 bars", PatternText.libraryMeta(68, MeterId.FOUR_FOUR, 4))
        assertEquals("92 BPM · 12/8 · 1 bar", PatternText.libraryMeta(92, MeterId.TWELVE_EIGHT, 1))
    }

    @Test
    fun metaLineClampsCorruptValues() {
        assertEquals("300 BPM · 7/8 · 1 bar", PatternText.libraryMeta(9_000, MeterId.SEVEN_EIGHT, 0))
    }

    @Test
    fun savesTheTrimmedName() {
        assertEquals("Verse", PatternText.renamed("  Verse  ", "New Pattern"))
    }

    @Test
    fun anEmptyNameKeepsTheOldOne() {
        assertEquals("Verse", PatternText.renamed("   ", "Verse"))
        assertEquals("Verse", PatternText.renamed("", "Verse"))
    }

    // Names have no length limit; the title truncates on screen instead.
    @Test
    fun keepsLongNamesWhole() {
        val long = "a".repeat(31) + " b " + "c".repeat(40)
        assertEquals(long, PatternText.renamed(long, "Verse"))
    }

    @Test
    fun duplicateUsesTheLowestAvailableSuffix() {
        assertEquals("Groove 2", PatternText.duplicateTitle("Groove", listOf("Groove")))
        assertEquals("Groove 3", PatternText.duplicateTitle("Groove", listOf("Groove", "Groove 2")))
        assertEquals("Groove 2", PatternText.duplicateTitle("Groove", listOf("Groove", "Groove 3")))
        assertEquals("Groove 3 2", PatternText.duplicateTitle("Groove 3", listOf("Groove", "Groove 3")))
        assertEquals("Waltz 3", PatternText.duplicateTitle("Waltz", listOf("Waltz", "Waltz 2")))
    }
}
