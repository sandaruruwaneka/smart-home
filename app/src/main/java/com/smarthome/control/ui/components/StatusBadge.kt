package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.rememberReducedMotion

/**
 * Section 8.1 — StatusBadge.
 *
 * A small pill showing ON / OFF / ERROR / DISCONNECTED. It carries an icon **and** a text
 * label, never colour alone: a user with deuteranopia reading this app at a glance gets
 * the state from the word and the glyph, and the colour is a third, redundant channel.
 *
 * The fill is the state colour at low opacity rather than solid, so a row of badges does
 * not out-shout the device names beside them. ERROR is the exception — it earns a solid
 * border because it is the one state the user must not scroll past.
 *
 * @param compact drops the text label to an icon-only dot for dense contexts such as a
 *   floor-plan legend. Use sparingly: the content description still names the state, but
 *   sighted users lose the redundant channel.
 */
@Composable
fun StatusBadge(
    state: DeviceState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()

    val target = when (state) {
        DeviceState.ON -> colors.stateOn
        DeviceState.OFF -> colors.stateOff
        DeviceState.ERROR -> colors.stateError
        DeviceState.DISCONNECTED -> colors.textSecondary
    }

    // Section 10: 200 ms colour cross-fade on state change, instant when the user has
    // asked for reduced motion.
    val accent by animateColorAsState(
        targetValue = if (enabled) target else target.copy(alpha = DisabledAlpha),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 200),
        label = "StatusBadge accent",
    )

    val fill = when (state) {
        DeviceState.DISCONNECTED -> colors.stateDisconnected
        else -> accent.copy(alpha = if (colors.isDark) 0.16f else 0.12f)
    }
    val contentAlpha = if (enabled) 1f else DisabledAlpha

    Row(
        modifier = modifier
            .background(fill, RoundedCornerShape(percent = 50))
            .stateBorder(
                color = accent.copy(alpha = if (state == DeviceState.ERROR) 1f else 0.4f),
                width = 1.dp,
                cornerRadius = 999.dp,
                dashed = state == DeviceState.DISCONNECTED,
            )
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp)
            // One node, one sentence. Without this the reader announces the icon and the
            // label as two separate items.
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = accent.copy(alpha = contentAlpha),
            modifier = Modifier.size(14.dp),
        )
        if (!compact) {
            Text(
                text = state.label,
                style = AppType.label,
                color = accent.copy(alpha = contentAlpha),
            )
        }
    }
}

/** Section 8 requires a disabled rendering of every component. 38 % holds 3:1 on both themes. */
internal const val DisabledAlpha = 0.38f

// ---------------------------------------------------------------------------
// Previews — all relevant states, both themes
// ---------------------------------------------------------------------------

@Preview(name = "StatusBadge · dark", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun StatusBadgePreviewDark() = GalleryPreview(dark = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceState.entries.forEach { StatusBadge(it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceState.entries.forEach { StatusBadge(it, enabled = false) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceState.entries.forEach { StatusBadge(it, compact = true) }
    }
}

@Preview(name = "StatusBadge · light", showBackground = true, backgroundColor = 0xFFF5F7F8)
@Composable
private fun StatusBadgePreviewLight() = GalleryPreview(dark = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceState.entries.forEach { StatusBadge(it) }
    }
}
