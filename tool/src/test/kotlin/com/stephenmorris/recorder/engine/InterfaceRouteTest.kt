package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterfaceRouteTest {
    private val route = InterfaceRoute()

    @Test
    fun nothingRoutedIsNoInterface() {
        assertFalse(route.hasInterface.value)
        route.outputRouted(RouteKind.USB_DEVICE)
        route.outputRouted(null)
        assertFalse(route.hasInterface.value)
    }

    @Test
    fun anInterfaceOutputCountsWhileTheInputIsClosed() {
        route.outputRouted(RouteKind.USB_DEVICE)
        assertTrue(route.hasInterface.value)
    }

    @Test
    fun anOpenInputDecidesOverTheOutput() {
        route.outputRouted(RouteKind.USB_DEVICE)
        route.inputRouted(RouteKind.BUILT_IN)
        assertFalse(route.hasInterface.value)

        route.outputRouted(RouteKind.BUILT_IN)
        route.inputRouted(RouteKind.USB_DEVICE)
        assertTrue(route.hasInterface.value)
    }

    @Test
    fun closingTheInputFallsBackToTheOutput() {
        route.outputRouted(RouteKind.USB_DEVICE)
        route.inputRouted(RouteKind.BUILT_IN)
        route.inputRouted(null)
        assertTrue(route.hasInterface.value)
    }

    @Test
    fun aHeadsetIsNotAnInterface() {
        route.outputRouted(RouteKind.USB_HEADSET)
        route.inputRouted(RouteKind.USB_HEADSET)
        assertFalse(route.hasInterface.value)
    }

    @Test
    fun aRouteChangeUpdatesTheAnswer() {
        route.inputRouted(RouteKind.USB_DEVICE)
        assertTrue(route.hasInterface.value)
        route.inputRouted(RouteKind.BUILT_IN)
        assertFalse(route.hasInterface.value)
    }
}
