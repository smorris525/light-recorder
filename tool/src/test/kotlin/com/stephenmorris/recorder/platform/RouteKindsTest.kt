package com.stephenmorris.recorder.platform

import android.media.AudioDeviceInfo
import com.stephenmorris.recorder.engine.RouteKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteKindsTest {
    @Test
    fun eachDeviceTypeHasItsKind() {
        assertEquals(RouteKind.BUILT_IN, routeKind(AudioDeviceInfo.TYPE_BUILTIN_MIC))
        assertEquals(RouteKind.BUILT_IN, routeKind(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(RouteKind.USB_DEVICE, routeKind(AudioDeviceInfo.TYPE_USB_DEVICE))
        assertEquals(RouteKind.USB_HEADSET, routeKind(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(RouteKind.OTHER, routeKind(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertNull(routeKind(null))
    }
}
