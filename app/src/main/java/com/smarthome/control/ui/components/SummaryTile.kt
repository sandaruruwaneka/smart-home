package com.smarthome.control.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.stateChangeSpec

/**
 * The four tiles of the house-status overview (section 8.6).
 *
 * Each knows its own icon, label, and — critically — the tier it should adopt once its
 * count is non-zero.
 */
enum class SummaryTileKind {
    TOTAL_DEVICES,
    ACTIVE_NOW,
    ERRORS,
    WARNINGS;

    /**
     * `Active` rather than `Active now`: screen prompt 02 section 7 fixes the four labels,
     * and at four-to-a-row on a 412 dp frame the second word wraps anyway.
     */
    val label: String
        get() = when (this) {
            TOTAL_DEVICES -> "Devices"
            ACTIVE_NOW -> "Active"
            ERRORS -> "Errors"
            WARNINGS -> "Warnings"
        }

    val icon: ImageVector
        get() = when (this) {
            TOTAL_DEVICES -> Icons.Rounded.DeviceHub
            ACTIVE_NOW -> Icons.Rounded.Bolt
            ERRORS -> Icons.Rounded.ErrorOutline
            WARNINGS -> Icons.Rounded.WarningAmber
        }

    /**
     * The tier this tile adopts when its count is above zero.
     *
     * Errors take Critical, warnings take Attention. Totals and active counts are facts,
     * not events, so they are always Normal however large they get — a house with
     * fourteen lights on is not an emergency.
     */
    /**
     * Whether a zero on this tile should sit back in `textSecondary`.
     *
     * True for the two tiles that only mean something when they are non-zero. A `0` in the
     * same ink as the device count invites the eye to read it as a figure worth having;
     * one tone quieter, it reads as the absence it is. The totals never recede — they are
     * the numbers the user is actually there to read.
     */
    val recedesAtZero: Boolean get() = this == ERRORS || this == WARNINGS

    fun tierFor(count: Int): PriorityTier = when {
        count <= 0 -> PriorityTier.NORMAL
        this == ERRORS -> PriorityTier.CRITICAL
        this == WARNINGS -> PriorityTier.ATTENTION
        else -> PriorityTier.NORMAL
    }
}

/**
 * Section 8.6 — SummaryTile.
 *
 * A compact metric block: large numeric value, short label beneath.
 *
 * The errors and warnings tiles adopt Critical and Attention treatment respectively **only
 * when their count is above zero**. At zero they render Normal.
 *
 * That conditional is the entire design. A permanently red "Errors" tile showing `0` is
 * the single most common way a dashboard teaches its user to ignore red — after a week of
 * seeing it every day, the colour has stopped meaning anything and a real error arrives
 * into a screen that has already cried wolf. A zero-count tile is good news and should
 * look like the rest of the interface.
 *
 * Note this does not violate the "one Critical element at a time" rule in section 5: the
 * rule governs banners, which claim the top of a container. A tile is a metric, and its
 * red border is a pointer to the Alerts screen rather than an alarm in its own right.
 */
@Composable
fun SummaryTile(
    kind: SummaryTileKind,
    count: Int,
    modifier: Modifier = Modifier,
) {
    PriorityContainer(
        tier = kind.tierFor(count),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$count ${kind.label}"
        },
    ) {
        SummaryTileContent(kind, count, Modifier.padding(Spacing.md))
    }
}

/**
 * Icon, value, label — the inside of a tile, wherever the tile is drawn.
 *
 * Shared by the standalone [SummaryTile] and by the cells of [SummaryTileGroup] so that
 * the two can differ in how they are framed and in nothing else.
 *
 * ### The value cross-fades
 *
 * These numbers are driven by a live listener and change under the user's eyes. A digit
 * that swaps instantly registers as a glitch — the eye catches the change without catching
 * the number, and the user has to re-read the tile to find out what it says now. 200 ms of
 * cross-fade (master prompt section 10) is enough to be seen as a change rather than a
 * flicker, and short enough that nobody waits for it.
 *
 * The accent colour animates over the same duration, so a warnings tile crossing from zero
 * to one warms into amber rather than snapping.
 */
@Composable
private fun SummaryTileContent(
    kind: SummaryTileKind,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    val tier = kind.tierFor(count)

    val accent by animateColorAsState(
        targetValue = when (tier) {
            PriorityTier.NORMAL -> colors.textSecondary
            PriorityTier.ATTENTION -> colors.stateOn
            PriorityTier.CRITICAL -> colors.stateError
        },
        animationSpec = stateChangeSpec(),
        label = "summaryTileAccent",
    )
    val valueColor by animateColorAsState(
        targetValue = when (tier) {
            PriorityTier.NORMAL ->
                if (kind.recedesAtZero) colors.textSecondary else colors.textPrimary
            PriorityTier.ATTENTION -> colors.stateOn
            PriorityTier.CRITICAL -> colors.stateError
        },
        animationSpec = stateChangeSpec(),
        label = "summaryTileValue",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = kind.icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Crossfade(
            targetState = count,
            animationSpec = stateChangeSpec(),
            label = "summaryTileCount",
        ) { value ->
            Text(
                text = value.toString(),
                style = AppType.numericLarge,
                color = valueColor,
            )
        }
        Text(
            text = kind.label,
            style = AppType.label,
            color = colors.textSecondary,
        )
    }
}

