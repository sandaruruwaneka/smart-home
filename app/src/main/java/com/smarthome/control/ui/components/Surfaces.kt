package com.smarthome.control.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.SmartHomeTheme

/**
 * The app's card. 16 dp radius, 1 dp outline border, **no drop shadow** — surfaces
 * separate by tone, not elevation (section 7).
 *
 * Elevation is left at zero everywhere rather than being a parameter, because a shadow
 * on one card and not another is exactly the inconsistency this design system exists to
 * prevent.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.card,
    color: Color = SmartHomeTheme.colors.surface,
    border: BorderStroke? = BorderStroke(AppBorders.hairline, SmartHomeTheme.colors.outline),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = SmartHomeTheme.colors.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border,
        content = content,
    )
}

/**
 * Applies the section 5 treatment for a tier to any container.
 *
 * The three tiers are deliberately implemented in one place. Rendering the Attention bar
 * by hand in each component is how a 1 dp bar becomes a 2 dp bar on one screen and an
 * amber background on another.
 *
 * - [PriorityTier.NORMAL] — standard surface, no accent border
 * - [PriorityTier.ATTENTION] — surfaceVariant fill, stateOn left bar
 * - [PriorityTier.CRITICAL] — surface fill, full stateError border, red left bar
 *
 * The left bar is drawn as a clipped overlay rather than as a sibling row, so callers do
 * not have to reserve space for it and every tier keeps identical content geometry.
 */
@Composable
fun PriorityContainer(
    tier: PriorityTier,
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val fill = when (tier) {
        PriorityTier.NORMAL -> colors.surface
        PriorityTier.ATTENTION -> colors.surfaceVariant
        PriorityTier.CRITICAL -> colors.surface
    }
    val borderColor = when (tier) {
        PriorityTier.NORMAL -> colors.outline
        PriorityTier.ATTENTION -> colors.outline
        PriorityTier.CRITICAL -> colors.stateError
    }
    val barColor = when (tier) {
        PriorityTier.NORMAL -> null
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }

    AppCard(
        modifier = modifier,
        shape = shape,
        color = fill,
        border = BorderStroke(AppBorders.hairline, borderColor),
    ) {
        Box {
            content()
            if (barColor != null) {
                // `matchParentSize` rather than `fillMaxHeight`, and the difference is not
                // cosmetic. `fillMaxHeight` resolves against the *maximum* height offered,
                // so a container placed where a lot of vertical space is available — a
                // hazard chip in a Column above a weighted canvas, say — stretched the bar
                // to that maximum and took the whole card with it, squeezing the floor plan
                // off screen entirely. `matchParentSize` measures against the size the Box
                // actually settled on and contributes nothing to it.
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(AppBorders.hairline)
                            .background(barColor),
                    )
                }
            }
        }
    }
}

/**
 * A dashed border, used only for [com.smarthome.control.ui.model.DeviceState.DISCONNECTED].
 *
 * Compose's `Modifier.border` draws solid strokes only, so the dashed case is drawn
 * directly. The dash is what makes "offline" survive being photocopied, colour-blindness,
 * and a phone held at arm's length — it is the non-colour half of the encoding required
 * by section 11.
 */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
    dashLength: Dp = 4.dp,
    gapLength: Dp = 4.dp,
): Modifier = drawBehind {
    val strokePx = width.toPx()
    val effect = PathEffect.dashPathEffect(
        floatArrayOf(dashLength.toPx(), gapLength.toPx()),
        0f,
    )
    // Inset by half the stroke so the dash sits inside the bounds, matching how
    // Modifier.border lays out a solid stroke of the same width.
    val inset = strokePx / 2f
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx,
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = strokePx, pathEffect = effect),
    )
}

/**
 * Solid or dashed border in one call, so a component can switch on state without
 * branching its whole modifier chain.
 */
fun Modifier.stateBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
    dashed: Boolean,
): Modifier = if (dashed) {
    dashedBorder(color, width, cornerRadius)
} else {
    border(width, color, RoundedCornerShape(cornerRadius))
}

/** Standard 16 dp screen padding (section 2). */
fun Modifier.screenPadding(): Modifier = padding(horizontal = 16.dp)
