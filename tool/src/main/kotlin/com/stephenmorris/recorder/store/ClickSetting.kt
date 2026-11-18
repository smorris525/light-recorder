package com.stephenmorris.recorder.store

/**
 * Whether the Pads screen clicks (spec §3): one setting for the whole tool, on by default. Stored as `"on"` or `"off"`;
 * anything missing or unknown reads as on.
 */
class ClickSetting(private val read: suspend () -> String?, private val write: suspend (String) -> Unit) {
    suspend fun load(): Boolean = read() != OFF

    suspend fun save(on: Boolean) = write(if (on) ON else OFF)

    companion object {
        private const val ON = "on"
        private const val OFF = "off"
    }
}
