package com.stephenmorris.recorder.engine

/** The device a recorder is routed to, as far as its shape goes: its kind, its id and the input counts it supports. */
class RoutedDevice(val kind: RouteKind?, val id: Int, val channelCounts: IntArray)

/**
 * How many inputs the mic opens together on a device (spec §4.2): all of a USB interface's, up to [maxChannels], or
 * 1 for anything else, or for an interface that refused them this session. Refusals may come from the reader thread.
 */
class InputShapes(private val maxChannels: Int = MAX_CHANNELS) {
    @Volatile
    private var refused = emptySet<Int>()

    fun inputsOf(kind: RouteKind?, id: Int, channelCounts: IntArray): Int {
        if (kind != RouteKind.USB_DEVICE || id in refused) {
            return 1
        }
        // An interface that doesn't say counts as two (spec §9).
        return (channelCounts.maxOrNull() ?: 2).coerceIn(1, maxChannels)
    }

    /** The interface [id] wouldn't deliver its inputs together: it opens mono from now on. */
    fun refuse(id: Int) = synchronized(this) {
        refused = refused + id
    }

    /**
     * Whether a recorder open with [channels] inputs must reopen in another shape, now it's routed to [device]. Never
     * for no device: a passing gap, which the next route change settles.
     */
    fun needsReopen(channels: Int, device: RoutedDevice?): Boolean {
        device ?: return false
        return channels != inputsOf(device.kind, device.id, device.channelCounts)
    }

    private companion object {
        /** The most inputs opened together: Android's long-standing capture limit. */
        const val MAX_CHANNELS = 8
    }
}
