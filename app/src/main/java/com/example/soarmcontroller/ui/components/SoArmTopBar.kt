package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.ui.connection.LinkStatus

/**
 * Top bar: app name on the left; connection pill + overflow menu on the right.
 * The overflow menu holds the theme toggle, a link to the source repo, and About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoArmTopBar(
    linkStatus: LinkStatus,
    resolvedDark: Boolean,
    onConnectionClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("SOArm Controller", style = MaterialTheme.typography.titleLarge) },
        actions = {
            ConnectionPill(status = linkStatus, onClick = onConnectionClick)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (resolvedDark) "Light mode" else "Dark mode") },
                    leadingIcon = {
                        Icon(
                            if (resolvedDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleTheme()
                    },
                )
                DropdownMenuItem(
                    text = { Text("View source") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onOpenSource()
                    },
                )
                DropdownMenuItem(
                    text = { Text("About") },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenAbout()
                    },
                )
            }
            Spacer(Modifier.width(4.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}
