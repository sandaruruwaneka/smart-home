package com.smarthome.control.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 8.7 — AlertBanner.
 *
 * The Critical-tier, full-width banner: icon, one-line cause, one-line reason, relative
 * timestamp, and a single action.
 *
 * ### The rules that travel with this component
 *
 * **One at a time.** Only one Critical element may be visible at once. Multiple critical
 * events collapse into a single banner carrying a count and a link to the Alerts screen —
 * see [AlertBannerCollapsed]. Two red banners stacked do not communicate twice the
 * urgency; they communicate that red is ordinary.
 *
 * **Never swipe-dismissible.** There is no swipe handler here and there should not be
 * one. This banner clears when the underlying condition clears or when the user
 * acknowledges explicitly through [actionLabel]. A safety cutoff that can be flicked away
 * by accident while scrolling is a safety feature that does not work.
 *
 * **Always on top.** Callers place this above all other content in its container. The
 * component does not enforce that — it cannot see its siblings — so it is the screen's
 * job, and every screen prompt repeats it.
 *
 * ### Cause and reason are two lines, deliberately
 *
 * `Iron switched off automatically` answers "what happened to my thing", and
 * `Maximum active duration exceeded` answers "why". Collapsing them into one sentence
 * makes the user parse a clause to find out whether they need to act. Section 12: errors
 * state what happened and what to do, and never stay vague.
 *
 * @param cause what happened, in the user's terms
 * @param reason why it happened
 * @param timestamp relative, e.g. `2 min ago`
 * @param actionLabel the single action. Null renders an informational banner with no
 *   action, for a condition the user cannot act on yet.
 */
@Composable
fun AlertBanner(
    cause: String,
    reason: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = "Acknowledge",
    onAction: () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors

    PriorityContainer(
        tier = PriorityTier.CRITICAL,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Alert. $cause. $reason. $timestamp"
                // Announces itself when it appears, without the user having to be
                // focused on this part of the screen.
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = colors.stateError,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(cause, style = AppType.sectionHeader, color = colors.textPrimary)
                Text(reason, style = AppType.body, color = colors.textSecondary)
                Text(timestamp, style = AppType.label, color = colors.textSecondary)
            }

            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    shape = AppShapes.buttonSecondary,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.stateError),
                ) {
                    Text(actionLabel, style = AppType.label)
                }
            }
        }
    }
}

/**
 * The collapsed form, shown when more than one alert is outstanding.
 *
 * Section 5 requires multiple critical events to become one banner with a count and a
 * link to the Alerts screen. The count goes in the cause line rather than in a badge so
 * that the first thing read is the number of things wrong.
 */
@Composable
fun AlertBannerCollapsed(
    alertCount: Int,
    timestamp: String,
    modifier: Modifier = Modifier,
    onViewAll: () -> Unit = {},
) {
    AlertBanner(
        cause = "$alertCount ${plural(alertCount, "alert")} need attention",
        reason = "Devices were switched off automatically or reported a fault",
        timestamp = timestamp,
        modifier = modifier,
        actionLabel = "View all",
        onAction = onViewAll,
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "AlertBanner · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun AlertBannerPreviewDark() = GalleryPreview(dark = true) {
    AlertBanner(
        cause = "Iron switched off automatically",
        reason = "Maximum active duration exceeded",
        timestamp = "2 min ago",
    )
    AlertBannerCollapsed(alertCount = 3, timestamp = "Just now")
    AlertBanner(
        cause = "Kitchen camera is offline",
        reason = "The stream stopped responding",
        timestamp = "12 min ago",
        actionLabel = null,
    )
}

@Preview(name = "AlertBanner · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun AlertBannerPreviewLight() = GalleryPreview(dark = false) {
    AlertBanner(
        cause = "Iron switched off automatically",
        reason = "Maximum active duration exceeded",
        timestamp = "2 min ago",
    )
    AlertBannerCollapsed(alertCount = 3, timestamp = "Just now")
}
