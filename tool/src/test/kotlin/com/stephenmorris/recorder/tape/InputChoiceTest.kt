package com.stephenmorris.recorder.tape

import kotlin.test.Test
import kotlin.test.assertEquals

class InputChoiceTest {
    private fun mic(channel: Int) = Track(number = 2, input = TrackInput.MIC, channel = channel)

    private val drums = Track(number = 1, input = TrackInput.DRUMS)

    private fun underlined(track: Track, inputCount: Int) =
        inputChoices(inputCount).filter { track.isChoice(it, inputCount) }.map { it.label(inputCount) }

    @Test
    fun oneInputOrNoneIsMicLineAndDrums() {
        assertEquals(listOf("Mic/Line", "Drums"), inputChoices(1).map { it.label(1) })
    }

    @Test
    fun fourInputsAreListed() {
        assertEquals(
            listOf("Input 1", "Input 2", "Input 3", "Input 4", "Drums"),
            inputChoices(4).map { it.label(4) },
        )
    }

    @Test
    fun labels() {
        assertEquals("Input 2", mic(2).inputLabel(2))
        assertEquals("Input 1", mic(1).inputLabel(2))
        assertEquals("Mic/Line", mic(1).inputLabel(1))
        assertEquals("Input 4 → Input 1", mic(4).inputLabel(2))
        assertEquals("Input 2 → Mic/Line", mic(2).inputLabel(1))
        assertEquals("Drums", drums.inputLabel(4))
    }

    @Test
    fun recordedChannelFallsBackTo1() {
        assertEquals(2, recordedChannel(2, 4))
        assertEquals(1, recordedChannel(4, 2))
        assertEquals(1, recordedChannel(3, 1))
    }

    @Test
    fun underlineIsWhatRecords() {
        assertEquals(listOf("Input 1"), underlined(mic(4), 2))
        assertEquals(listOf("Input 3"), underlined(mic(3), 4))
        assertEquals(listOf("Mic/Line"), underlined(mic(2), 1))
        assertEquals(listOf("Drums"), underlined(drums, 2))
    }
}
