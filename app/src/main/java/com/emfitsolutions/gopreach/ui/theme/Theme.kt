package com.emfitsolutions.gopreach.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = PrimaryPurpleLight,
    onPrimaryContainer = Color.White,
    secondary = SecondaryPurple,
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = StatusError,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryPurpleBright,
    onPrimary = Color.Black,
    primaryContainer = PrimaryPurpleContainerDark,
    onPrimaryContainer = Color.White,
    secondary = SecondaryPurpleDark,
    onSecondary = Color.Black,
    background = DarkBackground121212,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = StatusError,
    onError = Color.White,
)

/**
 * GoPreach Material 3 theme. Uses dynamic color on Android 12+ where available,
 * falling back to the fixed brand palette otherwise, and follows the system
 * light/dark setting.
 */
@Composable
fun GoPreachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GoPreachTypography,
        content = content,
    )
}
