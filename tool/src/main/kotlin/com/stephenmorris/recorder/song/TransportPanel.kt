package com.stephenmorris.recorder.song

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.platform.Recorder

/** The room above the ruler, and between the ruler and the lane (spec §8). */
private val ABOVE_RULER = 18.dp
private val RULER_TO_LANE = 5.dp
private val RULER = 28.dp

/**
 * The transport (spec §8), the same under Song, Track, Input and Patterns: the ruler, the lane right under it, then
 * the bar. The lane's touches reach into the room above and below it. The screen stays on while playing or recording.
 */
@Composable
fun TransportPanel(ready: Recorder.Ready, modifier: Modifier = Modifier) {
    val isPlaying by ready.transport.isPlaying.collectAsState()
    val take by ready.transport.take.collectAsState()
    val patternTake by ready.transport.patternTake.collectAsState()
    val isBusy = isPlaying || take != null || patternTake?.records == true
    Box(modifier = modifier.fillMaxWidth().then(if (isBusy) Modifier.keepScreenOn() else Modifier)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(ABOVE_RULER))
            Ruler(ready)
            Spacer(Modifier.height(RULER_TO_LANE))
            Lane(ready)
            TransportBar(ready)
        }
        LaneTouchArea(
            ready,
            Modifier
                .offset(y = ABOVE_RULER + RULER + RULER_TO_LANE - LANE_REACH_ABOVE)
                .fillMaxWidth()
                .height(LANE_REACH_ABOVE + LANE_HEIGHT + LANE_REACH_BELOW),
        )
    }
}
