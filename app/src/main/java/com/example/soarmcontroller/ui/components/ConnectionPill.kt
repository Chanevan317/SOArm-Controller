package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.ui.connection.LinkStatus
import com.example.soarmcontroller.ui.theme.StatusBusy
import com.example.soarmcontroller.ui.theme.StatusError
import com.example.soarmcontroller.ui.theme.StatusOff
import com.example.soarmcontroller.ui.theme.StatusOk

/**
 * Compact status pill shown in the top bar. Tapping it opens the connection sheet.
 */
@Composable
fun ConnectionPill(
    status: LinkStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dot, label) = when (status) {
        LinkStatus.DISCONNECTED -> StatusOff to "Offline"
        LinkStatus.CONNECTING -> StatusBusy to "Linking"
        LinkStatus.CONNECTED -> StatusOk to "Linked"
        LinkStatus.ERROR -> StatusError to "Error"
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Dot(dot)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Spacer(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
