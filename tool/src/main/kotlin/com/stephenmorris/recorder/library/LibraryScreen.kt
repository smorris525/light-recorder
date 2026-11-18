package com.stephenmorris.recorder.library

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.recorder.engine.ExportName
import com.stephenmorris.recorder.platform.M4aEncoder
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.TimeText
import com.stephenmorris.recorder.tape.uniqueName
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The library's view model. Until the library has its songs, it drives a temporary engine check: a new song, arming, ●, ▶/■ and Export, with a status line.
 */
class LibraryViewModel(private val context: SealedLightContext) : RecorderViewModel<Unit>(context) {
    val message = MutableStateFlow("")

    fun newSong() {
        val ready = ready.value ?: return
        val model = ready.model
        val song = Song.new(uniqueName("New Song", model.songs.value.map { it.name }))
        model.replaceSongs(model.songs.value + song)
        model.open(song.id)
    }

    /** Opens the newest song, if there is one and none is open. */
    fun openLatest() {
        val model = ready.value?.model ?: return
        if (!model.isSongOpen) {
            model.songs.value.lastOrNull()?.let { model.open(it.id) }
        }
    }

    /**
     * Arms Track [number]. A Microphone track whose mic won't open asks for it through the SDK (which needs the Light
     * server); grant it with `adb shell pm grant` on an emulator without one.
     */
    fun arm(number: Int, askForMic: () -> Unit) {
        val ready = ready.value ?: return
        val track = ready.model.song.tracks.getOrNull(number - 1) ?: return
        ready.transport.toggleArm(track.id)
        if (number == 2 && ready.transport.isMicRefused.value) {
            message.value = "microphone not allowed: asking"
            askForMic()
        }
    }

    fun record() = ready.value?.transport?.record()

    fun togglePlay() = ready.value?.transport?.togglePlay()

    fun export() {
        val ready = ready.value ?: return
        viewModelScope.launch {
            message.value = "exporting"
            val job = ready.measurer.mixdown { } ?: run {
                message.value = "nothing to export"
                return@launch
            }
            val name = "exports/${ExportName.fileName(ready.model.song.name)}.m4a"
            val done = withContext(Dispatchers.IO) {
                val temp = File(context.filesDir, "Export/export.m4a")
                val written = M4aEncoder.write(job, temp, isCancelled = { false }, progress = { })
                if (written) {
                    context.fileShare.writeStream(name) { out -> temp.inputStream().use { it.copyTo(out) } }
                    temp.delete()
                }
                written
            }
            message.value = if (done) "exported $name" else "export failed"
        }
    }

    /** The playhead, the count, the mic's level and each track's content, for the status line. */
    fun status(): String {
        val ready = ready.value ?: return "starting"
        val model = ready.model
        if (!model.isSongOpen) {
            return "no song open (${model.songs.value.size} saved)"
        }
        val transport = ready.transport
        val tracks = model.song.tracks.joinToString("  ") { track ->
            val armed = if (model.arming.value.isArmed(track.id)) "●" else ""
            "${track.number}$armed:${track.spans.size}s/${track.hits.size}h"
        }
        val count = transport.countNumber.value?.let { " count $it" } ?: ""
        val level = (transport.inputLevel() * 100).toInt()
        return "${model.song.name} ${TimeText.readout(model.playhead.value)}$count in $level%\n$tracks"
    }
}

/** The first screen: the song library. For now, an engine check. */
@InitialScreen
class LibraryScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel(): LibraryViewModel = LibraryViewModel(lightContext)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val message by viewModel.message.collectAsState()
        val launcher = rememberPermissionRequestLauncher(Manifest.permission.RECORD_AUDIO)
        var status by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                status = viewModel.status()
                delay(100)
            }
        }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(16.dp),
            ) {
                Button("NEW SONG") { viewModel.newSong() }
                Button("OPEN LATEST") { viewModel.openLatest() }
                Button("ARM 2 (MIC)") { viewModel.arm(2) { launcher?.launch() } }
                Button("ARM 1 (DRUMS)") { viewModel.arm(1) { } }
                Button("● RECORD") { viewModel.record() }
                Button("▶ / ■") { viewModel.togglePlay() }
                Button("EXPORT") { viewModel.export() }
                LightText(text = status, variant = LightTextVariant.Detail)
                LightText(text = message, variant = LightTextVariant.Detail)
            }
        }
    }

    @Composable
    private fun Button(label: String, onClick: () -> Unit) {
        LightText(
            text = label,
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onClick)
                .padding(vertical = 6.dp),
        )
    }
}
