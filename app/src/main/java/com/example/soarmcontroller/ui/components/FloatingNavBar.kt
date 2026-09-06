package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.ui.navigation.Destination

/**
 * Pill-shaped floating navigation bar. Sits in the Scaffold's bottomBar slot so
 * the content area still reserves space for it; the "floating" is visual only —
 * inset margins, rounded corners and a 1dp border.
 */
@Composable
fun FloatingNavBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Destination.entries.forEach { dest ->
                    NavItem(
                        dest = dest,
                        selected = dest == current,
                        onClick = { onSelect(dest) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    dest: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(dest.icon, contentDescription = dest.label, tint = tint)
        Text(
            dest.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
