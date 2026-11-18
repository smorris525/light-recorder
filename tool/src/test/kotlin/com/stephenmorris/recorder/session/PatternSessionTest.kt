package com.stephenmorris.recorder.session

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.engine.PatternEditing
import com.stephenmorris.recorder.engine.PatternStep
import com.stephenmorris.recorder.engine.TransportFixture
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from Drums' `PatternSessionTest`: its follow, window and edit cases, over the song instead of a store. */
class PatternSessionTest {
    private val fixture = TransportFixture()
    private val model = fixture.model
    private val transport = fixture.transport
    private val editing = PatternEditing(model, transport)
    private val a = model.song.patterns[0].id

    private fun session(bars: Int = 2): PatternSession {
        editing.setBars(a, bars)
        return PatternSession(model, transport, editing, a)
    }

    @Test
    fun anEditShowsAtOnceAndReachesTheSong() {
        val session = session()
        session.tap(Sound.SHAKER, 2)
        assertEquals(Level.HIT, session.pattern.value.steps[Sound.SHAKER, 2])
        assertEquals(Level.HIT, model.pattern(a)?.steps?.get(Sound.SHAKER, 2))
    }

    @Test
    fun togglePlayLoopsThisPatternAlone() {
        val session = session()
        session.togglePlay()
        fixture.advance(300)

        assertTrue(session.isPlaying.value)
        assertEquals(a, model.auditionPattern)
        session.togglePlay()
        assertFalse(transport.isPlaying.value)
    }

    @Test
    fun followingMovesTheWindow() {
        val session = session()
        session.togglePlay()
        session.playheadMoved(16)
        assertEquals(1, session.window.value.firstBar)
    }

    @Test
    fun editingTurnsFollowOff() {
        val session = session()
        session.togglePlay()
        session.tap(Sound.SHAKER, 4)
        session.playheadMoved(16)
        assertEquals(0, session.window.value.firstBar)
    }

    @Test
    fun showBarClampsAndFollowsOnlyThePlayingBar() {
        val session = session()
        session.togglePlay()
        session.playheadMoved(2)
        session.showBar(1)
        session.playheadMoved(5)
        assertEquals(1, session.window.value.firstBar)
        session.showBar(0)
        session.playheadMoved(17)
        assertEquals(1, session.window.value.firstBar)
        session.showBar(9)
        assertEquals(1, session.window.value.firstBar)
    }

    @Test
    fun refreshIsIgnoredMidStroke() {
        val session = session()
        session.beginPaint(Sound.HAT, 0)
        editing.rename(a, "Elsewhere")
        session.refresh()
        assertEquals("A", session.pattern.value.name)
        session.endPaint()
        session.refresh()
        assertEquals("Elsewhere", session.pattern.value.name)
    }

    @Test
    fun shrinkingBarsClampsTheWindow() {
        val session = session(bars = 4)
        session.showBar(3)
        editing.setBars(a, 2)
        session.refresh()
        assertEquals(1, session.window.value.firstBar)
        assertEquals(2, session.pattern.value.bars)
    }

    @Test
    fun thePlayheadIsOnlyThisPatterns() {
        val session = session()
        assertEquals(5, session.playhead(PatternStep(a, 5)))
        assertNull(session.playhead(PatternStep(UUID.randomUUID(), 5)))
        assertNull(session.playhead(null))
    }


    @Test
    fun padsRecordWithoutPressingPlayFirst() {
        val session = session()
        transport.recordIntoPattern(session.id)
        fixture.advance(200)
        assertTrue(transport.isPlaying.value)
        assertEquals(1, transport.padCount.value)
        fixture.advance(3_000)
        assertTrue(transport.isPadRecording.value)
    }

    @Test
    fun playShowsStopWhileTheTapePlays() {
        val session = session()
        model.auditionPattern = null
        transport.togglePlay()
        fixture.advance(200)
        assertTrue(session.isPlaying.value)
    }
}
