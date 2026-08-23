package com.emfitsolutions.gopreach.ui.theme

import androidx.compose.ui.graphics.Color

// GoPreach brand palette — light theme: Purple + White, per explicit user
// request ("GoPreach App: Purple Brand Logo and Purple/White Theme").
// Replaces the earlier Dark Green light theme.
val PrimaryPurple = Color(0xFF6A1B9A)
val PrimaryPurpleDark = Color(0xFF4A148C)
val PrimaryPurpleLight = Color(0xFF9C4DCC)
// A distinct, still-purple secondary tone (not an unrelated accent color —
// spec §9: "do not leave old green/blue/yellow accents") for links/secondary
// emphasis, kept visibly different from the primary purple for hierarchy.
val SecondaryPurple = Color(0xFF9575CD)

val SurfaceLight = Color(0xFFFFFFFF) // white primary background, per spec §2/§11
val SurfaceContainerLight = Color(0xFFF6F1FA) // faint purple-tinted neutral, for card/section hierarchy only
val OutlineLight = Color(0xFFD8CCE6)
val OnSurfaceLight = Color(0xFF1C1424)

// GoPreach brand palette — dark theme: Purple + #121212, per explicit user
// request. #121212 is Android's own recommended dark-theme baseline
// background (Material Design dark theme spec) — used verbatim, not
// approximated.
val PrimaryPurpleBright = Color(0xFFBB86FC) // classic high-contrast purple accent for #121212 backgrounds
val PrimaryPurpleContainerDark = Color(0xFF4A148C)
val DarkBackground121212 = Color(0xFF121212)
val SecondaryPurpleDark = Color(0xFFCE93D8)

val SurfaceDark = Color(0xFF121212) // #121212, per explicit request
val SurfaceContainerDark = Color(0xFF1E1E1E) // slightly lifted, for card/section hierarchy on #121212
val OutlineDark = Color(0xFF4A3F58)
val OnSurfaceDark = Color(0xFFFFFFFF) // white text on dark theme

val StatusPending = Color(0xFFE0A526) // "pending sync" indicator — a genuine status color, kept (spec §9's exception)
val StatusSynced = Color(0xFF2E9E5B)
val StatusError = Color(0xFFC63737)
