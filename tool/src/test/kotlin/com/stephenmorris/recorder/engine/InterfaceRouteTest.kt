package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun inputCountIs1WithoutAnInterface() {
        assertEquals(1, route.inputCount.value)
        route.inputRouted(RouteKind.BUILT_IN, 1)
        assertEquals(1, route.inputCount.value)
    }

    @Test
    fun anInterfaceInputReportsItsChannels() {
        route.outputRouted(RouteKind.USB_DEVICE)
        route.inputRouted(RouteKind.USB_DEVICE, 4)
        assertEquals(4, route.inputCount.value)
        route.inputRouted(null)
        assertEquals(4, route.inputCount.value)
        route.outputRouted(RouteKind.BUILT_IN)
        assertEquals(1, route.inputCount.value)
    }

    @Test
    fun anInterfaceNotYetOpenedCountsAs2() {
        route.outputRouted(RouteKind.USB_DEVICE)
        assertEquals(2, route.inputCount.value)
    }
}
