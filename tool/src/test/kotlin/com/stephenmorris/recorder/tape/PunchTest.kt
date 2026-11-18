package com.stephenmorris.recorder.tape

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PunchTest {
    @Test
    fun oneMicTrackAtATime() {
        val a = Track(number = 2, input = TrackInput.MIC)
        val b = Track(number = 3, input = TrackInput.MIC)
        val arming = Arming().toggling(a).toggling(b)
        assertEquals(b.id, arming.audio)
        assertNull(arming.drums)
    }

    @Test
    fun aDrumTrackAndAMicTrackArmTogether() {
        val drums = Track(number = 1, input = TrackInput.DRUMS)
        val mic = Track(number = 2, input = TrackInput.MIC)
        val arming = Arming().toggling(drums).toggling(mic)
        assertEquals(mic.id, arming.audio)
        assertEquals(drums.id, arming.drums)
    }

    @Test
    fun togglingAnArmedTrackDisarmsIt() {
        val a = Track(number = 2, input = TrackInput.MIC)
        assertTrue(Arming().toggling(a).toggling(a).isEmpty)
    }

    @Test
    fun removingArmedTrackDisarms() {
        val a = Track(number = 2, input = TrackInput.MIC)
        val arming = Arming().toggling(a)
        assertTrue(arming.isArmed(a.id))
        assertTrue(arming.removing(a.id).isEmpty)
    }

    @Test
    fun playStartsAtThePlayheadWhateverThePoints() {
        var song = Song.new("s")
        assertEquals(7.0, Punch.playStart(song, playhead = 7.0))
        song = song.settingIn(2.0).settingOut(9.0)
        assertEquals(7.0, Punch.playStart(song, playhead = 7.0))
        assertEquals(0.0, Punch.playStart(song, playhead = 0.0))
    }

    @Test
    fun playFromTheEndStartsAtZero() {
        val song = Song.new("s")
        assertEquals(0.0, Punch.playStart(song, playhead = song.length))
    }

    @Test
    fun recordStartUsesInPointEvenWhilePlaying() {
        var song = Song.new("s")
        assertNull(Punch.recordStart(song, playhead = 4.0, isPlaying = true))
        assertEquals(4.0, Punch.recordStart(song, playhead = 4.0, isPlaying = false))
        song = song.settingIn(2.0)
        assertEquals(2.0, Punch.recordStart(song, playhead = 4.0, isPlaying = true))
    }

    @Test
    fun recordEndsAtOutPointAfterStart() {
        val song = Song.new("s").settingOut(10.0)
        assertEquals(10.0, Punch.recordEnd(song, start = 3.0))
        assertNull(Punch.recordEnd(song, start = 12.0))
    }

    @Test
    fun leadInIsThreeSeconds() {
        assertEquals(Punch.LeadIn(tapeFrom = 7.0, silence = 0.0), Punch.leadIn(10.0))
    }

    @Test
    fun leadInNearZeroIsPartSilent() {
        val lead = Punch.leadIn(1.2)
        assertEquals(0.0, lead.tapeFrom)
        assertTrue(abs(lead.silence - 1.8) < 1e-9)
        assertEquals(3.0, Punch.leadIn(0.0).silence)
    }

    // New: Review Focus 2, tape edges.
    @Test
    fun aLeadInNearTheStartNeverGoesBelowZero() {
        assertEquals(Punch.LeadIn(tapeFrom = 0.0, silence = 2.0), Punch.leadIn(1.0))
        assertEquals(Punch.LeadIn(tapeFrom = 0.0, silence = 3.0), Punch.leadIn(0.0))
    }

    @Test
    fun songEndStopsOnlyPlaybackThatStartedBeforeIt() {
        val song = Song.new("s")
        // An empty song is its blank tape long.
        assertTrue(Punch.hasRunPastEnd(song, passStart = 0.0, time = song.length))
        assertFalse(Punch.hasRunPastEnd(song, passStart = 0.0, time = song.length - 1))
        // From a take's in point beyond the end, playback carries on.
        assertFalse(Punch.hasRunPastEnd(song, passStart = song.length, time = song.length + 5))
        assertFalse(Punch.hasRunPastEnd(song, passStart = song.length + 2, time = song.length + 5))
    }

    @Test
    fun readoutIsMinutesSecondsAndHundredths() {
        assertEquals("0:12.78", TimeText.readout(12.789))
        assertEquals("1:15.00", TimeText.readout(75.0))
        assertEquals("12:03.40", TimeText.readout(723.4))
        assertEquals("1:02:03.40", TimeText.readout(3723.4))
        assertEquals("0:00.00", TimeText.readout(-1.0))
        assertEquals("0:12.73", TimeText.readout(Math.rint(12.73 / 0.01) * 0.01))
    }

    @Test
    fun pointShowsHundredths() {
        assertEquals("00:11.14", TimeText.point(11.14))
        assertEquals("01:21.96", TimeText.point(81.969))
    }
}
