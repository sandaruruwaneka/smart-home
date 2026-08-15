package com.smarthome.control.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/** Alert type, matching the `alerts.type` enum in SCHEMA.md. */
enum class AlertType {
    MAX_DURATION_EXCEEDED,
    DEVICE_ERROR;

    val icon: ImageVector
        get() = when (this) {
            MAX_DURATION_EXCEEDED -> Icons.Rounded.TimerOff
            DEVICE_ERROR -> Icons.Rounded.ErrorOutline
        }
}

/**
 * Section 8.8 — AlertRow.
 *
 * The Normal-tier list item for alert history: icon, device name, reason, relative time.
 *
 * ### Why this is not a small AlertBanner
 *
 * History is a record, not an alarm. The events in this list have already happened and
 * have already been handled — the iron is off, the cutoff worked. Rendering them in
 * Critical treatment would mean a user opening the Alerts screen after a normal week
 * faces a wall of red describing a system that functioned correctly every time.
 *
 * So the row sits on the standard surface with a muted icon, and only the unacknowledged
 * marker distinguishes a live item from a settled one. The red in this app is reserved
 * for things that are wrong *now*.
 *
 * @param acknowledged when false, a small primary dot marks the row as still outstanding.
 *   A Critical AlertBanner is showing elsewhere for the same condition (SCHEMA.md
 *   section 8) — this dot is a cross-reference, not a second alarm.
 */
@Composable
fun AlertRow(
    deviceName: String,
    reason: String,
    timestamp: String,
    type: AlertType,
    modifier: Modifier = Modifier,
    acknowledged: Boolean = true,
) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$deviceName. $reason. $timestamp")
                    if (!acknowledged) append(". Not yet acknowledged")
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = null,
            // Muted, not red. The event is over.
            tint = colors.textSecondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = deviceName,
                style = AppType.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reason,
                style = AppType.label,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(text = timestamp, style = AppType.label, color = colors.textSecondary)
            if (!acknowledged) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(colors.primary, CircleShape),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "AlertRow · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun AlertRowPreviewDark() = GalleryPreview(dark = true) {
    AlertRow(
        deviceName = "Iron",
        reason = "Maximum active time exceeded",
        timestamp = "2 min ago",
        type = AlertType.MAX_DURATION_EXCEEDED,
        acknowledged = false,
    )
    AlertRow(
        deviceName = "Kitchen outlet",
        reason = "Device reported a fault",
        timestamp = "1 hr ago",
        type = AlertType.DEVICE_ERROR,
    )
    AlertRow(
        deviceName = "Water heater",
        reason = "Maximum active time exceeded",
        timestamp = "Yesterday",
        type = AlertType.MAX_DURATION_EXCEEDED,
    )
}

@Preview(name = "AlertRow · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun AlertRowPreviewLight() = GalleryPreview(dark = false) {
    AlertRow(
        deviceName = "Iron",
        reason = "Maximum active time exceeded",
        timestamp = "2 min ago",
        type = AlertType.MAX_DURATION_EXCEEDED,
        acknowledged = false,
    )
    AlertRow(
        deviceName = "Kitchen outlet",
        reason = "Device reported a fault",
        timestamp = "1 hr ago",
        type = AlertType.DEVICE_ERROR,
    )
}
