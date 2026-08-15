package com.smarthome.control.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.stateChangeSpec

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
 *   nullable in SCHEMA.md, and the app must render a working card without it. Takes
 *   precedence over [planImageUrl], which is how an artboard shows a plan without a
 *   network.
 * @param planImageUrl the Storage download URL from the floor document, loaded remotely.
 * @param deviceCount total devices on the floor
 * @param activeCount devices currently ON
 * @param highestTier the loudest tier present among this floor's devices
 * @param flaggedDevices how many devices are behind the dot, for the spoken description.
 *   Screen prompt 02 section 9 wants `Ground Floor, 1 device needs attention` rather than a
 *   colour nobody can hear.
 * @param onLongClick opens the context menu (`Rename`, `Delete floor`). Null leaves the
 *   card with no long-press behaviour at all rather than an empty menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloorCard(
    name: String,
    deviceCount: Int,
    activeCount: Int,
    modifier: Modifier = Modifier,
    planThumbnail: Painter? = null,
    planImageUrl: String? = null,
    highestTier: PriorityTier = PriorityTier.NORMAL,
    flaggedDevices: Int = 0,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val colors = SmartHomeTheme.colors
    val summary = "$deviceCount ${plural(deviceCount, "device")} · $activeCount active"

    val dotMeaning = when (highestTier) {
        PriorityTier.NORMAL -> ""
        PriorityTier.ATTENTION ->
            ", $flaggedDevices ${plural(flaggedDevices, "device")} " +
                "${if (flaggedDevices == 1) "needs" else "need"} attention"
        PriorityTier.CRITICAL ->
            ", $flaggedDevices ${plural(flaggedDevices, "device")} " +
                "${if (flaggedDevices == 1) "has" else "have"} an alert"
    }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = "$name, $summary$dotMeaning" },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Spacing.minTouchTarget)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FloorThumbnail(planThumbnail, planImageUrl)

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

            StatusDot(highestTier)

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
 * The 8 dp dot, and the icon that makes it survive without colour.
 *
 * Master prompt section 11 admits no state encoded by colour alone, and a coloured dot on
 * its own is exactly that — amber and red at 8 dp are the same grey to a red-green
 * colour-blind user and the same dot to anyone who has not learned the convention yet. The
 * paired glyph carries the meaning; the colour makes it findable in one scan.
 *
 * It fades and scales in over 200 ms (screen prompt 02 section 6). A dot that pops into
 * existence at full size next to a chevron reads as a rendering artefact; one that grows
 * into place reads as something that just happened.
 */
@Composable
private fun StatusDot(tier: PriorityTier) {
    val colors = SmartHomeTheme.colors

    val dotColor = when (tier) {
        PriorityTier.NORMAL -> null
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }
    val glyph = when (tier) {
        PriorityTier.NORMAL -> null
        PriorityTier.ATTENTION -> Icons.Rounded.WarningAmber
        PriorityTier.CRITICAL -> Icons.Rounded.ErrorOutline
    }

    // The tier that was last worth showing, so the dot has something to draw on the way
    // out instead of vanishing a frame before its exit animation starts.
    val lastFlagged = remember { mutableStateOf(PriorityTier.ATTENTION) }
    SideEffect { if (tier != PriorityTier.NORMAL) lastFlagged.value = tier }

    val spec = stateChangeSpec<Float>()

    AnimatedVisibility(
        visible = dotColor != null,
        enter = fadeIn(spec) + scaleIn(spec, initialScale = 0.6f),
        exit = fadeOut(spec) + scaleOut(spec, targetScale = 0.6f),
    ) {
        val shownColor = dotColor ?: when (lastFlagged.value) {
            PriorityTier.CRITICAL -> colors.stateError
            else -> colors.stateOn
        }
        val shownGlyph = glyph ?: when (lastFlagged.value) {
            PriorityTier.CRITICAL -> Icons.Rounded.ErrorOutline
            else -> Icons.Rounded.WarningAmber
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = shownGlyph,
                contentDescription = null,
                tint = shownColor,
                modifier = Modifier.size(16.dp),
            )
            Box(
                Modifier
                    .size(8.dp)
                    .background(shownColor, CircleShape),
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
private fun FloorThumbnail(painter: Painter?, imageUrl: String?) {
    val colors = SmartHomeTheme.colors
    Box(
        modifier = Modifier
            // 64 dp per screen prompt 02 section 4. At 56 the plan was a texture rather
            // than a picture — a room outline needs the extra eight to be recognisable.
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            painter != null -> Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            !imageUrl.isNullOrBlank() -> AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // No placeholder and no shimmer: the surfaceVariant square behind is
                // already the right shape in the right tone, and a spinner in a 64 dp
                // thumbnail is noise on a screen that is meant to be scanned.
                modifier = Modifier.fillMaxSize(),
            )

            else -> Icon(
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
    FloorCard(
        "First Floor",
        deviceCount = 5,
        activeCount = 1,
        highestTier = PriorityTier.ATTENTION,
        flaggedDevices = 1,
    )
    FloorCard(
        "Kitchen Annexe",
        deviceCount = 3,
        activeCount = 3,
        highestTier = PriorityTier.CRITICAL,
        flaggedDevices = 2,
    )
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
