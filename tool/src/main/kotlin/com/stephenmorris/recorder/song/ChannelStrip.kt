package com.stephenmorris.recorder.song

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.panel.SliderMath
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.tape.Track
import com.stephenmorris.recorder.tape.TrackInput
import com.stephenmorris.recorder.ui.RecorderIcons
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.ui.LightGrid
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** The number's and arm's rows; their 44 dp targets reach 4 dp past them. */
private val BUTTON_ROW = 36.dp
private val TARGET = 44.dp

/** The pan's row, drawn 38 dp tall over 30 dp of the strip, and its inset from each side. */
private val PAN_ROW = 38.dp

/** How far the number and input sit below their row's centre. */
private val HEADER_DROP = 6.dp
private val PAN_OVERLAP = 4.dp
private val PAN_INSET = 6.dp

/** [dp] in the SDK's grid units, for its icons. */
@Composable
fun Dp.asGridUnits(): Float = value / (LocalConfiguration.current.screenWidthDp.toFloat() / LightGrid.WIDTH)

/**
 * A track on the song screen as a channel strip (spec §8): its number and input, its pan, its meter
 * and fader side by side, then its arm.
 */
@Composable
fun ChannelStrip(ready: Recorder.Ready, track: Track, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val model = ready.model
    val transport = ready.transport
    val arming by model.arming.collectAsState()
    val take by transport.take.collectAsState()
    val patternTake by transport.patternTake.collectAsState()
    val isTakeUnderWay = take != null || patternTake?.records == true
    val askForMic = rememberPermissionRequestLauncher(Manifest.permission.RECORD_AUDIO)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ButtonRow {
            Row(
                modifier = Modifier
                    // Nearer its pan slider than the top bar.
                    .offset(y = HEADER_DROP)
                    .sizeIn(minWidth = TARGET, minHeight = TARGET)
                    .lightClickable(onClickLabel = "Open ${track.name}", onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LightText("${track.number}", LightTextVariant.Detail)
                if (track.input == TrackInput.MIC) {
                    LightIcon(LightIcons.MICROPHONE, size = 18.dp.asGridUnits(), contentDescription = null)
                } else {
                    Image(RecorderIcons.drums(), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(PAN_ROW - PAN_OVERLAP * 2),
            contentAlignment = Alignment.Center,
        ) {
            LevelSlider(
                value = track.pan.toDouble(),
                range = SliderMath.PAN_RANGE,
                home = 0.0,
                isCentred = true,
                isFilled = false,
                vertical = false,
                onChange = { value -> model.updateTrack(track.id) { it.copy(pan = SliderMath.pan(value)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PAN_INSET).requiredHeight(PAN_ROW),
            )
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
            LevelMeter(ready, track, Modifier.weight(1f).fillMaxHeight())
            LevelSlider(
                value = SliderMath.volumeValue(track),
                range = SliderMath.VOLUME_RANGE,
                home = 0.0,
                isCentred = false,
                isFilled = true,
                vertical = true,
                onChange = { value -> model.updateTrack(track.id) { it.copy(volume = SliderMath.volume(value)) } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        ButtonRow {
            val isArmed = arming.isArmed(track.id)
            Box(
                modifier = Modifier
                    .size(TARGET)
                    .lightClickable(
                        enabled = !isTakeUnderWay,
                        onClickLabel = "${if (isArmed) "Disarm" else "Arm"} ${track.name}",
                    ) {
                        transport.toggleArm(track.id)
                        // A Mic/Line track whose mic won't open asks for it; granted, a second tap arms it.
                        if (track.input == TrackInput.MIC && transport.isMicRefused.value) {
                            askForMic?.launch()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    RecorderIcons.arm(underlined = isArmed, dimmed = isTakeUnderWay),
                    contentDescription = null,
                    modifier = Modifier.size(RecorderIcons.UNDERLINABLE_SIZE_UNITS.gridUnitsAsDp()),
                )
            }
        }
    }
}

/** A 44 dp target centred on the strip, laid over a shorter row. */
@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(BUTTON_ROW), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.requiredHeight(TARGET), contentAlignment = Alignment.Center) { content() }
    }
}
