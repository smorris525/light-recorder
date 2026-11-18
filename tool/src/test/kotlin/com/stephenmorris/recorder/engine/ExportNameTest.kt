package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportNameTest {
    @Test
    fun pathCharactersBecomeDashes() {
        assertEquals("A-B-C-D", ExportName.fileName("A/B:C\\D"))
    }

    @Test
    fun blankIsSong() {
        assertEquals("Song", ExportName.fileName("  "))
        assertEquals("Song", ExportName.fileName(""))
    }

    @Test
    fun theNameIsTrimmed() {
        assertEquals("Back Porch", ExportName.fileName("  Back Porch "))
    }
}
