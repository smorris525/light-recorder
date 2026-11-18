package com.stephenmorris.recorder.tape

/**
 * The stretch of tape on screen: where it starts and how many seconds fit across. There's no zoom: the screen always
 * shows the standard seconds across, and the rest of a longer song is scrolled to.
 */
data class TapeView(val start: Double = 0.0, val secondsAcross: Double = Song.BLANK_TAPE)
