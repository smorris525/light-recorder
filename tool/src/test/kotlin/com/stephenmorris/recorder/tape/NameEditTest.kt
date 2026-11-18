package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertEquals

class NameEditTest {
    @Test
    fun renamedTrimsTheTypedName() {
        assertEquals("Verse", NameEdit.renamed("A", "  Verse  "))
    }

    @Test
    fun anEmptyNameKeepsTheOldOne() {
        assertEquals("A", NameEdit.renamed("A", "   "))
    }

    @Test
    fun notesLoseTrailingWhitespaceOnly() {
        assertEquals("  one\ntwo", NameEdit.notes("  one\ntwo \n\n"))
    }
}
