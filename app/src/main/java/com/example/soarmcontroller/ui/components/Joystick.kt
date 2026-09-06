package com.example.soarmcontroller.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Circular analog joystick. The caller sizes it via [modifier] — pass a square
 * constraint (e.g. `Modifier.fillMaxWidth().aspectRatio(1f)`) so the track stays
 * a perfect circle. Reports the knob position via [onMove], each axis normalised
 * to -1f..1f (y positive = up / forward), and springs back to centre on release.
 * Inert when [enabled] is false.
 */
@Composable
fun Joystick(
    onMove: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        val sidePx = with(density) { side.toPx() }
        val knobSide = side * 0.34f
        val knobRadiusPx = with(density) { knobSide.toPx() } / 2f
        val maxTravel = (sidePx / 2f) - knobRadiusPx

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                .pointerInput(enabled, maxTravel) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { knob.animateTo(Offset.Zero) }
                            onMove(0f, 0f)
                        },
                        onDragCancel = {
                            scope.launch { knob.animateTo(Offset.Zero) }
                            onMove(0f, 0f)
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val next = knob.value + delta
                            val dist = next.getDistance()
                            val clamped = if (dist > maxTravel && dist > 0f) {
                                next / dist * maxTravel
                            } else {
                                next
                            }
                            scope.launch { knob.snapTo(clamped) }
                            onMove(clamped.x / maxTravel, -clamped.y / maxTravel)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(knob.value.x.roundToInt(), knob.value.y.roundToInt()) }
                    .size(knobSide)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}
