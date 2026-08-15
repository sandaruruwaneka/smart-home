package com.smarthome.control.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * Entry point for the design system.
 *
 * Dynamic colour is deliberately absent. Material You would recolour `primary` and the
 * surfaces from the user's wallpaper, and in a control panel that is not a nice touch —
 * the state colours carry meaning (amber is "drawing power", red is "act now"), and a
 * palette that shifts per device makes that vocabulary unlearnable.
 *
 * @param darkTheme dark is the primary theme; light follows the system by default.
 */
@Composable
fun SmartHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkSmartHomeColors else LightSmartHomeColors

    // Stock M3 components read the ColorScheme, so it is derived from the same palette
    // rather than maintained separately. `error` maps to stateError so a TextField or
    // Snackbar that goes into its error state matches an AlertBanner sitting above it.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.primary,
            onSecondary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceVariant,
            surfaceContainerHighest = colors.surfaceVariant,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.background,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.stateError,
            onError = colors.onStateError,
            scrim = colors.background,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.primary,
            onSecondary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceVariant,
            surfaceContainerHighest = colors.surfaceVariant,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.background,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.stateError,
            onError = colors.onStateError,
            scrim = colors.textPrimary,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge: system bar icons invert with the theme so they stay legible
            // against the app's own background (section 2).
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSmartHomeColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartHomeTypography,
            shapes = SmartHomeShapes,
            content = content,
        )
    }
}

/**
 * Accessor object, mirroring the shape of [MaterialTheme].
 *
 * `SmartHomeTheme.colors.stateOn` reads as an intent; `MaterialTheme.colorScheme.tertiary`
 * would not.
 */
object SmartHomeTheme {
    val colors: SmartHomeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSmartHomeColors.current
}

/**
 * True when the user has asked the system to reduce or remove animation.
 *
 * Section 10 requires the hazard pulse to become a static border and transitions to
 * become instant. Android exposes this as the animator duration scale — the same switch
 * that "Remove animations" in Accessibility settings and Developer Options both write.
 * Every animated component in this design system checks it.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Exception) {
        // Preview and test environments have no real ContentResolver behind this key.
        false
    }
}
