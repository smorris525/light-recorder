package com.stephenmorris.recorder.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputShapesTest {
    private val shapes = InputShapes()

    @Test
    fun anInterfacesInputsAreOpenedTogether() {
        assertEquals(2, shapes.inputsOf(RouteKind.USB_DEVICE, 7, intArrayOf(1, 2)))
        assertEquals(2, shapes.inputsOf(RouteKind.USB_DEVICE, 7, intArrayOf()))
        assertEquals(8, shapes.inputsOf(RouteKind.USB_DEVICE, 7, intArrayOf(16)))
        assertEquals(1, shapes.inputsOf(RouteKind.BUILT_IN, 3, intArrayOf(2)))
        assertEquals(1, shapes.inputsOf(RouteKind.USB_HEADSET, 4, intArrayOf(2)))
    }

    @Test
    fun aRefusedInterfaceOpensMono() {
        shapes.refuse(7)

        assertEquals(1, shapes.inputsOf(RouteKind.USB_DEVICE, 7, intArrayOf(4)))
        assertEquals(4, shapes.inputsOf(RouteKind.USB_DEVICE, 8, intArrayOf(4)))
    }

    @Test
    fun noDeviceIsNoReasonToReopen() {
        assertFalse(shapes.needsReopen(4, null))
    }

    @Test
    fun aRecorderOpenForAnotherShapeReopens() {
        assertTrue(shapes.needsReopen(4, RoutedDevice(RouteKind.BUILT_IN, 3, intArrayOf(1))))
        assertTrue(shapes.needsReopen(1, RoutedDevice(RouteKind.USB_DEVICE, 7, intArrayOf(2))))
        assertFalse(shapes.needsReopen(2, RoutedDevice(RouteKind.USB_DEVICE, 7, intArrayOf(2))))
    }

    @Test
    fun anInterfaceRefusedAfterStartingIsNotRetried() {
        val device = RoutedDevice(RouteKind.USB_DEVICE, 7, intArrayOf(2))
        shapes.refuse(7)

        assertFalse(shapes.needsReopen(1, device))
    }
}
