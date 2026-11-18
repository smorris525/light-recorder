package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.core.Level
import com.stephenmorris.recorder.core.MeterId
import com.stephenmorris.recorder.core.MeterLayout
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.core.StepPaintStroke
import com.stephenmorris.recorder.core.Swing
import com.stephenmorris.recorder.core.Tempo
import com.stephenmorris.recorder.tape.NameEdit
import java.util.UUID

/**
 * What the pattern screens change (spec §10).
 * Every change goes into the song, which the transport reads on every loop, so an edit is heard from
 * the next step it hasn't scheduled yet. Main thread only.
 */
class PatternEditing(private val model: SongModel, private val transport: Transport) {
    private var stroke: StepPaintStroke? = null

    /** A tap toggles rest and hit; tapping an accent clears it. */
    fun tap(id: UUID, sound: Sound, step: Int) = model.updatePattern(id) { it.copy(steps = it.steps.toggled(sound, step)) }

    /** A hold sets an accent. */
    fun accent(id: UUID, sound: Sound, step: Int) =
        model.updatePattern(id) { it.copy(steps = it.steps.with(sound, step, Level.ACCENT)) }

    /** A drag begins: its first cell decides whether it paints or erases. */
    fun beginPaint(id: UUID, sound: Sound, step: Int) {
        stroke = StepPaintStroke(model.pattern(id)?.steps?.get(sound, step))
    }

    fun paint(id: UUID, sound: Sound, step: Int) {
        val stroke = stroke ?: return
        model.updatePattern(id) { pattern ->
            val painted = stroke.apply(sound, step, pattern.steps)
            if (painted === pattern.steps) pattern else pattern.copy(steps = painted)
        }
    }

    fun endPaint() {
        stroke = null
    }

    fun setBpm(id: UUID, bpm: Int) = model.updatePattern(id) { it.copy(bpm = Tempo.clamped(bpm)) }

    /** The meter changes; the steps stay where they are. */
    fun setMeter(id: UUID, meter: MeterId) = model.updatePattern(id) { it.copy(meter = meter) }

    /** A new length: longer repeats the bars there are into empty new ones (`DrumPattern.resized`). */
    fun setBars(id: UUID, bars: Int) = model.updatePattern(id) { it.resized(MeterLayout.clampedBars(bars)) }

    fun setSwing(id: UUID, swing: Swing) = model.updatePattern(id) { it.copy(swing = swing) }

    fun rename(id: UUID, typed: String) = model.updatePattern(id) { it.copy(name = NameEdit.renamed(it.name, typed)) }

    /** Duplicate on a pattern's Actions: a copy straight after it, selected. Returns its id. */
    fun duplicate(id: UUID): UUID {
        var copy = id
        model.updateSong { song -> song.duplicatingPattern(id).also { copy = it.second }.first }
        return copy
    }

    /**
     * Deletes a pattern, unless it's the last. A pattern take, audition or overdub stops first, keeping what's been
     * heard of a take, so it can't go on playing a pattern that's gone.
     */
    fun delete(id: UUID) {
        if (model.song.patterns.size <= 1) {
            return
        }
        if (transport.patternTake.value != null) {
            transport.stop()
        }
        if (model.auditionPattern == id) {
            model.auditionPattern = null
        }
        model.updateSong { it.deletingPattern(id) }
    }

    /** ▶ on a pattern's screens loops it alone, without the tape. */
    fun audition(id: UUID) {
        model.auditionPattern = id
    }

    /**
     * Pattern or Pads goes (spec §6): the pattern stops, an overdub ends keeping its hits, and ▶ plays the tape again.
     * The tape playing under a take carries on.
     */
    fun leave() {
        val take = transport.patternTake.value
        if (take != null && !take.records) {
            transport.stop()
        }
        model.auditionPattern = null
        model.clicks = false
    }

    /** Pads is showing: the pattern clicks while the click is on (spec §3). */
    fun padsShown(clickOn: Boolean) {
        model.clicks = clickOn
    }

    fun padsHidden() {
        model.clicks = false
    }
}
