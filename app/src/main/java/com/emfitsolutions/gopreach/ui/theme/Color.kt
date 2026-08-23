package com.emfitsolutions.gopreach.ui.theme

import androidx.compose.ui.graphics.Color

// GoPreach brand palette — light theme: Dark Green (standard "DarkGreen"
// web color, #006400), per explicit user request (previously Forest Green).
val PrimaryGreen = Color(0xFF006400)
val PrimaryGreenDark = Color(0xFF003D00)
val PrimaryGreenLight = Color(0xFF2E7D32)
val AccentGold = Color(0xFFE0A526)

val SurfaceLight = Color(0xFFFAFAFC)
val SurfaceContainerLight = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFFC4C8D0)
val OnSurfaceLight = Color(0xFF1A1C1E)

// GoPreach brand palette — dark theme: "Gray Purple" primary on a "Graphite
// Black" background, white text, per explicit user request (a deliberately
// different identity from the light theme's green, not a dimmed copy of it —
// same two-distinct-theme approach the app has used since Phase 0).
val PrimaryGrayPurple = Color(0xFF8A7CA8)
val PrimaryGrayPurpleContainer = Color(0xFF4E4460)
val GraphiteBlackDeep = Color(0xFF121212)
val AccentGoldDark = Color(0xFFF0C05A)

val SurfaceDark = Color(0xFF1A1A1C) // Graphite Black
val SurfaceContainerDark = Color(0xFF242426)
val OutlineDark = Color(0xFF56505E)
val OnSurfaceDark = Color(0xFFFFFFFF) // white text on dark theme, per explicit request

val StatusPending = Color(0xFFE0A526) // "pending sync" indicator
val StatusSynced = Color(0xFF2E9E5B)
val StatusError = Color(0xFFC63737)

/**
 * The Main Form's "Professional Green Icon-Based Redesign" spec: every
 * navigation/action icon in the dashboard grid ([DashboardTile]) stays this
 * exact green regardless of light/dark mode or dynamic color — a deliberate,
 * fixed constant, not `MaterialTheme.colorScheme.primary` (which is Dark
 * Green in light mode but Gray Purple in dark mode, per an earlier, separate
 * request about the app's *overall* theme color). Chosen for contrast on
 * both this app's light (near-white) and dark (Graphite Black) surfaces
 * alike, since one fixed value has to work on both.
 */
val IconAccentGreen = Color(0xFF2E7D32)
