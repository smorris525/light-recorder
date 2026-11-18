package com.stephenmorris.recorder.store

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickSettingTest {
    private var stored: String? = null
    private val setting = ClickSetting(read = { stored }, write = { stored = it })

    @Test
    fun onByDefault() = runBlocking {
        assertTrue(setting.load())
    }

    @Test
    fun offIsKept() = runBlocking {
        setting.save(false)
        assertFalse(setting.load())
        setting.save(true)
        assertTrue(setting.load())
    }

    @Test
    fun anUnknownValueIsOn() = runBlocking {
        stored = "loud"
        assertTrue(setting.load())
    }
}
