package com.stephenmorris.recorder.tape

/** One row of the Input page (spec §9): Drums, or Mic/Line from one of an interface's inputs. */
data class InputChoice(val input: TrackInput, val channel: Int = 1)

/** The Input page's rows: with an interface of two or more inputs, each of them, then Drums; otherwise Mic/Line, Drums. */
fun inputChoices(inputCount: Int): List<InputChoice> {
    val mics = if (inputCount > 1) {
        (1..inputCount).map { InputChoice(TrackInput.MIC, it) }
    } else {
        listOf(InputChoice(TrackInput.MIC))
    }
    return mics + InputChoice(TrackInput.DRUMS)
}

/** The row's name: `Input 2` with more than one input to choose from, otherwise the input's own name. */
fun InputChoice.label(inputCount: Int): String =
    if (input == TrackInput.MIC && inputCount > 1) "Input $channel" else input.displayName

/** The input a track on [channel] records from: that one if the interface has it, else the first. */
fun recordedChannel(channel: Int, inputCount: Int): Int = if (channel <= inputCount) channel else 1

/**
 * The track's input as the Track screen names it: the saved one, or, when it isn't there, the saved one and what it
 * records from instead (`Input 4 → Input 1`, `Input 2 → Mic/Line`).
 */
fun Track.inputLabel(inputCount: Int): String {
    val recorded = InputChoice(input, recordedChannel(channel, inputCount)).label(inputCount)
    if (input != TrackInput.MIC || channel == 1 || channel <= inputCount) {
        return recorded
    }
    return "Input $channel → $recorded"
}

/** Whether [choice] is underlined for this track: what it records from now. */
fun Track.isChoice(choice: InputChoice, inputCount: Int): Boolean =
    choice.input == input && (input == TrackInput.DRUMS || choice.channel == recordedChannel(channel, inputCount))
