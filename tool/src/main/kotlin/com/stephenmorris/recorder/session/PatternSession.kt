package com.stephenmorris.recorder.session

import com.stephenmorris.recorder.core.BarWindow
import com.stephenmorris.recorder.core.Follow
import com.stephenmorris.recorder.core.Sound
import com.stephenmorris.recorder.engine.PatternEditing
import com.stephenmorris.recorder.engine.PatternStep
import com.stephenmorris.recorder.engine.PatternTakeState
import com.stephenmorris.recorder.engine.SongModel
import com.stephenmorris.recorder.engine.Transport
import com.stephenmorris.recorder.tape.DrumPattern
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One visit to a pattern's screens (spec §10), Drums' `PatternSession` over the song: the pattern as it is, the bars in
 * view, and follow. Pattern, Sound and Pads share it. Edits go into the song through [editing]; the transport reads
 * them from there. Its view model calls [refresh] when the song changes and [playheadMoved] as the step heard moves.
 * Main thread only.
 */
class PatternSession(
    private val model: SongModel,
    private val transport: Transport,
    private val editing: PatternEditing,
    val id: UUID,
) {
    private val _pattern = MutableStateFlow(model.pattern(id) ?: DrumPattern(id = id, name = ""))
    val pattern: StateFlow<DrumPattern> = _pattern.asStateFlow()

    private var firstBar = 0
    private val _window = MutableStateFlow(windowFor(_pattern.value))

    /** The bars in view. Opening a pattern shows bar 1. */
    val window: StateFlow<BarWindow> = _window.asStateFlow()

    private var follow = Follow()
    private var isPainting = false

    /** The last step heard while this pattern played: what a bar dot compares against. */
    private var lastPlayhead: Int? = null

    /** The transport's pattern take, for the screens to collect: [isPlaying] reads it. */
    val patternTake: StateFlow<PatternTakeState?> = transport.patternTake

    /** The step being heard, for the screens to collect: [playhead] reads it. */
    val playingStep: StateFlow<PatternStep?> = model.playingStep

    /** Whether [take] is this pattern looping alone (not a take on the tape). */
    fun isPlaying(take: PatternTakeState?): Boolean = take != null && !take.records && take.playing == id

    /** The step of [step] if it's this pattern's. */
    fun playhead(step: PatternStep?): Int? = step?.takeIf { it.pattern == id }?.step

    /** The song's copy, unless a drag is under way, so nothing arriving mid-stroke can undo it. */
    fun refresh() {
        if (isPainting) {
            return
        }
        _pattern.value = model.pattern(id) ?: return
        updateWindow()
    }

    /** ▶ loops this pattern alone, following it; ■ stops whatever's playing. */
    fun togglePlay() {
        if (transport.isPlaying.value) {
            transport.togglePlay()
            lastPlayhead = null
            return
        }
        editing.audition(id)
        follow = follow.played()
        transport.togglePlay()
    }

    /** A bar dot: shows that bar first; follow stays on only for the playing bar's dot. */
    fun showBar(bar: Int) {
        val playingBar = if (transport.isPlaying.value) lastPlayhead?.let { _window.value.bar(it) } else null
        follow = follow.tappedBar(bar, playingBar)
        firstBar = _window.value.startingAt(bar).firstBar
        updateWindow()
    }

    /** While following, the window moves to the playing bar when the playhead leaves it. */
    fun playheadMoved(step: Int) {
        lastPlayhead = step
        firstBar = follow.window(_window.value, step).firstBar
        updateWindow()
    }

    /** A rename: trimmed; an empty name keeps the old one. */
    fun rename(typed: String) = edit { editing.rename(id, typed) }

    fun tap(sound: Sound, step: Int) = edit { editing.tap(id, sound, step) }

    fun accent(sound: Sound, step: Int) = edit { editing.accent(id, sound, step) }

    fun beginPaint(sound: Sound, step: Int) {
        follow = follow.edited()
        editing.beginPaint(id, sound, step)
        isPainting = true
    }

    fun paint(sound: Sound, step: Int) {
        editing.paint(id, sound, step)
        model.pattern(id)?.let { _pattern.value = it }
    }

    fun endPaint() {
        editing.endPaint()
        isPainting = false
        refresh()
    }

    private inline fun edit(change: () -> Unit) {
        follow = follow.edited()
        change()
        refresh()
    }

    private fun updateWindow() {
        _window.value = windowFor(_pattern.value)
        firstBar = _window.value.firstBar
    }

    private fun windowFor(pattern: DrumPattern) = BarWindow.of(pattern.meter, pattern.bars, firstBar)
}
