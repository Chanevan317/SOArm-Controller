package com.example.soarmcontroller.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Single-axis vertical control for gripper height (Z). Drag the knob up or down;
 * it reports -1f..1f via [onMove] (up positive) and springs back to centre on
 * release. Inert when [enabled] is false.
 */
@Composable
fun VerticalJog(
    onMove: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }

    BoxWithConstraints(modifier) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val knobSize = maxWidth * 0.84f
        val knobRadiusPx = with(density) { knobSize.toPx() } / 2f
        val pad = with(density) { 4.dp.toPx() }
        val maxTravel = (trackHeightPx / 2f) - knobRadiusPx - pad

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(percent = 50),
                )
                .pointerInput(enabled, maxTravel) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { knob.animateTo(0f) }
                            onMove(0f)
                        },
                        onDragCancel = {
                            scope.launch { knob.animateTo(0f) }
                            onMove(0f)
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val next = (knob.value + delta.y).coerceIn(-maxTravel, maxTravel)
                            scope.launch { knob.snapTo(next) }
                            onMove(if (maxTravel > 0f) -next / maxTravel else 0f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, knob.value.roundToInt()) }
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}
