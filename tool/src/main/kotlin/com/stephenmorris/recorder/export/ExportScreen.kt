package com.stephenmorris.recorder.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.engine.ExportName
import com.stephenmorris.recorder.engine.ExportProgress
import com.stephenmorris.recorder.platform.M4aEncoder
import com.stephenmorris.recorder.platform.Recorder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.ui.ScreenFrame
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where an export is. */
sealed interface ExportState {
    data class Exporting(val percent: Int) : ExportState

    data object Done : ExportState

    data object Failed : ExportState
}

/**
 * Export's view model (spec §13): mixes the song down and encodes it to a `.m4a` in the tool's own files, then copies
 * it into the shared `exports` folder Tool Manager lists. Leaving the screen cancels it: the encoder stops, and the
 * file is deleted. A finished file is copied whole, so Exports never holds half of one.
 */
class ExportViewModel(context: SealedLightContext) : RecorderViewModel<ExportScreen.Exit?>(context) {
    private val temp = File(context.filesDir, "Export/export.m4a")
    private val fileShare = context.fileShare

    private val _state = MutableStateFlow<ExportState>(ExportState.Exporting(0))
    val state: StateFlow<ExportState> = _state.asStateFlow()

    init {
        viewModelScope.launch { export(ready.filterNotNull().first()) }
    }

    /** Reports may come from a background thread; the update is atomic. */
    private fun report(fraction: Double) = _state.update { state ->
        if (state is ExportState.Exporting) ExportState.Exporting(ExportProgress.shown(state.percent, fraction)) else state
    }

    private suspend fun export(ready: Recorder.Ready) {
        val name = "exports/${ExportName.fileName(ready.model.song.name)}.m4a"
        val job = ready.measurer.mixdown(::report)
        if (job == null) {
            if (currentCoroutineContext().isActive) {
                _state.value = ExportState.Failed
            }
            return
        }
        val written = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            try {
                M4aEncoder.write(job, temp, isCancelled = { !context.isActive }, progress = ::report) &&
                    context.isActive &&
                    runCatching { fileShare.writeStream(name) { out -> temp.inputStream().use { it.copyTo(out) } } }.isSuccess
            } finally {
                temp.delete()
            }
        }
        if (currentCoroutineContext().isActive) {
            _state.value = if (written) ExportState.Done else ExportState.Failed
        }
    }
}

/**
 * Export (spec §13): EXPORTING and a percentage while the song renders, with ✕ to cancel; then where to find the file.
 * A failure goes back to the song's Actions.
 */
class ExportScreen(sealedActivity: SealedLightActivity) : LightScreen<ExportScreen.Exit?, ExportViewModel>(sealedActivity) {

    /** The file is in Exports: back to the song. */
    enum class Exit { DONE }

    override val viewModelClass: Class<ExportViewModel>
        get() = ExportViewModel::class.java

    override fun createViewModel() = ExportViewModel(lightContext)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        when (val current = state) {
            is ExportState.Exporting -> ScreenFrame {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LightText("EXPORTING", LightTextVariant.Paragraph)
                    LightText("${current.percent}%", LightTextVariant.Heading)
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(LightIcons.CLOSE, onClick = { goBack(null) }, contentDescription = "Cancel"),
                    ),
                )
            }
            ExportState.Done -> ScreenFrame {
                LightFullscreenModal(
                    message = "Exported. Find it in Tool Manager, under Recorder › Exports.",
                    onClose = { goBack(Exit.DONE) },
                )
            }
            ExportState.Failed -> {
                ScreenFrame { }
                LaunchedEffect(Unit) { goBack(null) }
            }
        }
    }
}
