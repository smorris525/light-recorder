package com.stephenmorris.recorder.engine

import com.stephenmorris.recorder.tape.RulerScale
import com.stephenmorris.recorder.tape.TapeView
import com.stephenmorris.recorder.tape.roundHalfAway
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the ruler, the lane and the bar's brackets do to the tape: scrubbing, the in and out
 * points, and scrolling. An `x` and `width` are along the tape on show: from where it starts, across the width
 * it fills (`TapeFrame.inner`). Main thread only.
 */
class TapeControls(private val model: SongModel, private val transport: Transport) {
    /**
     * A scrub under way. [ticksCrossed] counts the ruler's ticks it has passed or landed on, for the haptics; the last
     * of them was labelled when [isMajor].
     */
    data class Scrub(val position: Double, val ticksCrossed: Int, val isMajor: Boolean)

    private val _scrub = MutableStateFlow<Scrub?>(null)

    /** The scrub under way, while a finger moves the playhead. */
    val scrub: StateFlow<Scrub?> = _scrub.asStateFlow()

    /**
     * A finger at [x] across a ruler or lane [width] wide: moves the playhead to the nearest hundredth of a second
     * there, within the song, counting each tick it crosses. Not during a take, when the playhead stays put.
     */
    fun scrub(x: Double, width: Double) {
        if (transport.isTakeUnderWay) {
            return
        }
        model.isScrubbingPlayhead = true
        val song = model.song
        val view = song.view
        val scale = RulerScale.forView(view.secondsAcross, width)
        val time = roundHalfAway(view.time(x, width) / SCRUB_STEP) * SCRUB_STEP
        val previous = _scrub.value?.position ?: model.playhead.value
        val moved = time.coerceIn(0.0, song.length)
        model.playhead.value = moved
        // The tick nearest `moved` on the way from `previous`, if the move reached one. A hair's tolerance keeps a step
        // that lands on a tick, as 3.00, from missing it by rounding.
        val tolerance = SCRUB_STEP / 100
        val crossed = when {
            moved > previous -> (floor((moved + tolerance) / scale.minor) * scale.minor).takeIf { it > previous + tolerance }
            moved < previous -> (ceil((moved - tolerance) / scale.minor) * scale.minor).takeIf { it < previous - tolerance }
            else -> null
        }
        val current = _scrub.value ?: Scrub(moved, 0, false)
        _scrub.value = if (crossed != null) {
            val major = roundHalfAway(crossed / scale.major) * scale.major
            Scrub(moved, current.ticksCrossed + 1, abs(crossed - major) < scale.minor / 2)
        } else {
            current.copy(position = moved)
        }
    }

    /** The finger lifts: while playing, playback goes on from where the playhead was left. */
    fun endScrub() {
        _scrub.value = null
        if (model.isScrubbingPlayhead) {
            model.isScrubbingPlayhead = false
            transport.seek()
        }
    }

    /** The playhead a tick later (or earlier for negative [ticks]), on the ruler [width] wide, paging the view to it. */
    fun stepPlayhead(ticks: Int, width: Double) {
        if (transport.isTakeUnderWay) {
            return
        }
        val song = model.song
        val scale = RulerScale.forView(song.view.secondsAcross, width)
        model.playhead.value = (scale.snap(model.playhead.value) + ticks * scale.minor).coerceIn(0.0, song.length)
        model.updateSong { it.copy(view = it.view.following(model.playhead.value, it.length)) }
        transport.seek()
    }

    /** [ : the in point at the playhead, where that's before the out point. Neither seeks nor stops playback. */
    fun setIn() = unlessTaking { model.updateSong { it.settingIn(model.playhead.value) } }

    /** ] : the out point at the playhead, where that's after the in point. */
    fun setOut() = unlessTaking { model.updateSong { it.settingOut(model.playhead.value) } }

    fun clearIn() = unlessTaking { model.updateSong { it.clearingIn() } }

    fun clearOut() = unlessTaking { model.updateSong { it.clearingOut() } }

    /** Moved later by [dx] across [width] (earlier for negative), by hand: the view stops following the playhead. */
    fun scroll(dx: Double, width: Double) {
        model.isFollowing = false
        model.updateSong { it.copy(view = it.view.scrolled(dx, width, it.length)) }
    }

    /** A tap on the ruler's marker: the view follows the playhead again. */
    fun followAgain() {
        model.isFollowing = true
    }

    private inline fun unlessTaking(action: () -> Unit) {
        if (!transport.isTakeUnderWay) {
            action()
        }
    }

    companion object {
        /** The playhead scrubs to hundredths of a second, finer than the ruler's ticks. */
        const val SCRUB_STEP = 0.01
    }
}
