package com.stephenmorris.recorder.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The grid as JSON: `{"hitsBySound":{"kick":{"0":2}}}`. Keys are sorted, so equal grids
 * store equal strings.
 */
fun StepGrid.toJson(): String {
    val sounds = hitsBySound.toSortedMap().mapValues { (_, hits) ->
        JsonObject(
            hits.entries
                .associate { (step, level) -> step.toString() to JsonPrimitive(level.raw) }
                .toSortedMap(),
        )
    }
    return Json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("hitsBySound" to JsonObject(sounds))))
}

/**
 * The grid read back from [json]. Lenient, so one bad entry can't lose the pattern: step keys that aren't integers
 * are dropped, levels that aren't known integers read as hits, and empty maps are removed. Empty or unreadable JSON
 * reads as an empty grid, so one corrupt record can't stop the library loading.
 */
fun StepGrid.Companion.fromJson(json: String): StepGrid {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return StepGrid.EMPTY
    val sounds = root["hitsBySound"] as? JsonObject ?: return StepGrid.EMPTY
    return StepGrid.of(
        sounds.mapNotNull { (sound, entries) ->
            val hits = (entries as? JsonObject)?.entries?.mapNotNull { (key, value) ->
                val step = key.toIntOrNull() ?: return@mapNotNull null
                step to Level.fromRaw((value as? JsonPrimitive)?.intOrNull)
            }?.toMap() ?: return@mapNotNull null
            sound to hits
        }.toMap(),
    )
}
