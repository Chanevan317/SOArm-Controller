package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Two actions that read as one control: a single bordered pill split down the
 * middle, each half an independent tap target.
 */
@Composable
fun SplitAction(
    left: Pair<String, () -> Unit>,
    right: Pair<String, () -> Unit>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Half(left.first, left.second, enabled, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Half(right.first, right.second, enabled, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Half(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
