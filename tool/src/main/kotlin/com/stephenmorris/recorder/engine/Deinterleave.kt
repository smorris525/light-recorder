package com.stephenmorris.recorder.engine

/**
 * Copies [frames] samples of the 1-based [channel] out of [source], which interleaves [channels] channels of 16-bit
 * samples, into [into] as floats from −1 to 1.
 */
fun deinterleave(source: ShortArray, frames: Int, channels: Int, channel: Int, into: FloatArray) {
    var index = channel - 1
    for (frame in 0 until frames) {
        into[frame] = source[index] / 32_768f
        index += channels
    }
}
