package com.stephenmorris.recorder.core

/** The thresholds for an accented pad tap (spec §10). */
object PadDynamics {
    /** Finger contact radius. Contact size is a rough proxy for force and varies by finger. */
    const val ACCENT_RADIUS_DP = 25f

    /** A firm press, as a fraction of the screen's normalized pressure. */
    const val ACCENT_PRESSURE = 0.5f
}

/**
 * Decides whether a pad tap is an accent, using pressure or contact size only once the screen has shown it really
 * reports them (spec §10). Android always reports some pressure and size, even fake constant ones; until two taps
 * have reported different values, every tap is a plain hit.
 */
class PadTouchProbe {
    private var firstPressure: Float? = null
    private var pressureIsReal = false
    private var firstSize: Float? = null
    private var sizeIsReal = false

    /** Call on every touch-down, before [level]. [touchMajorDp] is the contact's diameter. */
    fun observe(touchMajorDp: Float, pressure: Float) {
        if (pressure > 0f && pressure < 1f) {
            val first = firstPressure
            if (first == null) firstPressure = pressure else if (first != pressure) pressureIsReal = true
        }
        if (touchMajorDp > 0f) {
            val first = firstSize
            if (first == null) firstSize = touchMajorDp else if (first != touchMajorDp) sizeIsReal = true
        }
    }

    fun level(touchMajorDp: Float, pressure: Float): Level = when {
        pressureIsReal -> if (pressure >= PadDynamics.ACCENT_PRESSURE) Level.ACCENT else Level.HIT
        sizeIsReal -> if (touchMajorDp / 2 >= PadDynamics.ACCENT_RADIUS_DP) Level.ACCENT else Level.HIT
        else -> Level.HIT
    }
}
