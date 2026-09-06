package com.example.soarmcontroller.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four control modes, surfaced as bottom-navigation tabs.
 *
 * 1. JOG      – jog the gripper through space with on-screen joysticks
 * 2. GO_TO    – type an X/Y/Z target; the arm moves to reach it
 * 3. SEQUENCE – hand-guide the arm, capture poses, replay the sequence
 * 4. VOICE    – keyword recognition drives the primitives of the other modes
 */
enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    JOG("Jog", Icons.Filled.Gamepad),
    GO_TO("Go To", Icons.Filled.MyLocation),
    SEQUENCE("Sequence", Icons.Filled.Route),
    VOICE("Voice", Icons.Filled.Mic),
}
