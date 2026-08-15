package com.smarthome.control.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Outlet
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 8.10 — EmptyState.
 *
 * Icon, one line of copy, one action button. Required for floors, devices, alerts, and
 * reports.
 *
 * ### Copy is an invitation, not an apology
 *
 * `No floors yet. Add your first floor plan to place devices.` tells the user what is
 * true and what to do about it, in that order. It does not say "Oops!", it does not
 * apologise, and it does not explain that the list is empty because no data was returned.
 * Section 12: active voice, from the user's side of the screen.
 *
 * An empty alerts list is the one case with no action — nothing is wrong, and there is
 * nothing to add. [actionLabel] is null there, and the component renders copy alone
 * rather than inventing a button. See [EmptyStates] for the four canonical uses.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(26.dp),
            )
        }

        Text(
            text = message,
            style = AppType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null) {
            Button(
                onClick = onAction,
                // Pill for primary actions (section 7).
                shape = AppShapes.buttonPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.lg,
                    vertical = 12.dp,
                ),
            ) {
                Text(actionLabel, style = AppType.label)
            }
        }
    }
}

/**
 * The four empty states the app is required to have, with their copy fixed here.
 *
 * Keeping the strings in one place is what stops the floors screen saying "No floors yet"
 * and the devices screen saying "You haven't added any devices!" — same situation, two
 * voices.
 */
object EmptyStates {

    @Composable
    fun Floors(modifier: Modifier = Modifier, onAddFloor: () -> Unit = {}) = EmptyState(
        icon = Icons.Rounded.Layers,
        message = "No floors yet. Add your first floor plan to place devices.",
        modifier = modifier,
        actionLabel = "Add floor",
        onAction = onAddFloor,
    )

    @Composable
    fun Devices(modifier: Modifier = Modifier, onAddDevice: () -> Unit = {}) = EmptyState(
        icon = Icons.Rounded.Outlet,
        message = "This floor has no devices. Place one on the grid to start controlling it.",
        modifier = modifier,
        actionLabel = "Place device",
        onAction = onAddDevice,
    )

    /** No action: an empty alert history is the good outcome, not a task. */
    @Composable
    fun Alerts(modifier: Modifier = Modifier) = EmptyState(
        icon = Icons.Rounded.NotificationsNone,
        message = "No alerts. Every device has stayed within its limits.",
        modifier = modifier,
    )

    @Composable
    fun Reports(modifier: Modifier = Modifier, onViewFloors: () -> Unit = {}) = EmptyState(
        icon = Icons.Rounded.QueryStats,
        message = "No usage recorded yet. Turn a device on and its hours will appear here.",
        modifier = modifier,
        actionLabel = "Go to floors",
        onAction = onViewFloors,
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "EmptyState · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun EmptyStatePreviewDark() = GalleryPreview(dark = true) {
    EmptyStates.Floors()
    EmptyStates.Alerts()
}

@Preview(name = "EmptyState · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun EmptyStatePreviewLight() = GalleryPreview(dark = false) {
    EmptyStates.Devices()
    EmptyStates.Reports()
}
