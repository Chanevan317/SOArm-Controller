package com.example.soarmcontroller.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SOArm Controller palette.
 *
 * Design language: clean / minimalist, inspired by ColorOS 16 system apps.
 * - Light: soft grey ground (#F5F5F5) with white elevated surfaces.
 * - Dark: true black ground (#000000) with #151515 surfaces / cards.
 * - Accent: orange, echoing the SO-100 / SO-ARM100 hardware colour. Used sparingly.
 */

// --- Accent (shared) -------------------------------------------------------
val AccentOrange = Color(0xFFFF6A2C)
val AccentOrangeDark = Color(0xFFFF7C45) // slightly lifted for contrast on pure black
val OnAccent = Color(0xFFFFFFFF)

// --- Light ---------------------------------------------------------------
val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFECECEC)
val LightOn = Color(0xFF1A1A1A)
val LightOnMuted = Color(0xFF5C5C5C)
val LightOutline = Color(0xFFDDDDDD)

// --- Dark (full black ground) -----------------------------------------
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF151515) // cards + floating nav bar
val DarkSurfaceVariant = Color(0xFF202020)
val DarkOn = Color(0xFFF5F5F5)
val DarkOnMuted = Color(0xFF9A9A9A)
val DarkOutline = Color(0xFF333333)

// --- Status signalling (shared) ---------------------------------------
val StatusOk = Color(0xFF3DBE6B)
val StatusBusy = Color(0xFFF2A83B)
val StatusOff = Color(0xFF8A8A8A)
val StatusError = Color(0xFFE0533D)
