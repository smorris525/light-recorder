package com.stephenmorris.recorder.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.stephenmorris.recorder.core.HoldAccelerator
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An arrow in a 56 dp target that steps by one on press. With [repeats], holding it repeats as [HoldAccelerator]
 * says. Hidden but keeping its space when not [visible], so the number beside it doesn't move.
 */
@Composable
fun StepArrow(icon: LightIconConfiguration, label: String, visible: Boolean, repeats: Boolean, onStep: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(56.dp)
            .then(
                if (!visible) {
                    Modifier
                } else {
                    Modifier.pointerInput(repeats) {
                        awaitEachGesture {
                            awaitFirstDown()
                            step(1)
                            val repeat = if (!repeats) {
                                null
                            } else {
                                scope.launch {
                                    delay(HoldAccelerator.INITIAL_DELAY_MS)
                                    var count = 1
                                    while (true) {
                                        val next = HoldAccelerator.repeatStep(count)
                                        step(next.size)
                                        delay(next.delayMs)
                                        count += 1
                                    }
                                }
                            }
                            waitForUpOrCancellation()
                            repeat?.cancel()
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            // The SDK draws ⌄ and ⌃ as one chevron rotated about the middle of its 30-unit box, but the chevron's
            // own middle sits 5.44 units off it, so ⌄ draws low and ⌃ high. Shift each back to the middle.
            val shift = ICON_SIZE.gridUnitsAsDp() * (5.44f / 30f)
            val offset = when (icon) {
                LightIcons.DOWN -> -shift
                LightIcons.UP -> shift
                else -> 0.dp
            }
            LightIcon(icon, size = ICON_SIZE, contentDescription = label, modifier = Modifier.offset(y = offset))
        }
    }
}

/** The arrows' size, in grid units. */
private const val ICON_SIZE = 2.5f
