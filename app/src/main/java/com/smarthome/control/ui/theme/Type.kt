package com.smarthome.control.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Master prompt section 6 — typography.
 *
 * Family: Inter, falling back to Roboto.
 *
 * Inter is not bundled in this repository, so [AppFontFamily] currently resolves to the
 * platform sans-serif, which is Roboto on Android — the documented fallback. To switch to
 * Inter, drop the variable font into app/src/main/res/font/inter.ttf and change
 * AppFontFamily to:
 *
 *     val AppFontFamily = FontFamily(
 *         Font(R.font.inter, FontWeight.Normal),
 *         Font(R.font.inter, FontWeight.Medium),
 *         Font(R.font.inter, FontWeight.SemiBold),
 *     )
 *
 * Nothing else in the app needs to change: every style below is built from this one value.
 */
val AppFontFamily = FontFamily.SansSerif

/**
 * OpenType tabular figures.
 *
 * Mandatory on any live-updating number. A countdown drawn with proportional digits
 * reflows its own width as the digits change, which reads as a rendering bug rather than
 * as a clock.
 */
private const val TabularFigures = "tnum"

/**
 * The scale from section 6, under the names the design document uses. These map onto the
 * Material 3 [Typography] slots in [SmartHomeTypography] below, but app code should
 * prefer these names — `AppType.numeric` says what it is for in a way that
 * `titleMedium` does not.
 */
object AppType {

    /** Screen titles. 24 sp semibold. */
    val display = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    )

    /** Section headers. 16 sp medium. */
    val sectionHeader = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    /** Body. 14 sp regular. */
    val body = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** Labels and captions. 12 sp medium, +0.2 letter spacing. */
    val label = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    )

    /** Countdowns and usage hours. 20 sp medium, tabular figures. */
    val numeric = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TabularFigures,
    )

    /**
     * The headline figure on a SummaryTile or StatCard. Same tabular treatment as
     * [numeric] — these numbers update live too — at the size the overview needs to be
     * readable from across a room.
     */
    val numericLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontFeatureSettings = TabularFigures,
    )
}

/**
 * Material 3 slots, filled from [AppType] so stock components inherit the same scale.
 * Slots the app does not use are left to sensible neighbours rather than invented.
 */
val SmartHomeTypography = Typography(
    headlineSmall = AppType.display,
    titleLarge = AppType.display,
    titleMedium = AppType.sectionHeader,
    titleSmall = AppType.sectionHeader,
    bodyLarge = AppType.body,
    bodyMedium = AppType.body,
    bodySmall = AppType.label,
    labelLarge = AppType.label.copy(fontSize = 14.sp),
    labelMedium = AppType.label,
    labelSmall = AppType.label,
)
