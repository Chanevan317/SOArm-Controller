package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One "title + explanation" entry in a mode's help popup. */
data class Guideline(val title: String, val detail: String)

/**
 * Help popup: each guideline is an orange bullet + emphasised title, with the
 * explanation on the line below.
 */
@Composable
fun GuidelinesDialog(
    title: String,
    items: List<Guideline>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { i, g ->
                    if (i > 0) Spacer(Modifier.height(18.dp))
                    GuidelineRow(g)
                }
            }
        },
    )
}

@Composable
private fun GuidelineRow(g: Guideline) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                g.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            g.detail,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp),
        )
    }
}
