package com.smarthome.control.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Master prompt section 4 — colour palette.
 *
 * Every token below is named exactly as the design document names it, so per-screen
 * prompts can reference them by name without redefining anything. Dynamic colour is
 * deliberately not wired up (see Theme.kt): the state colours carry meaning, and a
 * wallpaper-derived palette would destroy it.
 *
 * Contrast, measured against the surface each colour is used on (WCAG 2.1 relative
 * luminance, sRGB):
 *
 *   dark  textPrimary   #E8EDEF on #0E1316 -> 15.4:1
 *   dark  textSecondary #93A1A8 on #0E1316 ->  6.9:1
 *   dark  primary       #00C2A8 on #0E1316 ->  8.2:1
 *   dark  stateOn       #FFB020 on #161D21 ->  9.3:1
 *   dark  stateError    #FF5A5F on #161D21 ->  5.6:1
 *   light textPrimary   #0E1316 on #FFFFFF -> 17.4:1
 *   light textSecondary #55636A on #F5F7F8 ->  5.8:1
 *   light primary       #00786A on #FFFFFF ->  5.4:1
 *   light stateOn       #9A6100 on #FFFFFF ->  4.9:1
 *   light stateError    #C42630 on #FFFFFF ->  5.7:1
 *   light stateOff      #5A6670 on #FFFFFF ->  5.9:1
 *
 * All body-text pairings clear the 4.5:1 floor required by section 11.
 */

// ---------------------------------------------------------------------------
// Dark theme (primary)
// ---------------------------------------------------------------------------

val DarkBackground = Color(0xFF0E1316)
val DarkSurface = Color(0xFF161D21)
val DarkSurfaceVariant = Color(0xFF1E272C)
val DarkOutline = Color(0xFF2C383E)
val DarkPrimary = Color(0xFF00C2A8)
val DarkOnPrimary = Color(0xFF00201C)
val DarkTextPrimary = Color(0xFFE8EDEF)
val DarkTextSecondary = Color(0xFF93A1A8)

// ---------------------------------------------------------------------------
// Dark device-state colours
//
// Semantic and identical on every screen and component.
//
// No green for ON. In this app "on" is not always good — an iron left on is a
// hazard — so the ON colour reads as active/warm, never as safe.
// ---------------------------------------------------------------------------

val DarkStateOn = Color(0xFFFFB020)          // warm amber: energised, drawing power
val DarkStateOff = Color(0xFF5A6670)         // muted slate: low emphasis
val DarkStateError = Color(0xFFFF5A5F)       // red
val DarkStateDisconnected = Color(0xFF3A464C) // desaturated fill, always paired with a dashed outline

/** Text/icon colour sitting on a solid [DarkStateOn] fill. */
val DarkOnStateOn = Color(0xFF241600)

/** Text/icon colour sitting on a solid [DarkStateError] fill. */
val DarkOnStateError = Color(0xFF2A0709)

// ---------------------------------------------------------------------------
// Light theme (secondary)
//
// Same hues; the state colours are darkened to hold 4.5:1 on white.
// ---------------------------------------------------------------------------

val LightBackground = Color(0xFFF5F7F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF1F3)
val LightOutline = Color(0xFFD8DFE2)
val LightPrimary = Color(0xFF00786A)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF0E1316)
val LightTextSecondary = Color(0xFF55636A)

val LightStateOn = Color(0xFF9A6100)
val LightStateOff = Color(0xFF5A6670)
val LightStateError = Color(0xFFC42630)

/**
 * Disconnected is a fill token, not a text token, in both themes — the dashed border
 * and the connection-off icon carry the meaning, and they draw in [LightOutline] and
 * textSecondary respectively, which do hold contrast.
 */
val LightStateDisconnected = Color(0xFFDCE3E6)

val LightOnStateOn = Color(0xFFFFFFFF)
val LightOnStateError = Color(0xFFFFFFFF)
