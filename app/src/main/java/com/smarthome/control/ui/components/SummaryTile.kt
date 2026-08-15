package com.smarthome.control.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

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

    val label: String
        get() = when (this) {
            TOTAL_DEVICES -> "Devices"
            ACTIVE_NOW -> "Active now"
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
    val colors = SmartHomeTheme.colors
    val tier = kind.tierFor(count)

    val accent = when (tier) {
        PriorityTier.NORMAL -> colors.textSecondary
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }
    val valueColor = when (tier) {
        PriorityTier.NORMAL -> colors.textPrimary
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }

    PriorityContainer(
        tier = tier,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$count ${kind.label}"
        },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = kind.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = count.toString(),
                style = AppType.numericLarge,
                color = valueColor,
            )
            Text(
                text = kind.label,
                style = AppType.label,
                color = colors.textSecondary,
            )
        }
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
