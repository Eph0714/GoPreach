package com.emfitsolutions.gopreach.ui.theme

import androidx.compose.ui.graphics.Color

// GoPreach brand palette — light theme: Purple + White, per explicit user
// request ("GoPreach App: Purple Brand Logo and Purple/White Theme"). Hue
// re-matched to a muted violet swatch supplied directly by the user
// (previously a brighter, more saturated purple).
val PrimaryPurple = Color(0xFF5F4B8B)
val PrimaryPurpleDark = Color(0xFF453569)
val PrimaryPurpleLight = Color(0xFF8571B3)
// A distinct, still-purple secondary tone (not an unrelated accent color —
// spec §9: "do not leave old green/blue/yellow accents") for links/secondary
// emphasis, kept visibly different from the primary purple for hierarchy.
val SecondaryPurple = Color(0xFF9A8BC4)

val SurfaceLight = Color(0xFFFFFFFF) // white primary background, per spec §2/§11
val SurfaceContainerLight = Color(0xFFF6F1FA) // faint purple-tinted neutral, for card/section hierarchy only
val OutlineLight = Color(0xFFD8CCE6)
val OnSurfaceLight = Color(0xFF1C1424)

// GoPreach brand palette — dark theme: Purple + #121212, per explicit user
// request. #121212 is Android's own recommended dark-theme baseline
// background (Material Design dark theme spec) — used verbatim, not
// approximated.
val PrimaryPurpleBright = Color(0xFFA594D1) // lighter tint of the same violet, for contrast on #121212
val PrimaryPurpleContainerDark = Color(0xFF3D2F5C)
val DarkBackground121212 = Color(0xFF121212)
val SecondaryPurpleDark = Color(0xFFC4B8E0)

val SurfaceDark = Color(0xFF121212) // #121212, per explicit request
val SurfaceContainerDark = Color(0xFF1E1E1E) // slightly lifted, for card/section hierarchy on #121212
val OutlineDark = Color(0xFF4A3F58)
val OnSurfaceDark = Color(0xFFFFFFFF) // white text on dark theme

val StatusPending = Color(0xFFE0A526) // "pending sync" indicator — a genuine status color, kept (spec §9's exception)
val StatusSynced = Color(0xFF2E9E5B)
val StatusError = Color(0xFFC63737)
