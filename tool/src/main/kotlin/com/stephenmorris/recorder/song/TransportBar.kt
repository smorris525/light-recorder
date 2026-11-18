package com.stephenmorris.recorder.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.ui.RecorderIcons
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons

/**
 * The panel's bottom bar (spec §8): [ and ] for the in and out points, ● and ▶ / ■. Nothing hides,
 * so nothing moves; what can't be used for now is dimmed: [ or ] where that point can't be set, ● with nothing armed,
 * and both points during a take.
 */
@Composable
fun TransportBar(ready: Recorder.Ready) {
    val model = ready.model
    val transport = ready.transport
    val controls = ready.controls
    model.songs.collectAsState().value
    val song = model.song
    val take by transport.take.collectAsState()
    val patternTake by transport.patternTake.collectAsState()
    val count by transport.countNumber.collectAsState()
    val isPlaying by transport.isPlaying.collectAsState()
    val arming by model.arming.collectAsState()
    val playheadState = model.playhead.collectAsState()
    // Only whether a point can be set redraws the bar as the playhead moves.
    val canSetIn by remember(song) { derivedStateOf { song.canSetIn(playheadState.value) } }
    val canSetOut by remember(song) { derivedStateOf { song.canSetOut(playheadState.value) } }

    val isTakeUnderWay = take != null || patternTake?.records == true
    val isRecording = isTakeUnderWay && count == null
    val units = RecorderIcons.UNDERLINABLE_SIZE_UNITS

    val inSet = song.inPoint != null
    val inEnabled = !isTakeUnderWay && (inSet || canSetIn)
    val inPoint = LightBarButton.Icon(
        RecorderIcons.inPoint(underlined = inSet, dimmed = !inEnabled),
        onClick = { if (inSet) controls.clearIn() else controls.setIn() },
        contentDescription = if (inSet) "Clear in point" else "Set in point",
        sizeUnits = units,
        enabled = inEnabled,
    )
    val outSet = song.outPoint != null
    val outEnabled = !isTakeUnderWay && (outSet || canSetOut)
    val outPoint = LightBarButton.Icon(
        RecorderIcons.outPoint(underlined = outSet, dimmed = !outEnabled),
        onClick = { if (outSet) controls.clearOut() else controls.setOut() },
        contentDescription = if (outSet) "Clear out point" else "Set out point",
        sizeUnits = units,
        enabled = outEnabled,
    )
    val recordEnabled = isTakeUnderWay || !arming.isEmpty
    val record = count?.let { number ->
        LightBarButton.Icon(
            RecorderIcons.countIn(number),
            onClick = transport::record,
            contentDescription = "Stop recording, starting in $number",
            sizeUnits = units,
        )
    } ?: LightBarButton.Icon(
        RecorderIcons.record(underlined = isRecording, dimmed = !recordEnabled),
        onClick = transport::record,
        contentDescription = if (isRecording) "Stop recording" else "Record",
        sizeUnits = units,
        enabled = recordEnabled,
    )
    val play = LightBarButton.LightIcon(
        if (isPlaying) LightIcons.STOP else LightIcons.PLAY,
        onClick = transport::togglePlay,
        contentDescription = if (isPlaying) "Stop" else "Play",
    )
    LightBottomBar(items = listOf(inPoint, outPoint, record, play))
}
