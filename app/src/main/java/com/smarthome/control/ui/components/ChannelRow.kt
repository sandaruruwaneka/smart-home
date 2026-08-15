package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion

/**
 * Section 8.4 — ChannelRow.
 *
 * One row of a multi-switch gang unit: channel name left, toggle right, 3 dp vertical
 * state-colour bar leading.
 *
 * The leading bar exists so a three-gang unit can be read down its left edge without
 * parsing three toggle positions. Toggles are ambiguous at a glance — the thumb's
 * position means nothing until you have located the track — while a column of amber and
 * slate bars is legible instantly.
 *
 * A channel can never be DISCONNECTED (SCHEMA.md section 6): a channel cannot lose
 * connection independently of the gang box it lives in. Passing that state here renders
 * the ERROR treatment, because a channel reporting it is a data fault worth showing
 * rather than hiding.
 *
 * @param name may be empty; SCHEMA.md specifies falling back to `Channel {index+1}`
 * @param index 0-based position on the physical wall plate. Rows are ordered by this,
 *   never alphabetically, because the order mirrors the real fixture the user is looking
 *   at.
 */
@Composable
fun ChannelRow(
    name: String,
    index: Int,
    state: DeviceState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit = {},
) {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()
    val displayName = name.ifBlank { "Channel ${index + 1}" }
    val isOn = state == DeviceState.ON

    val barColor = when (state) {
        DeviceState.ON -> colors.stateOn
        DeviceState.OFF -> colors.stateOff
        DeviceState.ERROR, DeviceState.DISCONNECTED -> colors.stateError
    }
    val animatedBar by animateColorAsState(
        targetValue = barColor,
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "ChannelRow bar",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = "$displayName, ${state.spoken}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier
                .width(AppBorders.stateBar)
                .height(28.dp)
                .background(animatedBar, RoundedCornerShape(2.dp)),
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = AppType.body,
                color = if (enabled) colors.textPrimary else colors.textPrimary.copy(alpha = DisabledAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The word is the non-colour half of the encoding (section 11). A user who
            // cannot separate amber from slate still reads "ON".
            Text(
                text = state.label,
                style = AppType.label,
                color = if (state == DeviceState.OFF) colors.textSecondary else animatedBar,
            )
        }

        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            enabled = enabled && state != DeviceState.ERROR,
            colors = SwitchDefaults.colors(
                // The track is amber when on, matching the state vocabulary rather than
                // M3's default primary. Primary means "interactive"; amber means
                // "drawing power", and this switch is reporting the second thing.
                checkedThumbColor = colors.onStateOn,
                checkedTrackColor = colors.stateOn,
                checkedBorderColor = colors.stateOn,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceVariant,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "ChannelRow · dark", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun ChannelRowPreviewDark() = GalleryPreview(dark = true) {
    ChannelRow("Ceiling light", index = 0, state = DeviceState.ON)
    ChannelRow("Wall lamp", index = 1, state = DeviceState.OFF)
    ChannelRow("", index = 2, state = DeviceState.OFF)
    ChannelRow("Extractor fan", index = 3, state = DeviceState.ERROR)
    ChannelRow("Porch light", index = 4, state = DeviceState.ON, enabled = false)
}

@Preview(name = "ChannelRow · light", showBackground = true, backgroundColor = 0xFFF5F7F8)
@Composable
private fun ChannelRowPreviewLight() = GalleryPreview(dark = false) {
    ChannelRow("Ceiling light", index = 0, state = DeviceState.ON)
    ChannelRow("Wall lamp", index = 1, state = DeviceState.OFF)
    ChannelRow("Extractor fan", index = 2, state = DeviceState.ERROR)
}
