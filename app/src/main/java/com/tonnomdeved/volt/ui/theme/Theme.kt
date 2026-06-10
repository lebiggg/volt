package com.tonnomdeved.volt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FallbackLight = lightColorScheme(
    primary = VoltLightPrimary,
    onPrimary = VoltLightOnPrimary,
    surface = VoltLightSurface,
    onSurface = VoltLightOnSurface,
    surfaceVariant = VoltLightSurfaceVariant,
    outline = VoltLightOutline
)

private val FallbackDark = darkColorScheme(
    primary = VoltDarkPrimary,
    onPrimary = VoltDarkOnPrimary,
    surface = VoltDarkSurface,
    onSurface = VoltDarkOnSurface,
    surfaceVariant = VoltDarkSurfaceVariant,
    outline = VoltDarkOutline
)

/**
 * Theme racine.
 *
 * - Android 12+ (incl. GrapheneOS sur Pixel 8) → couleurs dynamiques Material You.
 * - Edge-to-edge activé + barre de statut transparente pour l'esthétique "privacy-first".
 */
@Composable
fun VoltTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDark
        else      -> FallbackLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoltTypography,
        content = content
    )
}
