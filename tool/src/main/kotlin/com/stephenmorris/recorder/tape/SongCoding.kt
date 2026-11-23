package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import com.stephenmorris.recorder.core.MeterLayout
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepGrid
import com.stephenmorris.recorder.core.Swing
import com.stephenmorris.recorder.core.Tempo
import com.stephenmorris.recorder.core.fromJson
import com.stephenmorris.recorder.core.toJson
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Songs as JSON, for `songs.json` (spec §3). Lenient, so a song saved before a setting existed, or a file that's been
 * damaged, still opens: anything missing, unknown or of the wrong type takes its default, numbers out of range are
 * brought back in, and
 * a song always has four tracks and at least one pattern. Only what makes a song or a track what it is (its id, a
 * song's name, a track's number) has to be there; a song without them is dropped, and a track without them is
 * replaced by an empty Mic/Line track.
 */
object SongCoding {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** [songs] as a JSON array. */
    fun encode(songs: List<Song>): String = json.encodeToString(songs.map { it.dto() })

    /** The songs in [text]. Never throws: unreadable JSON gives an empty list, and unreadable songs are dropped. */
    fun decode(text: String): List<Song> = decodeReporting(text).first

    /** The songs in [text], and whether every one of them could be read (an unreadable file reads none). */
    fun decodeReporting(text: String): Pair<List<Song>, Boolean> {
        val array = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList<Song>() to false
        val songs = array.mapNotNull { element -> runCatching { (element as? JsonObject)?.song() }.getOrNull() }
        return songs to (songs.size == array.size)
    }
}

@Serializable
private data class SongDto(
    val id: String? = null,
    val name: String? = null,
    val notes: String = "",
    val inPoint: Double? = null,
    val outPoint: Double? = null,
    val view: ViewDto = ViewDto(),
    val tracks: List<JsonElement> = emptyList(),
    val patterns: List<JsonElement> = emptyList(),
    val selectedPattern: String? = null,
)

@Serializable
private data class ViewDto(val start: Double = 0.0, val secondsAcross: Double = TapeView.STANDARD_SECONDS_ACROSS)

@Serializable
private data class TrackDto(
    val id: String? = null,
    val number: Int? = null,
    val input: String? = null,
    val channel: Int? = null,
    val volume: Int = 0,
    val pan: Int = 0,
    val spans: List<JsonElement> = emptyList(),
    val hits: List<JsonElement> = emptyList(),
)

@Serializable
private data class SpanDto(
    val start: Double,
    val end: Double,
    val seed: Double = 0.0,
    val clip: String? = null,
    val sourceTime: Double = 0.0,
)

@Serializable
private data class HitDto(val time: Double, val sound: String, val level: Int? = null)

@Serializable
private data class PatternDto(
    val id: String? = null,
    val name: String = "A",
    val bpm: Int = 100,
    val meter: String? = null,
    val swing: String? = null,
    val bars: Int = 1,
    val steps: JsonElement? = null,
)

private val lenient = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private fun String?.uuid(): UUID? = this?.let { runCatching { UUID.fromString(it) }.getOrNull() }

private fun Song.dto() = SongDto(
    id = id.toString(),
    name = name,
    notes = notes,
    inPoint = inPoint,
    outPoint = outPoint,
    view = ViewDto(view.start, view.secondsAcross),
    tracks = tracks.map { lenient.encodeToJsonElement(TrackDto.serializer(), it.dto()) },
    patterns = patterns.map { lenient.encodeToJsonElement(PatternDto.serializer(), it.dto()) },
    selectedPattern = selectedPattern?.toString(),
)

private fun Track.dto() = TrackDto(
    id = id.toString(),
    number = number,
    input = input.raw,
    // Input 1 isn't written, so a song only says which input when it's another.
    channel = channel.takeIf { it != 1 },
    volume = volume,
    pan = pan,
    spans = spans.map {
        lenient.encodeToJsonElement(
            SpanDto.serializer(),
            SpanDto(it.start, it.end, it.seed, it.clip?.toString(), it.sourceTime),
        )
    },
    hits = hits.map {
        lenient.encodeToJsonElement(HitDto.serializer(), HitDto(it.time, it.sound.raw, it.level.raw))
    },
)

