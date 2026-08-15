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
import com.smarthome.control.ui.components.AlertBannerCollapsed
import com.smarthome.control.ui.components.AlertRow
import com.smarthome.control.ui.components.AlertType
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.components.ChannelRow
import com.smarthome.control.ui.components.CountdownRing
import com.smarthome.control.ui.components.DeviceMarker
import com.smarthome.control.ui.components.EmptyStates
import com.smarthome.control.ui.components.FloorCard
import com.smarthome.control.ui.components.StatCard
import com.smarthome.control.ui.components.StatusBadge
import com.smarthome.control.ui.components.SummaryTileRow
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 13, item 3 — the ten components, each in all relevant states.
 *
 * Every specimen is the real composable with real arguments, not a picture of one. That
 * is the reason for building the deliverable as an app rather than as a document: a
 * static sheet cannot show that the hazard pulse runs at 2 s, that a badge cross-fades
 * over 200 ms, or that the dashed border survives a theme flip.
 */
@Composable
fun ComponentSheet() {
    val colors = SmartHomeTheme.colors

    GallerySheet {
        GallerySection("8.1 StatusBadge", "Icon and text label — never colour alone") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DeviceState.entries.forEach { StatusBadge(it) }
            }
            Specimen("Disabled") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DeviceState.entries.forEach { StatusBadge(it, enabled = false) }
                }
            }
            Specimen("Compact, for dense legends") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DeviceState.entries.forEach { StatusBadge(it, compact = true) }
                }
            }
        }

        GallerySection(
            "8.2 DeviceMarker",
            "State by fill, border weight, and border style — not by glow",
        ) {
            Specimen("ON · OFF · ERROR · DISCONNECTED") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DeviceState.entries.forEach {
                        DeviceMarker(DeviceType.OUTLET, it, onClick = {})
                    }
                }
            }
            Specimen("Every device type, ON") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DeviceType.entries.forEach {
                        DeviceMarker(it, DeviceState.ON, onClick = {})
                    }
                }
            }
            Specimen("Selected · disabled") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DeviceMarker(DeviceType.LIGHT, DeviceState.ON, selected = true, onClick = {})
                    DeviceMarker(DeviceType.LIGHT, DeviceState.OFF, selected = true, onClick = {})
                    DeviceMarker(DeviceType.CAMERA, DeviceState.ON, enabled = false, onClick = {})
                }
            }
            Specimen("The reserved pulse — hazard device against its limit") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DeviceMarker(
                        DeviceType.APPLIANCE,
                        DeviceState.ON,
                        hazardActive = true,
                        onClick = {},
                    )
                    DeviceMarker(DeviceType.APPLIANCE, DeviceState.ON, onClick = {})
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "The pulse is the only animation of its kind in the system. That is " +
                        "why it works: on a floor plan holding twenty markers, the one that " +
                        "moves is the one that needs you. Spending it on ordinary ON state " +
                        "would leave nothing to escalate to.",
                    style = AppType.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        GallerySection("8.3 FloorCard", "Amber dot at Attention, red dot at Critical") {
            FloorCard("Ground Floor", deviceCount = 7, activeCount = 2)
            FloorCard(
                "First Floor",
                deviceCount = 5,
                activeCount = 1,
                highestTier = PriorityTier.ATTENTION,
            )
            FloorCard(
                "Kitchen Annexe",
                deviceCount = 3,
                activeCount = 3,
                highestTier = PriorityTier.CRITICAL,
            )
            Specimen("One device, no plan image uploaded") {
                FloorCard("Loft", deviceCount = 1, activeCount = 0)
            }
        }

        GallerySection("8.4 ChannelRow", "Ordered by index, never alphabetically") {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.md)) {
                    ChannelRow("Ceiling light", 0, DeviceState.ON)
                    ChannelRow("Wall lamp", 1, DeviceState.OFF)
                    ChannelRow("", 2, DeviceState.OFF)
                    ChannelRow("Extractor fan", 3, DeviceState.ERROR)
                }
            }
            Specimen("Disabled") {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md)) {
                        ChannelRow("Porch light", 0, DeviceState.ON, enabled = false)
                    }
                }
            }
        }

        GallerySection("8.5 CountdownRing", "Turns red in the final 10 %") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Specimen("Just started") { CountdownRing(0, 1800) }
                Specimen("Halfway") { CountdownRing(900, 1800) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Specimen("Final 10 %") { CountdownRing(1700, 1800) }
                Specimen("Limit reached") { CountdownRing(1800, 1800) }
            }
        }

        GallerySection(
            "8.6 SummaryTile",
            "Errors and warnings escalate only when their count is above zero",
        ) {
            Specimen("Quiet house — every tile Normal") {
                SummaryTileRow(totalDevices = 16, activeNow = 3, errors = 0, warnings = 0)
            }
            Specimen("Faults present — Critical and Attention adopted") {
                SummaryTileRow(totalDevices = 16, activeNow = 5, errors = 1, warnings = 2)
            }
        }

        GallerySection("8.7 AlertBanner", "Critical tier. Never swipe-dismissible.") {
            AlertBanner(
                cause = "Iron switched off automatically",
                reason = "Maximum active duration exceeded",
                timestamp = "2 min ago",
            )
            Specimen("Multiple criticals collapse into one banner") {
                AlertBannerCollapsed(alertCount = 3, timestamp = "Just now")
            }
            Specimen("No action available") {
                AlertBanner(
                    cause = "Kitchen camera is offline",
                    reason = "The stream stopped responding",
                    timestamp = "12 min ago",
                    actionLabel = null,
                )
            }
        }

        GallerySection("8.8 AlertRow", "History is a record, not an alarm") {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Spacing.md)) {
                    AlertRow(
                        "Iron",
                        "Maximum active time exceeded",
                        "2 min ago",
                        AlertType.MAX_DURATION_EXCEEDED,
                        acknowledged = false,
                    )
                    AlertRow(
                        "Kitchen outlet",
                        "Device reported a fault",
                        "1 hr ago",
                        AlertType.DEVICE_ERROR,
                    )
                    AlertRow(
                        "Water heater",
                        "Maximum active time exceeded",
                        "Yesterday",
                        AlertType.MAX_DURATION_EXCEEDED,
                    )
                }
            }
        }

        GallerySection("8.9 StatCard", "Sparkline answers direction, nothing more") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(
                    value = "12.4",
                    unit = "hrs",
                    label = "On time this week",
                    trend = listOf(2f, 3.5f, 1f, 4f, 3f, 5f, 4.2f),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = "38",
                    unit = "switches",
                    label = "Toggles this week",
                    trend = listOf(6f, 4f, 7f, 5f, 8f, 3f, 5f),
                    modifier = Modifier.weight(1f),
                )
            }
            Specimen("No history yet — no line drawn") {
                StatCard(value = "0", unit = "hrs", label = "Iron", modifier = Modifier.fillMaxWidth())
            }
        }

        GallerySection("8.10 EmptyState", "Copy invites, it does not apologise") {
            AppCard(Modifier.fillMaxWidth()) { EmptyStates.Floors() }
            AppCard(Modifier.fillMaxWidth()) { EmptyStates.Devices() }
            AppCard(Modifier.fillMaxWidth()) { EmptyStates.Alerts() }
            AppCard(Modifier.fillMaxWidth()) { EmptyStates.Reports() }
        }
    }
}

@Preview(name = "Component sheet · dark", showBackground = true, widthDp = 412, heightDp = 1600)
@Composable
private fun ComponentSheetPreview() {
    SmartHomeTheme(darkTheme = true) { ComponentSheet() }
}
