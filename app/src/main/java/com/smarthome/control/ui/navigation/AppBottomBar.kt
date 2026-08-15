package com.smarthome.control.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.components.GalleryPreview
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme

/**
 * The four places the bottom bar goes.
 *
 * Four items, fixed, in the order the brief's screens are marked in: the house, what went
 * wrong, what it cost, and the settings. There is no "more" item and no fifth tab, because
 * a control panel whose navigation needs a drawer has already lost the two-second budget
 * the home screen is written against.
 */
enum class AppDestination(
    val label: String,
    /** Filled weight for the active item (master prompt section 9). */
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
) {
    Home("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Alerts("Alerts", Icons.Rounded.Notifications, Icons.Outlined.Notifications),
    Reports("Reports", Icons.Rounded.QueryStats, Icons.Outlined.QueryStats),
    Settings("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/**
 * The app's bottom navigation.
 *
 * Icon plus label on every item, never icon alone: four glyphs with no words is a puzzle
 * the user solves once per app and re-solves after every week away.
 *
 * @param unacknowledgedAlerts drives the badge on `Alerts`. Zero renders no badge at all —
 *   a permanent `0` badge is the same mistake as a permanently red errors tile.
 */
@Composable
fun AppBottomBar(
    current: AppDestination,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    unacknowledgedAlerts: Int = 0,
) {
    val colors = SmartHomeTheme.colors

    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.textSecondary,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    AlertBadge(
                        count = unacknowledgedAlerts.takeIf { destination == AppDestination.Alerts }
                            ?: 0,
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                destination.activeIcon
                            } else {
                                destination.inactiveIcon
                            },
                            // The label beneath says the name, and the badge carries its
                            // own description -- an icon description here would have the
                            // screen reader say "Alerts" three times per item.
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                label = { Text(destination.label, style = AppType.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                    // Surfaces separate by tone, not by a pill of tinted background
                    // (section 7). The tint on the icon is the whole indication.
                    indicatorColor = colors.surfaceVariant,
                ),
            )
        }
    }
}

/**
 * The count of outstanding alerts, or nothing when there are none.
 *
 * Capped at `9+`: past that the number stops being information the user acts on and starts
 * being a wide badge that pushes the icon off centre.
 */
@Composable
private fun AlertBadge(count: Int, content: @Composable () -> Unit) {
    if (count <= 0) {
        content()
        return
    }

    val colors = SmartHomeTheme.colors
    val spoken = "$count unacknowledged ${if (count == 1) "alert" else "alerts"}"

    BadgedBox(
        badge = {
            Badge(
                containerColor = colors.stateError,
                contentColor = colors.onStateError,
                modifier = Modifier.clearAndSetSemantics { },
            ) {
                Text(if (count > 9) "9+" else "$count", style = AppType.label)
            }
        },
        // Announced once, on the box, so the badge and the icon are one utterance.
        modifier = Modifier.semantics { contentDescription = spoken },
        content = { content() },
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "AppBottomBar · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun AppBottomBarPreviewDark() = GalleryPreview(dark = true) {
    AppBottomBar(current = AppDestination.Home, onSelect = {})
    AppBottomBar(current = AppDestination.Alerts, onSelect = {}, unacknowledgedAlerts = 2)
    AppBottomBar(current = AppDestination.Home, onSelect = {}, unacknowledgedAlerts = 14)
}

@Preview(name = "AppBottomBar · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun AppBottomBarPreviewLight() = GalleryPreview(dark = false) {
    AppBottomBar(current = AppDestination.Home, onSelect = {}, unacknowledgedAlerts = 1)
}
