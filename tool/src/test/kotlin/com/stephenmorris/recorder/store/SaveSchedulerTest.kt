package com.stephenmorris.recorder.store

import kotlin.test.Test
import kotlin.test.assertEquals

class SaveSchedulerTest {
    private val delayer = FakeDelayer()
    private var saves = 0
    private val scheduler = SaveScheduler(delayer) { saves += 1 }

    @Test
    fun changesSaveOnceAfterThePause() {
        repeat(3) { scheduler.changed() }
        assertEquals(0, saves)
        delayer.fire()
        assertEquals(1, saves)
    }

    @Test
    fun aNewChangeRestartsThePause() {
        scheduler.changed()
        scheduler.changed()
        assertEquals(1, delayer.waiting)
    }

    @Test
    fun saveNowSavesAtOnceAndOnlyOnce() {
        scheduler.changed()
        scheduler.saveNow()
        delayer.fire()
        assertEquals(1, saves)
    }

    @Test
    fun saveNowWithNothingChangedDoesNothing() {
        scheduler.saveNow()
        assertEquals(0, saves)
    }
}
