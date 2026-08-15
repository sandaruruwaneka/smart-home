package com.smarthome.control.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * An ordinary device list row, rendered at whichever [PriorityTier] the caller derives.
 *
 * This is the component the priority-hierarchy sheet (section 13, item 4) compares across
 * all three tiers, so it is written to change *only* what section 5 says changes: the
 * fill, the left bar, the border, and the icon tint. Content, geometry, and type stay
 * identical at every tier.
 *
 * That constraint is the whole demonstration. If escalating a row also moved its text or
 * grew its padding, the tiers would be three different components and a user could not
 * learn to read the difference as a single, consistent signal of urgency.
 *
 * @param tier normally derived with
 *   [com.smarthome.control.ui.model.priorityTierOf] rather than passed by hand
 * @param detail the secondary line — a countdown, a schedule window, a reason
 */
@Composable
fun DeviceRow(
    name: String,
    type: DeviceType,
    state: DeviceState,
    modifier: Modifier = Modifier,
    tier: PriorityTier = PriorityTier.NORMAL,
    detail: String? = null,
    onClick: () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors

    // The only thing the tier changes about the content itself.
    val iconTint = when (tier) {
        PriorityTier.NORMAL -> colors.textSecondary
        PriorityTier.ATTENTION -> colors.stateOn
        PriorityTier.CRITICAL -> colors.stateError
    }

    PriorityContainer(
        tier = tier,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$name, ${type.label}, ${state.spoken}")
                    if (detail != null) append(", $detail")
                }
            },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Spacing.minTouchTarget)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = type.icon(state),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppType.body,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = AppType.label,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            StatusBadge(state)

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "DeviceRow · three tiers", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun DeviceRowTiersPreview() = GalleryPreview(dark = true) {
    // Same row, same content, three volumes.
    DeviceRow("Living room lamp", DeviceType.LIGHT, DeviceState.ON, detail = "On for 20 min")
    DeviceRow(
        "Iron",
        DeviceType.APPLIANCE,
        DeviceState.ON,
        tier = PriorityTier.ATTENTION,
        detail = "8 min left of 30 min",
    )
    DeviceRow(
        "Iron",
        DeviceType.APPLIANCE,
        DeviceState.ERROR,
        tier = PriorityTier.CRITICAL,
        detail = "Maximum on time exceeded",
    )
}

@Preview(name = "DeviceRow · states", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun DeviceRowStatesPreview() = GalleryPreview(dark = true) {
    DeviceRow("Kitchen outlet", DeviceType.OUTLET, DeviceState.OFF)
    DeviceRow("Porch light", DeviceType.LIGHT, DeviceState.ON, detail = "On until 23:00")
    DeviceRow("Hallway switches", DeviceType.MULTI_SWITCH, DeviceState.ON, detail = "2 of 3 on")
    DeviceRow("Front camera", DeviceType.CAMERA, DeviceState.DISCONNECTED, detail = "Last seen 12 min ago")
}
