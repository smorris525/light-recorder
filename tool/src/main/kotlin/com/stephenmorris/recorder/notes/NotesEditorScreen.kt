package com.stephenmorris.recorder.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stephenmorris.recorder.platform.RecorderViewModel
import com.stephenmorris.recorder.ui.ScreenFrame
import com.stephenmorris.recorder.ui.backButton
import com.stephenmorris.recorder.ui.scaled
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.viewmodel.EnQwertyLp3KeyboardViewModel
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.keyboard.LightEmbeddedLp3Keyboard
import kotlinx.coroutines.flow.StateFlow

/**
 * The notes editor's view model: it holds the text, and saves it however the editor goes, BACK or the system back
 * key, which brings no result (spec §12: back saves).
 */
class NotesEditorViewModel(context: SealedLightContext, notes: String) : RecorderViewModel<Unit>(context) {
    val state = TextFieldState(notes, TextRange(notes.length))

    /** Leaving the tool saves what's typed so far, in case the tool isn't come back to. */
    override fun onAppPause() {
        save()
        super.onAppPause()
    }

    override fun onCleared() {
        save()
        super.onCleared()
    }

    private fun save() {
        ready.value?.editing?.setNotes(state.text.toString())
    }
}

/**
 * The notes editor (spec §12): a close copy of `LightTextInputEditor`'s layout, multi-line, with only the text
 * scrolling, so the cursor stays in view. The SDK's editor clips long text, which is why this is a copy. There's no
 * DONE: back saves.
 */
class NotesEditorScreen(sealedActivity: SealedLightActivity, private val notes: String) :
    LightScreen<Unit, NotesEditorViewModel>(sealedActivity) {

    override val viewModelClass: Class<NotesEditorViewModel>
        get() = NotesEditorViewModel::class.java

    override fun createViewModel() = NotesEditorViewModel(lightContext, notes)

    /** The keyboard's view model, in this screen's own store, so it's cleared with the screen. */
    private fun keyboard(callback: NotesKeyboardCallback, options: StateFlow<KeyboardOptions>) =
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EnQwertyLp3KeyboardViewModel<Unit>(
                        callback,
                        keyboardOptionsFlow = options,
                        // As the SDK's editor: no close key on the letters, as there's nothing to close to.
                        optionsForLayout = { LayoutOptions(!it.isRootLayout) },
                    ) as T
            },
        )["NotesKeyboard", EnQwertyLp3KeyboardViewModel::class.java]

    @Composable
    override fun Content() {
        val state = viewModel.state
        val haptics = LocalHapticFeedback.current
        val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
        val options = rememberKeyboardOptions()
        val keyboard = remember {
            keyboard(
                NotesKeyboardCallback(state) {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                },
                options,
            )
        }
        val colors = LightThemeTokens.colors
        val style = LightThemeTokens.typography.heading.scaled().copy(color = colors.content)
        val underline = 3f.designVerticalPxToDp()
        val scroll = rememberScrollState()
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        var viewport by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current

        // Keeps the cursor in view as it moves or the text grows.
        LaunchedEffect(state.selection, layout, viewport) {
            val text = layout ?: return@LaunchedEffect
            if (viewport == 0) return@LaunchedEffect
            val rect = text.getCursorRect(state.selection.min.coerceIn(0, text.layoutInput.text.length))
            val margin = with(density) { 8.dp.toPx() }
            when {
                rect.bottom + margin > scroll.value + viewport -> scroll.scrollTo((rect.bottom + margin - viewport).toInt())
                rect.top < scroll.value -> scroll.scrollTo(rect.top.toInt())
            }
        }

        ScreenFrame {
            LightTopBar(
                leftButton = backButton { goBack() },
                center = LightTopBarCenter.Text("Notes"),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2f.gridUnitsAsDp())
                    .onSizeChanged { viewport = it.height }
                    .verticalScroll(scroll),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = state.text.toString(),
                        style = style,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    layout?.let { text ->
                                        state.edit { selection = TextRange(text.getOffsetForPosition(down.position)) }
                                    }
                                }
                            },
                    )
                    Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
                    Spacer(Modifier.fillMaxWidth().height(underline).background(colors.content))
                }
                layout?.let { text ->
                    val rect = text.getCursorRect(state.selection.min.coerceIn(0, text.layoutInput.text.length))
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                            .width(2.dp)
                            .height(with(density) { rect.height.toDp() })
                            .background(colors.content),
                    )
                }
            }
            LightEmbeddedLp3Keyboard(
                viewModel = keyboard,
                additionalBottomHeight = 5f.gridUnitsAsDp(),
                bottomBar = { LightBottomBar(items = emptyList()) },
            )
        }
    }
}
