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
import kotlinx.serialization.json.JsonPrimitive

/**
 * Songs as JSON, for `songs.json` (spec §3). Lenient, so a song saved before a setting existed, or a file that's been
 * damaged, still opens: anything missing or unknown takes its default, numbers out of range are brought back in, and
 * a song always has four tracks and at least one pattern. Only what makes a song or a track what it is (its id, a
 * song's name, a track's number) has to be there; a song without them is dropped, and a track without them is
 * replaced by an empty Microphone track.
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
    fun decode(text: String): List<Song> {
        val array = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(SongDto.serializer(), element).song() }.getOrNull()
        }
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
private data class ViewDto(val start: Double = 0.0, val secondsAcross: Double = Song.BLANK_TAPE)

@Serializable
private data class TrackDto(
    val id: String? = null,
    val number: Int? = null,
    val input: String? = null,
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

/** Each element read as [T], dropping the ones that can't be. */
private fun <T> List<JsonElement>.readEach(read: (JsonElement) -> T?): List<T> =
    mapNotNull { runCatching { read(it) }.getOrNull() }

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

/** The song, or `null` without an id or a name. */
private fun SongDto.song(): Song? {
    val id = id.uuid() ?: return null
    val name = name ?: return null

    // Exactly the tracks a song has: the saved ones numbered 1 to TRACK_COUNT (the first, if a number repeats), and a
    // Microphone track for each missing number.
    val saved = tracks.readEach { lenient.decodeFromJsonElement(TrackDto.serializer(), it).track() }
    val songTracks = (1..Song.TRACK_COUNT).map { number ->
        saved.firstOrNull { it.number == number } ?: Track(number = number, input = TrackInput.MIC)
    }

    // A song always has a drum machine, so one saved without patterns gets the rock pattern a new song has.
    val songPatterns = patterns.readEach { lenient.decodeFromJsonElement(PatternDto.serializer(), it).pattern() }
        .ifEmpty { listOf(DrumPattern.rock()) }
    val selected = selectedPattern.uuid()?.takeIf { wanted -> songPatterns.any { it.id == wanted } }
        ?: songPatterns[0].id

    return Song(
        id = id,
        name = name,
        notes = notes,
        inPoint = inPoint?.coerceAtLeast(0.0),
        outPoint = outPoint?.coerceAtLeast(0.0),
        view = TapeView(view.start.coerceAtLeast(0.0), view.secondsAcross.takeIf { it > 0 } ?: Song.BLANK_TAPE),
        tracks = songTracks,
        patterns = songPatterns,
        selectedPattern = selected,
    )
}

/** The track, or `null` without an id or a number. */
private fun TrackDto.track(): Track? {
    val id = id.uuid() ?: return null
    val number = number ?: return null
    return Track(
        id = id,
        number = number,
        input = TrackInput.fromRaw(input) ?: TrackInput.MIC,
        volume = if (volume <= Track.SILENT) Track.SILENT else volume.coerceIn(Track.VOLUMES),
        pan = pan.coerceIn(-50, 50),
        spans = spans.readEach { element ->
            val span = lenient.decodeFromJsonElement(SpanDto.serializer(), element)
            Span(span.start, span.end, span.seed, span.clip.uuid(), span.sourceTime).takeIf { it.end > it.start }
        },
        hits = hits.readEach { element ->
            val hit = lenient.decodeFromJsonElement(HitDto.serializer(), element)
            Sound.fromRaw(hit.sound)?.let { Hit(hit.time, it, Level.fromRaw(hit.level)) }
        }.sortedBy { it.time },
    )
}

/** The pattern, or `null` without an id. Steps that can't be read open empty. */
private fun PatternDto.pattern(): DrumPattern? {
    val id = id.uuid() ?: return null
    val stepsText = when (val element = steps) {
        is JsonPrimitive -> element.content
        null -> ""
        else -> element.toString()
    }
    return DrumPattern(
        id = id,
        name = name,
        bpm = Tempo.clamped(bpm),
        meter = MeterId.fromRaw(meter),
        swing = Swing.fromRaw(swing),
        bars = MeterLayout.clampedBars(bars),
        steps = StepGrid.fromJson(stepsText),
    )
}
