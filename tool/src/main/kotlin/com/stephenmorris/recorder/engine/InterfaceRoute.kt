package com.stephenmorris.recorder.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What kind of device an audio stream is routed to, as far as telling an interface goes (spec §4.2). */
enum class RouteKind {
    /** The phone's own mic or speaker. */
    BUILT_IN,

    /** A USB audio interface. */
    USB_DEVICE,

    /** A USB-C headset, which has no direct monitoring, so isn't an interface. */
    USB_HEADSET,

    /** Anything else: Bluetooth, the earpiece, a wired headset. */
    OTHER,
}

/**
 * Whether Recorder is using a USB audio interface (spec §4.2), so a Mic/Line track shows the jack and points the player
 * at the interface's own monitoring. The input decides while it's open; otherwise the output does, as an interface
 * plays as well as records. Either stream may report from any thread.
 */
class InterfaceRoute {
    private val _hasInterface = MutableStateFlow(false)
    private var input: RouteKind? = null
    private var output: RouteKind? = null

    val hasInterface: StateFlow<Boolean> = _hasInterface.asStateFlow()

    /** Where the input is routed now; `null` while it's closed. */
    fun inputRouted(kind: RouteKind?) = synchronized(this) {
        input = kind
        update()
    }

    /** Where the output is routed now; `null` while it's suspended. */
    fun outputRouted(kind: RouteKind?) = synchronized(this) {
        output = kind
        update()
    }

    private fun update() {
        _hasInterface.value = (input ?: output) == RouteKind.USB_DEVICE
    }
}
