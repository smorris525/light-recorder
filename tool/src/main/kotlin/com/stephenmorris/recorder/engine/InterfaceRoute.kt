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
    private val _inputCount = MutableStateFlow(0)
    private var input: RouteKind? = null
    private var output: RouteKind? = null

    /** The inputs an interface's mic last opened with this session; `null` until one has. */
    private var interfaceChannels: Int? = null

    val hasInterface: StateFlow<Boolean> = _hasInterface.asStateFlow()

    /**
     * How many of an interface's inputs there are to choose from (spec §9): 0 without one, as the phone's own mic is
     * the only input then; with one, as many as its mic last opened with, or 2 until it has.
     */
    val inputCount: StateFlow<Int> = _inputCount.asStateFlow()

    /** Whether an interface's mic has opened this session, so [inputCount] isn't a guess. */
    val isCounted: Boolean
        get() = synchronized(this) { interfaceChannels != null }

    /** Where the input is routed now, and with how many [channels]; `null` while it's closed. */
    fun inputRouted(kind: RouteKind?, channels: Int = 1) = synchronized(this) {
        input = kind
        if (kind == RouteKind.USB_DEVICE) {
            interfaceChannels = channels
        }
        update()
    }

    /** Where the output is routed now; `null` while it's suspended. */
    fun outputRouted(kind: RouteKind?) = synchronized(this) {
        output = kind
        update()
    }

    private fun update() {
        _hasInterface.value = (input ?: output) == RouteKind.USB_DEVICE
        _inputCount.value = if (_hasInterface.value) interfaceChannels ?: 2 else 0
    }
}
