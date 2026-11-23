package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackInputTest {
    @Test
    fun inputsAreNamed() {
        assertEquals("Built-In Mic", TrackInput.MIC.displayName)
        assertEquals("mic", TrackInput.MIC.raw)
        assertEquals("Drums", TrackInput.DRUMS.displayName)
    }
}
