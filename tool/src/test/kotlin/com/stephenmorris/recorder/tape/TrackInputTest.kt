package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackInputTest {
    @Test
    fun inputsAreNamed() {
        assertEquals("Microphone", TrackInput.MIC.displayName)
        assertEquals("Drums", TrackInput.DRUMS.displayName)
    }
}
