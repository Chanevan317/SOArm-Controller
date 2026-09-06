package com.example.soarmcontroller.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Row of equaliser-style bars. When [active] they pulse at staggered rates;
 * otherwise they rest at a thin idle line. Purely decorative feedback for the
 * voice "listening" state.
 */
@Composable
fun LevelBars(
    active: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 7,
) {
    val transition = rememberInfiniteTransition(label = "level-bars")
    val phases = List(barCount) { i ->
        transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 360 + i * 90),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar-$i",
        )
    }

    Row(
        modifier = modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        phases.forEach { phase ->
            val fraction = if (active) phase.value else 0.12f
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