private fun DrumPattern.dto() = PatternDto(
    id = id.toString(),
    name = name,
    bpm = bpm,
    meter = meter.raw,
    swing = swing.raw,
    bars = bars,
    steps = JsonPrimitive(steps.toJson()),
)

// Reading is by hand from the parsed tree, so a value of the wrong type takes its default instead of failing the
// whole song, as a typed decoder would.

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

private fun JsonObject.whole(key: String): Int? = number(key)?.let { roundHalfAway(it).toInt() }

private fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/** The song, or `null` without an id or a name. */
private fun JsonObject.song(): Song? {
    val id = text("id").uuid() ?: return null
    val name = text("name") ?: return null

    // Exactly the tracks a song has: the saved ones numbered 1 to TRACK_COUNT (the first, if a number repeats), and a
    // Mic/Line track for each missing number.
    val saved = objects("tracks").mapNotNull { it.track() }
    val songTracks = (1..Song.TRACK_COUNT).map { number ->
        saved.firstOrNull { it.number == number } ?: Track(number = number, input = TrackInput.MIC)
    }

    // A song always has a drum machine, so one saved without patterns gets the rock pattern a new song has.
    val songPatterns = objects("patterns").mapNotNull { it.pattern() }.ifEmpty { listOf(DrumPattern.rock()) }
    val selected = text("selectedPattern").uuid()?.takeIf { wanted -> songPatterns.any { it.id == wanted } }
        ?: songPatterns[0].id
    val view = this["view"] as? JsonObject

    return Song(
        id = id,
        name = name,
        notes = text("notes") ?: "",
        inPoint = number("inPoint")?.coerceAtLeast(0.0),
        outPoint = number("outPoint")?.coerceAtLeast(0.0),
        view = TapeView(
            (view?.number("start") ?: 0.0).coerceAtLeast(0.0),
            view?.number("secondsAcross")?.takeIf { it > 0 } ?: TapeView.STANDARD_SECONDS_ACROSS,
        ),
        tracks = songTracks,
        patterns = songPatterns,
        selectedPattern = selected,
    )
}

/** The track, or `null` without an id or a number. */
private fun JsonObject.track(): Track? {
    val id = text("id").uuid() ?: return null
    val number = whole("number") ?: return null
    val volume = whole("volume") ?: 0
    return Track(
        id = id,
        number = number,
        input = TrackInput.fromRaw(text("input")) ?: TrackInput.MIC,
        channel = whole("channel")?.takeIf { it >= 1 } ?: 1,
        volume = if (volume <= Track.SILENT) Track.SILENT else volume.coerceIn(Track.VOLUMES),
        pan = (whole("pan") ?: 0).coerceIn(-50, 50),
        spans = objects("spans").mapNotNull { span ->
            val start = span.number("start") ?: return@mapNotNull null
            val end = span.number("end") ?: return@mapNotNull null
            Span(start, end, span.number("seed") ?: 0.0, span.text("clip").uuid(), span.number("sourceTime") ?: 0.0)
                .takeIf { it.end > it.start }
        },
        hits = objects("hits").mapNotNull { hit ->
            val time = hit.number("time") ?: return@mapNotNull null
            val sound = Sound.fromRaw(hit.text("sound") ?: return@mapNotNull null) ?: return@mapNotNull null
            Hit(time, sound, Level.fromRaw(hit.whole("level")))
        }.sortedBy { it.time },
    )
}

/** The pattern, or `null` without an id. Steps that can't be read open empty. */
private fun JsonObject.pattern(): DrumPattern? {
    val id = text("id").uuid() ?: return null
    val stepsText = when (val element = this["steps"]) {
        is JsonPrimitive -> element.content
        null -> ""
        else -> element.toString()
    }
    return DrumPattern(
        id = id,
        name = text("name") ?: "A",
        bpm = Tempo.clamped(whole("bpm") ?: 100),
        meter = MeterId.fromRaw(text("meter")),
        swing = Swing.fromRaw(text("swing")),
        bars = MeterLayout.clampedBars(whole("bars") ?: 1),
        steps = StepGrid.fromJson(stepsText),
    )
}
