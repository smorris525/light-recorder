package com.stephenmorris.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * [text], underlined while it's the current choice: a 2 dp line as wide as the text, 3 dp below it, as LightOS marks a
 * choice. The SDK's own underline is the font's hairline. As Drums'.
 */
@Composable
fun UnderlinedText(text: String, underlined: Boolean, variant: LightTextVariant = LightTextVariant.Copy) {
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        LightText(text, variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (underlined) LightThemeTokens.colors.content else Color.Transparent),
        )
    }
}
