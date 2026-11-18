package com.stephenmorris.recorder.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.designVerticalPxToSp

/**
 * A theme style at the screen's size. The theme's sizes are design pixels, which `LightText` scales for the screen's
 * height; text drawn by hand (the ruler, CLIP, the notes editor) needs the same. The SDK's own helper is internal.
 */
@Composable
fun TextStyle.scaled(): TextStyle = copy(
    fontSize = fontSize.scaled(),
    lineHeight = lineHeight.scaled(),
    letterSpacing = letterSpacing.scaled(),
)

@Composable
private fun TextUnit.scaled(): TextUnit = if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