/**
 * The four tiles as one row, which is how the house-status overview uses them.
 *
 * Equal weights rather than intrinsic widths: the numbers change every few seconds, and
 * tiles that resize as counts cross from 9 to 10 make the whole overview twitch.
 */
@Composable
fun SummaryTileRow(
    totalDevices: Int,
    activeNow: Int,
    errors: Int,
    warnings: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        SummaryTile(SummaryTileKind.TOTAL_DEVICES, totalDevices, Modifier.weight(1f))
        SummaryTile(SummaryTileKind.ACTIVE_NOW, activeNow, Modifier.weight(1f))
        SummaryTile(SummaryTileKind.ERRORS, errors, Modifier.weight(1f))
        SummaryTile(SummaryTileKind.WARNINGS, warnings, Modifier.weight(1f))
    }
}

/**
 * The four tiles as one card — the form the floor list uses.
 *
 * ### Why this is not [SummaryTileRow]
 *
 * Screen prompt 02 section 4 asks for one `surface` card with internal dividers rather than
 * four floating cards, and it is right: the status summary is the most important block on
 * the home screen, and four separate cards fragment it into four things to read instead of
 * one thing to glance at. The row form stays for the gallery, where the point is to show a
 * tile on its own.
 *
 * Escalation is therefore carried by the value colour rather than by a border — inside a
 * shared card there is no border to give one cell. That is exactly what the screen prompt's
 * table asks for: the warnings value in `stateOn`, the errors value in `stateError`, and
 * everything else in a single calm register.
 *
 * The whole card is one accessibility node. Four disconnected numbers read out in sequence
 * — "twelve, three, zero, one" — is a puzzle; one sentence is a status.
 */
@Composable
fun SummaryTileGroup(
    totalDevices: Int,
    activeNow: Int,
    errors: Int,
    warnings: Int,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    val spoken = "House status. $totalDevices devices, $activeNow active, " +
        "$errors ${plural(errors, "error")}, $warnings ${plural(warnings, "warning")}."

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                // Announced when the numbers change, but never over whatever the user is
                // reading — the AlertBanner is the only assertive thing on this screen.
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(
            // Intrinsic height so the dividers can fill it: without this they have no
            // height to fill and disappear.
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            SummaryTileKind.entries.forEachIndexed { index, kind ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(AppBorders.hairline)
                            .fillMaxHeight()
                            .background(colors.outline),
                    )
                }
                SummaryTileContent(
                    kind = kind,
                    count = when (kind) {
                        SummaryTileKind.TOTAL_DEVICES -> totalDevices
                        SummaryTileKind.ACTIVE_NOW -> activeNow
                        SummaryTileKind.ERRORS -> errors
                        SummaryTileKind.WARNINGS -> warnings
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "SummaryTile · quiet house", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun SummaryTileQuietPreview() = GalleryPreview(dark = true) {
    // Everything Normal. This is what the app looks like almost all of the time, and it
    // is the state the design has to keep calm.
    SummaryTileRow(totalDevices = 16, activeNow = 3, errors = 0, warnings = 0)
}

@Preview(name = "SummaryTile · house with faults", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun SummaryTileFaultsPreview() = GalleryPreview(dark = true) {
    SummaryTileRow(totalDevices = 16, activeNow = 5, errors = 1, warnings = 2)
}

@Preview(name = "SummaryTile · light", showBackground = true, backgroundColor = 0xFFF5F7F8)
@Composable
private fun SummaryTileLightPreview() = GalleryPreview(dark = false) {
    SummaryTileRow(totalDevices = 16, activeNow = 3, errors = 0, warnings = 0)
    SummaryTileRow(totalDevices = 16, activeNow = 5, errors = 1, warnings = 2)
}

@Preview(name = "SummaryTileGroup · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun SummaryTileGroupPreviewDark() = GalleryPreview(dark = true) {
    SummaryTileGroup(totalDevices = 12, activeNow = 3, errors = 0, warnings = 0)
    SummaryTileGroup(totalDevices = 12, activeNow = 3, errors = 0, warnings = 1)
    SummaryTileGroup(totalDevices = 12, activeNow = 4, errors = 1, warnings = 2)
}

@Preview(name = "SummaryTileGroup · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun SummaryTileGroupPreviewLight() = GalleryPreview(dark = false) {
    SummaryTileGroup(totalDevices = 12, activeNow = 3, errors = 0, warnings = 0)
    SummaryTileGroup(totalDevices = 12, activeNow = 4, errors = 1, warnings = 2)
}
