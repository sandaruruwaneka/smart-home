package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlertRow(
    deviceName: String,
    reason: String,
    timestamp: String,
    type: AlertType,
    modifier: Modifier = Modifier,
    acknowledged: Boolean = true,
    /**
     * Changes identity when this alert arrived while the list was on screen, flashing the
     * row's border to `primary` once (screen prompt 09 section 8). The same convention the
     * dashboard uses for a device somebody else changed.
     */
    arrivalToken: Any? = null,
    /** Overrides the row's spoken description; the alerts screen composes a fuller one. */
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()

    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(arrivalToken) {
        if (arrivalToken != null) {
            flashing = true
            delay(ArrivalFlashMillis)
            flashing = false
        }
    }
    // Under reduced motion the colour is held rather than faded: the flash carries the
    // information that this row is new, so removing it entirely would remove the meaning.
    val flashBorder by animateColorAsState(
        targetValue = if (flashing) colors.primary.copy(alpha = FlashAlpha) else Color.Transparent,
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "AlertRow arrival",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .border(AppBorders.hairline, flashBorder, RoundedCornerShape(8.dp))
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription ?: buildString {
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

/** Section 8 fixes the arrival flash at 400 ms and 60 %. */
private const val ArrivalFlashMillis = 400L
private const val FlashAlpha = 0.6f

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
