package com.smarthome.control.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.smarthome.control.ui.components.AlertBanner
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.components.DeviceRow
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.model.priorityTierOf
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 13, item 4 — the same device row rendered at Normal, Attention, and Critical.
 *
 * Shown stacked rather than literally side by side: on a 412 dp portrait frame, three
 * rows across would be 130 dp each, which is narrower than the component ever is in the
 * real app and would compare something the user will never see. Stacked at full width
 * they are directly comparable, which is what the deliverable is for.
 */
@Composable
fun PriorityHierarchySheet() {
    val colors = SmartHomeTheme.colors

    GallerySheet {
        GallerySection(
            "The same row at three volumes",
            "Only the fill, the left bar, the border, and the icon tint change.",
        ) {
            Specimen("Normal — standard surface, no accent border") {
                DeviceRow(
                    name = "Iron",
                    type = DeviceType.APPLIANCE,
                    state = DeviceState.OFF,
                    tier = PriorityTier.NORMAL,
                    detail = "Off since 14:20",
                )
            }
            Specimen("Attention — surfaceVariant fill, stateOn left bar, amber icon") {
                DeviceRow(
                    name = "Iron",
                    type = DeviceType.APPLIANCE,
                    state = DeviceState.ON,
                    tier = PriorityTier.ATTENTION,
                    detail = "8 min left of 30 min",
                )
            }
            Specimen("Critical — stateError border, red icon, above all other content") {
                DeviceRow(
                    name = "Iron",
                    type = DeviceType.APPLIANCE,
                    state = DeviceState.ERROR,
                    tier = PriorityTier.CRITICAL,
                    detail = "Maximum on time exceeded",
                )
            }
        }

        GallerySection(
            "What earns each tier",
            "Derived by priorityTierOf, so no screen can disagree with another.",
        ) {
            TierNote(
                tier = PriorityTier.NORMAL,
                reserved = "Ordinary device rows and markers in any state, floor cards, usage stats",
            )
            TierNote(
                tier = PriorityTier.ATTENTION,
                reserved = "Hazard-class devices currently ON, devices approaching their " +
                    "duration limit, scheduled devices running now",
            )
            TierNote(
                tier = PriorityTier.CRITICAL,
                reserved = "Automatic safety cutoffs, devices in ERROR, duration limit breached",
            )
        }

        GallerySection(
            "Under-escalation beats over-escalation",
            "An ordinary light being ON is Normal.",
        ) {
            // The tier here is derived, not asserted — this is the live rule, and it
            // returns NORMAL for a light that is simply on.
            DeviceRow(
                name = "Living room lamp",
                type = DeviceType.LIGHT,
                state = DeviceState.ON,
                tier = priorityTierOf(DeviceState.ON, DeviceType.LIGHT),
                detail = "On for 20 min",
            )
            DeviceRow(
                name = "Iron",
                type = DeviceType.APPLIANCE,
                state = DeviceState.ON,
                tier = priorityTierOf(
                    state = DeviceState.ON,
                    type = DeviceType.APPLIANCE,
                    elapsedSeconds = 600,
                    maxOnSeconds = 1800,
                ),
                detail = "20 min left of 30 min",
            )
            AppCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Both devices are ON and both draw power, so both markers are amber. " +
                        "Only the second one is a hazard, so only the second row escalates. " +
                        "Escalating the lamp too would make Attention mean \"something is " +
                        "switched on\", and the tier would stop carrying information.",
                    style = AppType.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        GallerySection(
            "One Critical at a time",
            "Multiple critical events collapse into a single banner with a count.",
        ) {
            AlertBanner(
                cause = "Iron switched off automatically",
                reason = "Maximum active duration exceeded",
                timestamp = "2 min ago",
            )
            AppCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "A second banner never appears beside this one. Two red banners " +
                        "stacked do not read as twice the urgency — they read as red being " +
                        "ordinary, and the next real emergency arrives into an interface " +
                        "that has already spent its loudest signal.",
                    style = AppType.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun TierNote(tier: PriorityTier, reserved: String) {
    val colors = SmartHomeTheme.colors
    val accent = when (tier) {
        PriorityTier.NORMAL -> colors.textSecondary
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = tier.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = AppType.sectionHeader,
                    color = accent,
                )
            }
            Text(reserved, style = AppType.body, color = colors.textSecondary)
        }
    }
}

@Preview(name = "Hierarchy · dark", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun PriorityHierarchySheetPreviewDark() {
    SmartHomeTheme(darkTheme = true) { PriorityHierarchySheet() }
}

@Preview(name = "Hierarchy · light", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun PriorityHierarchySheetPreviewLight() {
    SmartHomeTheme(darkTheme = false) { PriorityHierarchySheet() }
}
