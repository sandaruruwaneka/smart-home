package com.smarthome.control.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette from master prompt section 4, carried under the names the design document
 * uses.
 *
 * Material 3's [androidx.compose.material3.ColorScheme] has no slot for device-state
 * colour, so the four state tokens would otherwise have to be smuggled into unrelated
 * roles (`tertiary`, `error`, …) where the next person to read the code cannot tell what
 * they mean. Instead the whole palette lives here and [SmartHomeTheme] derives a
 * ColorScheme from it, so stock M3 components stay correct while app components can say
 * `SmartHomeTheme.colors.stateOn` and mean it.
 */
@Immutable
data class SmartHomeColors(
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    // Interaction
    val primary: Color,
    val onPrimary: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    // Device state
    val stateOn: Color,
    val stateOff: Color,
    val stateError: Color,
    val stateDisconnected: Color,
    val onStateOn: Color,
    val onStateError: Color,
    /** True when the dark palette is active. Dark is the primary theme. */
    val isDark: Boolean,
)

val DarkSmartHomeColors = SmartHomeColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    stateOn = DarkStateOn,
    stateOff = DarkStateOff,
    stateError = DarkStateError,
    stateDisconnected = DarkStateDisconnected,
    onStateOn = DarkOnStateOn,
    onStateError = DarkOnStateError,
    isDark = true,
)

val LightSmartHomeColors = SmartHomeColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    stateOn = LightStateOn,
    stateOff = LightStateOff,
    stateError = LightStateError,
    stateDisconnected = LightStateDisconnected,
    onStateOn = LightOnStateOn,
    onStateError = LightOnStateError,
    isDark = false,
)

/**
 * Defaults to the dark palette so a composable previewed or tested outside
 * [SmartHomeTheme] still renders in the app's primary theme rather than a blank one.
 */
val LocalSmartHomeColors = staticCompositionLocalOf { DarkSmartHomeColors }
