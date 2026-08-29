package se.kidquest.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import se.kidquest.app.theme.LocalSeasonPalette
import se.kidquest.app.theme.SeasonPalette
import se.kidquest.app.theme.SeasonTheme

/**
 * @param dark null means nobody has chosen yet, so follow the phone. Once a parent
 *   touches the switch in the overflow menu it is their decision and stays theirs.
 */
@Composable
fun KidQuestTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isDark = dark ?: isSystemInDarkTheme()
    val palette = SeasonTheme.current(isDark)

    // enableEdgeToEdge() follows the phone, not the switch in our own menu. Without
    // this, a parent who turns dark mode on while the phone stays light gets dark
    // status-bar icons on a dark background -- an invisible clock.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalSeasonPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Material's colours come from the season, not from the wallpaper.
 *
 * This used to be the new-project template: Purple80/Purple40 and `dynamicColor = true`,
 * which on Android 12+ hands every dialog, switch and text field a colour taken from
 * whatever picture the parent set as their background. It explains a lot of colours in
 * this app that nobody chose. Dialogs and text fields read from MaterialTheme, so
 * building the scheme here is also what makes them follow dark mode at all.
 */
private fun schemeFor(p: SeasonPalette) = if (p.dark) {
    darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.accent, onPrimaryContainer = p.onAccent,
        secondary = p.accent, onSecondary = p.onAccent,
        background = p.pageBg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.tipBg, onSurfaceVariant = p.inkSoft,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
        outline = p.outlineEdge, outlineVariant = p.cardEdge,
        error = p.danger, onError = p.onAccent,
    )
} else {
    lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.calBg, onPrimaryContainer = p.calInk,
        secondary = p.accent, onSecondary = p.onAccent,
        background = p.pageBg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.tipBg, onSurfaceVariant = p.inkSoft,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
        outline = p.outlineEdge, outlineVariant = p.cardEdge,
        error = p.danger, onError = androidx.compose.ui.graphics.Color.White,
    )
}
