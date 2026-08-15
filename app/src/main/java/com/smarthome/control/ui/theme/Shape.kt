package com.smarthome.control.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Master prompt section 7 — shape and spacing.
 *
 * Base unit 8 dp. Cards carry a 1 dp outline border and no drop shadow: surfaces
 * separate by tone, not elevation.
 */

/** Spacing scale. Nothing in the app should use a gap that is not one of these. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** Horizontal screen padding (section 2). */
    val screenHorizontal = 16.dp

    /** Minimum touch target (section 2). */
    val minTouchTarget = 48.dp
}

/** Corner radii, under the names section 7 uses. */
object AppShapes {
    val card = RoundedCornerShape(16.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Primary actions. */
    val buttonPill = RoundedCornerShape(percent = 50)

    /** Secondary actions. */
    val buttonSecondary = RoundedCornerShape(12.dp)

    /** Chips and device markers. */
    val chip = RoundedCornerShape(12.dp)
}

/** Border weights. 1 dp is the resting weight; 1.5 dp marks a state worth looking at. */
object AppBorders {
    val hairline = 1.dp
    val emphasis = 1.5.dp

    /** The leading state bar on a ChannelRow. */
    val stateBar = 3.dp
}

val SmartHomeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = AppShapes.buttonSecondary,
    medium = AppShapes.card,
    large = AppShapes.card,
    extraLarge = RoundedCornerShape(28.dp),
)
