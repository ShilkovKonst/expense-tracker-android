package com.spendobserver.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SpendObserverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.withShiftedSurfaces(if (darkTheme) DarkSurfaceLift else LightSurfaceDim)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
// How far every background and surface is moved off the scheme's own: the dark theme's
// is close to black, the light theme's close to white. Neutral overlays, so the wallpaper's
// hue in a dynamic scheme stays as it is.
private val DarkSurfaceLift = Color.White.copy(alpha = 0.05f)
private val LightSurfaceDim = Color.Black.copy(alpha = 0.035f)

/** All background and surface roles moved by the same [overlay], so they keep their steps between each other. */
private fun ColorScheme.withShiftedSurfaces(overlay: Color): ColorScheme {
    fun Color.shifted() = overlay.compositeOver(this)
    return copy(
        background = background.shifted(),
        surface = surface.shifted(),
        surfaceDim = surfaceDim.shifted(),
        surfaceBright = surfaceBright.shifted(),
        surfaceContainerLowest = surfaceContainerLowest.shifted(),
        surfaceContainerLow = surfaceContainerLow.shifted(),
        surfaceContainer = surfaceContainer.shifted(),
        surfaceContainerHigh = surfaceContainerHigh.shifted(),
        surfaceContainerHighest = surfaceContainerHighest.shifted(),
    )
}
