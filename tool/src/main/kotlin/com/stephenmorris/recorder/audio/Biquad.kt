package com.stephenmorris.recorder.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A second-order filter using the Web Audio API's `BiquadFilterNode` formulas. As in Web Audio, [q] is in decibels of resonance for low-pass and high-pass, and the plain
 * quality factor for band-pass (whose peak gain is 0 dB).
 */
class Biquad(kind: Kind, frequency: Double, q: Double, sampleRate: Double) {
    enum class Kind { LOWPASS, HIGHPASS, BANDPASS }

    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val w0 = 2 * PI * minOf(frequency, sampleRate / 2) / sampleRate
        val cosW0 = cos(w0)
        val alpha = when (kind) {
            Kind.LOWPASS, Kind.HIGHPASS -> sin(w0) / (2 * 10.0.pow(q / 20))
            Kind.BANDPASS -> sin(w0) / (2 * q)
        }
        val (n0, n1, n2) = when (kind) {
            Kind.LOWPASS -> Triple((1 - cosW0) / 2, 1 - cosW0, (1 - cosW0) / 2)
            Kind.HIGHPASS -> Triple((1 + cosW0) / 2, -(1 + cosW0), (1 + cosW0) / 2)
            Kind.BANDPASS -> Triple(alpha, 0.0, -alpha)
        }
        val a0 = 1 + alpha
        b0 = n0 / a0
        b1 = n1 / a0
        b2 = n2 / a0
        a1 = -2 * cosW0 / a0
        a2 = (1 - alpha) / a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}
