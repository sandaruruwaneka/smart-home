package com.smarthome.control.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 8.3 — FloorCard.
 *
 * A list item: plan thumbnail, floor name, summary line, chevron. It shows an amber dot
 * if any device on that floor is at Attention tier and a red dot if any is Critical.
 *
 * The dot is the whole point of this card. The floor list is the screen a user lands on,
 * and it has to answer "is anything wrong anywhere in the house?" before they have
 * finished reading the floor names. A dot at the right edge of each row answers it in one
 * vertical scan.
 *
 * The card itself stays at [PriorityTier.NORMAL] regardless. Escalating the whole card
 * would put three Attention-tier surfaces on screen at once in a three-storey house,
 * which is exactly the over-escalation section 5 forbids — the dot carries the signal, the
 * surface stays quiet.
 *
 * @param planThumbnail null renders the blank-grid placeholder. `floors.plan_image_url` is
 *   nullable in SCHEMA.md, and the app must render a working card without it.
 * @param deviceCount total devices on the floor
 * @param activeCount devices currently ON
 * @param highestTier the loudest tier present among this floor's devices
 */
@Composable
fun FloorCard(
    name: String,
    deviceCount: Int,
    activeCount: Int,
    modifier: Modifier = Modifier,
    planThumbnail: Painter? = null,
    highestTier: PriorityTier = PriorityTier.NORMAL,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors
    val summary = "$deviceCount ${plural(deviceCount, "device")} · $activeCount active"

    val dotColor = when (highestTier) {
        PriorityTier.NORMAL -> null
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }
    val dotMeaning = when (highestTier) {
        PriorityTier.NORMAL -> ""
        PriorityTier.ATTENTION -> ", needs attention"
        PriorityTier.CRITICAL -> ", has an alert"
    }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "$name, $summary$dotMeaning" },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Spacing.minTouchTarget)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FloorThumbnail(planThumbnail)

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppType.sectionHeader,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = AppType.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (dotColor != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape),
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The plan thumbnail, or the blank-grid placeholder when `plan_image_url` is null.
 *
 * The placeholder is a grid glyph rather than a broken-image icon: a floor with no
 * uploaded plan still has a working coordinate grid, and the card should say "grid, no
 * picture yet", not "something failed".
 */
@Composable
private fun FloorThumbnail(painter: Painter?) {
    val colors = SmartHomeTheme.colors
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.GridOn,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

internal fun plural(count: Int, singular: String): String =
    if (count == 1) singular else "${singular}s"

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "FloorCard · dark", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun FloorCardPreviewDark() = GalleryPreview(dark = true) {
    FloorCard("Ground Floor", deviceCount = 7, activeCount = 2)
    FloorCard("First Floor", deviceCount = 5, activeCount = 1, highestTier = PriorityTier.ATTENTION)
    FloorCard("Kitchen Annexe", deviceCount = 3, activeCount = 3, highestTier = PriorityTier.CRITICAL)
    FloorCard("Loft", deviceCount = 1, activeCount = 0)
    FloorCard("Garage", deviceCount = 4, activeCount = 0, enabled = false)
}

@Preview(name = "FloorCard · light", showBackground = true, backgroundColor = 0xFFF5F7F8)
@Composable
private fun FloorCardPreviewLight() = GalleryPreview(dark = false) {
    FloorCard("Ground Floor", deviceCount = 7, activeCount = 2)
    FloorCard("First Floor", deviceCount = 5, activeCount = 1, highestTier = PriorityTier.ATTENTION)
    FloorCard("Kitchen Annexe", deviceCount = 3, activeCount = 3, highestTier = PriorityTier.CRITICAL)
}
