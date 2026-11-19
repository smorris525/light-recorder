package com.stephenmorris.recorder.platform

import android.media.AudioDeviceInfo
import com.stephenmorris.recorder.engine.RouteKind

/** The kind of an `AudioDeviceInfo.type`, for [com.stephenmorris.recorder.engine.InterfaceRoute]; `null` for no device. */
fun routeKind(deviceType: Int?): RouteKind? = when (deviceType) {
    null -> null
    AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> RouteKind.BUILT_IN
    AudioDeviceInfo.TYPE_USB_DEVICE -> RouteKind.USB_DEVICE
    AudioDeviceInfo.TYPE_USB_HEADSET -> RouteKind.USB_HEADSET
    else -> RouteKind.OTHER
}
