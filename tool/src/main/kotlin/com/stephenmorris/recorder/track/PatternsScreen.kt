package com.stephenmorris.recorder.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.core.PatternText
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.song.TransportPanel
import com.stephenmorris.recorder.song.asGridUnits
import com.stephenmorris.recorder.tape.DrumPattern
import com.stephenmorris.recorder.ui.ROW_INSET
import com.stephenmorris.recorder.ui.SCREEN_MARGIN
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.UnderlinedText
import com.stephenmorris.recorder.ui.backButton
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable

/**
 * A Drums track's patterns (spec §9), over the transport panel: the song's patterns and
 * ADD PATTERN. A tap selects a pattern and stays on the page, as during a take it's where the next one is queued.
 */
class PatternsScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, PanelViewModel>(sealedActivity) {

    override val viewModelClass: Class<PanelViewModel>
        get() = PanelViewModel::class.java

    override fun createViewModel() = PanelViewModel(lightContext)

    @Composable
    override fun Content() {
        val ready by viewModel.ready.collectAsState()
        ScreenFrame {
            ready?.let { Patterns(it) }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.ColumnScope.Patterns(ready: Recorder.Ready) {
        ready.model.songs.collectAsState().value
        val patternTake by ready.transport.patternTake.collectAsState()
        val song = ready.model.song
        LightTopBar(leftButton = backButton { goBack() }, center = LightTopBarCenter.Text("Patterns"))
        LightScrollView(
            scrollState = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ROW_INSET, end = SCREEN_MARGIN, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                for (pattern in song.patterns) {
                    PatternRow(
                        pattern = pattern,
                        isSelected = song.selectedPattern == pattern.id,
                        isQueued = patternTake?.records == true && patternTake?.queued == pattern.id,
                        onSelect = { ready.transport.select(pattern.id) },
                        // The pattern's screens open here later.
                        onOpen = { },
                    )
                }
                LightText(
                    "ADD PATTERN",
                    LightTextVariant.Button,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .lightClickable(onClickLabel = "Add pattern", onClick = ready.editing::addPattern)
                        .padding(vertical = 12.dp),
                )
            }
        }
        TransportPanel(ready)
    }
}

/**
 * A pattern: its name, underlined while selected and dotted while queued in a take, over its tempo, time signature
 * and length. Only the selected one has the pencil, so a tap on a row only ever selects.
 */
@Composable
private fun PatternRow(
    pattern: DrumPattern,
    isSelected: Boolean,
    isQueued: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier.weight(1f).lightClickable(onClickLabel = pattern.name, onClick = onSelect),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UnderlinedText(pattern.name, isSelected, LightTextVariant.Paragraph)
                if (isQueued) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LightThemeTokens.colors.content))
                }
            }
            LightText(
                PatternText.libraryMeta(pattern.bpm, pattern.meter, pattern.bars),
                LightTextVariant.Detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    .lightClickable(onClickLabel = "Edit ${pattern.name}", onClick = onOpen),
                contentAlignment = Alignment.CenterEnd,
            ) {
                LightIcon(LightIcons.PENCIL, size = 24.dp.asGridUnits(), contentDescription = null)
            }
        } else {
            Spacer(Modifier.height(0.dp))
        }
    }
}
