package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepGrid
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongCodingTest {
    private fun decodeOne(json: String): Song = SongCoding.decode("[$json]").single()

    private fun songJson(trackNumbers: List<Int>): String {
        val tracks = trackNumbers.map { """{"id":"${UUID.randomUUID()}","number":$it,"input":"drums"}""" }
        return """{"id":"${UUID.randomUUID()}","name":"s","tracks":[${tracks.joinToString(",")}]}"""
    }

    @Test
    fun aSongComesBackAsItWasSaved() {
        var song = Song.sample.copy(notes = "verse, chorus", inPoint = 2.0, outPoint = 9.0)
        song = song.withTrack(0, song.tracks[0].copy(pan = -20, spans = listOf(Span(1.0, 2.0, 0.5, UUID.randomUUID(), 0.25))))
        val read = SongCoding.decode(SongCoding.encode(listOf(song))).single()

        assertEquals(song.id, read.id)
        assertEquals(song.name, read.name)
        assertEquals(song.notes, read.notes)
        assertEquals(2.0, read.inPoint)
        assertEquals(9.0, read.outPoint)
        assertEquals(song.tracks.map { it.id }, read.tracks.map { it.id })
        assertEquals(listOf(1, 2, 3, 4), read.tracks.map { it.number })
        assertEquals(song.tracks.map { it.input }, read.tracks.map { it.input })
        assertEquals(-20, read.tracks[0].pan)
        assertEquals(song.tracks.map { it.spans }, read.tracks.map { it.spans })
        assertEquals(song.tracks.map { it.hits }, read.tracks.map { it.hits })
        assertEquals(song.patterns, read.patterns)
        assertEquals(song.selectedPattern, read.selectedPattern)
        assertEquals(song, read)
    }

    @Test
    fun aTrackSavedWithoutNewerSettingsTakesTheirDefaults() {
        val id = UUID.randomUUID()
        val song = decodeOne("""{"id":"${UUID.randomUUID()}","name":"s","tracks":[{"id":"$id","number":2}]}""")
        val track = song.tracks[1]

        assertEquals(id, track.id)
        assertEquals(2, track.number)
        assertEquals("Track 2", track.name)
        assertEquals(TrackInput.MIC, track.input)
        assertEquals(0, track.volume)
        assertEquals(0, track.pan)
        assertTrue(track.spans.isEmpty())
    }

    @Test
    fun aSongSavedWithTwoTracksOpensWithFour() {
        val song = decodeOne(songJson(listOf(2, 1)))

        assertEquals(listOf(1, 2, 3, 4), song.tracks.map { it.number })
        assertEquals(
            listOf(TrackInput.DRUMS, TrackInput.DRUMS, TrackInput.MIC, TrackInput.MIC),
            song.tracks.map { it.input },
        )
        assertEquals(1, song.patterns.size)
        assertEquals(song.patterns[0].id, song.selectedPattern)
    }

    @Test
    fun aSongSavedWithFiveTracksKeepsNumbersOneToFour() {
        val song = decodeOne(songJson(listOf(1, 2, 3, 4, 5)))

        assertEquals(listOf(1, 2, 3, 4), song.tracks.map { it.number })
    }

    @Test
    fun aSongWithNoPatternsOpensWithA() {
        val song = decodeOne("""{"id":"${UUID.randomUUID()}","name":"s","patterns":[]}""")

        assertEquals(listOf("A"), song.patterns.map { it.name })
        assertEquals(DrumPattern.rock().steps, song.patterns[0].steps)
        assertEquals(song.patterns[0].id, song.selectedPattern)
    }

    @Test
    fun aSelectedPatternThatIsGoneSelectsTheFirst() {
        val (song, _) = Song.new("s").addingPattern()
        val json = SongCoding.encode(listOf(song.copy(selectedPattern = UUID.randomUUID())))

        val read = SongCoding.decode(json).single()

        assertEquals(read.patterns[0].id, read.selectedPattern)
    }

    @Test
    fun unreadableJsonGivesNoSongs() {
        assertTrue(SongCoding.decode("{nope").isEmpty())
        assertTrue(SongCoding.decode("").isEmpty())
        assertTrue(SongCoding.decode("""{"id":"x"}""").isEmpty())
    }

    @Test
    fun anUnreadableSongIsDroppedAndTheRestOpen() {
        val good = Song.new("good")
        val json = SongCoding.encode(listOf(good)).removeSuffix("]") + """,{"name":"no id"},{"id":"bad","name":"x"}]"""

        assertEquals(listOf(good.id), SongCoding.decode(json).map { it.id })
    }

    @Test
    fun aSongWithAnUnknownInputOpensOnMicrophone() {
        val song = decodeOne(
            """{"id":"${UUID.randomUUID()}","name":"s","tracks":[{"id":"${UUID.randomUUID()}","number":1,"input":"theremin"}]}""",
        )

        assertEquals(TrackInput.MIC, song.tracks[0].input)
    }

    @Test
    fun aPatternWithBadStepsOpensEmpty() {
        val id = UUID.randomUUID()
        val song = decodeOne(
            """{"id":"${UUID.randomUUID()}","name":"s","patterns":[{"id":"$id","name":"A","steps":"{nope"}]}""",
        )

        assertEquals(id, song.patterns.single().id)
        assertEquals(StepGrid.EMPTY, song.patterns.single().steps)
    }

    // New: corrupt numbers are brought back in range, so nothing downstream divides by zero or loops forever.
    @Test
    fun outOfRangeSettingsAreClamped() {
        val song = decodeOne(
            """{"id":"${UUID.randomUUID()}","name":"s",
               "tracks":[{"id":"${UUID.randomUUID()}","number":1,"volume":40,"pan":-90}],
               "patterns":[{"id":"${UUID.randomUUID()}","name":"A","bpm":0,"bars":0,"meter":"9/9","swing":"lots"}]}""",
        )

        assertEquals(6, song.tracks[0].volume)
        assertEquals(-50, song.tracks[0].pan)
        assertEquals(30, song.patterns[0].bpm)
        assertEquals(1, song.patterns[0].bars)
    }

    @Test
    fun hitsOfUnknownSoundsAreDropped() {
        val song = decodeOne(
            """{"id":"${UUID.randomUUID()}","name":"s","tracks":[{"id":"${UUID.randomUUID()}","number":1,
               "hits":[{"time":1.0,"sound":"cowbell","level":2},{"time":2.0,"sound":"kick","level":2}]}]}""",
        )

        assertEquals(listOf(Hit(2.0, Sound.KICK, Level.ACCENT)), song.tracks[0].hits)
    }

    // Review: a value of the wrong type takes its default; it never loses the song or a track's recordings.
    @Test
    fun aWrongTypedValueTakesItsDefaultAndLosesNothing() {
        val clip = UUID.randomUUID()
        val trackId = UUID.randomUUID()
        val json = """[{"id":"${UUID.randomUUID()}","name":"s","notes":5,"selectedPattern":7,"view":"wide","inPoint":"x",
            "tracks":[{"id":"$trackId","number":1,"input":3,"volume":"loud","pan":{},
                "spans":[{"start":1.0,"end":2.0,"seed":"x","clip":"$clip"},{"start":"one","end":2.0}],
                "hits":[{"time":1.0,"sound":"kick","level":"loud"}]}],
            "patterns":[{"id":"${UUID.randomUUID()}","name":3,"bpm":"fast","bars":"two","steps":5}]}]"""

        val song = SongCoding.decode(json).single()

        assertEquals("", song.notes)
        assertEquals(null, song.inPoint)
        val track = song.tracks[0]
        assertEquals(trackId, track.id)
        assertEquals(TrackInput.MIC, track.input)
        assertEquals(0, track.volume)
        assertEquals(0, track.pan)
        assertEquals(listOf(Span(1.0, 2.0, 0.0, clip)), track.spans)
        assertEquals(listOf(Hit(1.0, Sound.KICK, Level.HIT)), track.hits)
        assertEquals(1, song.patterns.size)
        assertEquals(song.patterns[0].id, song.selectedPattern)
    }

    @Test
    fun aFractionalVolumeIsRounded() {
        val song = decodeOne(
            """{"id":"${UUID.randomUUID()}","name":"s","tracks":[{"id":"${UUID.randomUUID()}","number":1,"volume":-3.5}]}""",
        )
        assertEquals(-4, song.tracks[0].volume)
    }

    @Test
    fun aTracksListOfTheWrongTypeOpensWithFourEmptyTracks() {
        val song = decodeOne("""{"id":"${UUID.randomUUID()}","name":"s","tracks":{}}""")
        assertEquals(listOf(1, 2, 3, 4), song.tracks.map { it.number })
    }
}
