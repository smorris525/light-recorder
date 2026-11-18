package com.stephenmorris.recorder.platform

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stephenmorris.recorder.store.ClickSetting
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.first

private val CLICK = stringPreferencesKey("click")

/** The Click setting in the tool's preferences, as Drums keeps its own. */
fun ClickSetting.Companion.open(context: SealedLightContext) = ClickSetting(
    read = { context.dataStore.data.first()[CLICK] },
    write = { value -> context.dataStore.edit { it[CLICK] = value } },
)
