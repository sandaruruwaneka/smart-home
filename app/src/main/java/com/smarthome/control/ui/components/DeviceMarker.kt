package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/** The default visual square. Kept at 40 dp so a 20-cell grid fits a 412 dp frame. */
val DefaultMarkerSize = 40.dp

/** Corner radius for chips and device markers (section 7), at the default size. */
private val MarkerRadius = 12.dp

/**
 * Section 8.2 — DeviceMarker.
 *
 * The token that sits in a floor-plan grid cell. State is encoded by **fill, border
 * weight, and border style** — not by glow:
 *
 * | State        | Encoding                                                          |
 * |--------------|-------------------------------------------------------------------|
 * | OFF          | Muted stateOff fill, 1 dp outline border, outlined icon           |
 * | ON           | stateOn at 20 % over surface, 1.5 dp stateOn border, filled icon   |
 * | ERROR        | surface fill, 1.5 dp stateError border, warning icon              |
 * | DISCONNECTED | stateDisconnected fill, 1 dp **dashed** border, connection-off icon|
 *
 * ### Why the glow is reserved
 *
 * A slow 2 s low-opacity amber pulse appears only on a hazard-class device running
 * against its `max_on_duration` ([hazardActive]). Because it is the only glow anywhere in
 * the system, it reads instantly as "this one needs your attention" even on a floor plan
 * holding twenty devices. Spending it on ordinary ON state would make it noise, and there
 * would then be nothing left to escalate to.
 *
 * The marker is 40 dp but its touch target is padded to 48 dp (section 2). The visual
 * size stays fixed so grid alignment does not depend on the touch target.
 *
 * @param hazardActive true only for an APPLIANCE currently running against its limit
 * @param selected the user has tapped this marker and its controls are open
 * @param onClick null renders a non-interactive marker, as used in the legend
 * @param size the visual square. Screen prompt 03 sizes markers from the floor plan's own
 *   cell size, so this varies with the grid and the zoom level; radius, icon and badge all
 *   scale from it. The touch target still grows to 48 dp underneath.
 * @param channelBadge the `2/3` a switch bank carries in its corner, giving how many of
 *   its channels are on. Null on every other type.
 * @param pendingWrite the app has written a new state and Firestore has not confirmed it
 *   yet. The marker shows the new state immediately with a half-strength border, so the
 *   tap feels instant without claiming more certainty than there is.
 * @param externalChangeToken changes identity when this device is changed by somebody
 *   *else* — the simulator, the worker, another phone. That flashes the border to
 *   `primary` once. A locally initiated change passes an unchanged token and does not
 *   flash: the user already knows what they touched.
 */
@Composable
fun DeviceMarker(
    type: DeviceType,
    state: DeviceState,
    modifier: Modifier = Modifier,
    deviceName: String = type.label,
    hazardActive: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = DefaultMarkerSize,
    channelBadge: String? = null,
    pendingWrite: Boolean = false,
    externalChangeToken: Any? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Everything inside scales with the marker so a 32 dp marker on a dense grid is the
    // same design, not a clipped version of a 40 dp one.
    val radius = (size * 0.3f).coerceAtMost(MarkerRadius)
    val iconSize = size * 0.5f

    // The external-change flash (screen prompt 03 section 8). Suppressed under reduced
    // motion, where a border that changes colour twice in half a second is exactly the
    // kind of movement the setting exists to remove.
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(externalChangeToken) {
        if (externalChangeToken != null && !reducedMotion) {
            flashing = true
            delay(ExternalFlashMillis)
            flashing = false
        }
    }

    val accent = when (state) {
        DeviceState.ON -> colors.stateOn
        DeviceState.OFF -> colors.stateOff
        DeviceState.ERROR -> colors.stateError
        DeviceState.DISCONNECTED -> colors.textSecondary
    }

    val fill = when (state) {
        DeviceState.OFF -> colors.stateOff.copy(alpha = if (colors.isDark) 0.22f else 0.16f)
        DeviceState.ON -> colors.stateOn.copy(alpha = 0.20f)
        DeviceState.ERROR -> colors.surface
        DeviceState.DISCONNECTED -> colors.stateDisconnected
    }

    val borderColor = when (state) {
        DeviceState.OFF -> colors.outline
        DeviceState.ON -> colors.stateOn
        DeviceState.ERROR -> colors.stateError
        // Not `outline`: on the dark theme outline (#2C383E) is darker than the
        // stateDisconnected fill (#3A464C) it would sit on, and the dashes disappear.
        // The dash is the non-colour half of this state's encoding (section 11), so it
        // has to out-contrast its own fill in both themes.
        DeviceState.DISCONNECTED -> colors.textSecondary
    }

    val borderWidth = when (state) {
        DeviceState.ON, DeviceState.ERROR -> AppBorders.emphasis
        else -> AppBorders.hairline
    }

    val iconTint = when (state) {
        DeviceState.OFF -> colors.textSecondary
        DeviceState.ON -> colors.stateOn
        DeviceState.ERROR -> colors.stateError
        DeviceState.DISCONNECTED -> colors.textSecondary
    }

    // 200 ms colour cross-fade on state change (section 10). Instant under reduced
    // motion; the colour still changes, only the transition is dropped.
    //
    // The flash and the pending-write treatment both ride this one animation rather than
    // painting a second border on top, so a marker that is confirmed mid-flash resolves
    // smoothly instead of jumping between two overlaid edges.
    val animatedBorder by animateColorAsState(
        targetValue = when {
            flashing -> colors.primary.copy(alpha = 0.6f)
            pendingWrite -> borderColor.copy(alpha = 0.5f)
            else -> borderColor
        },
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "DeviceMarker border",
    )
    val animatedIcon by animateColorAsState(
        targetValue = iconTint,
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "DeviceMarker icon",
    )

    // The reserved pulse. Only a hazard-class device running against its limit gets it,
    // and reduced motion turns it into a static emphasised border instead of removing the
    // signal altogether — the information must survive, only the movement goes.
    val showPulse = hazardActive && state == DeviceState.ON
    val pulseAlpha = if (showPulse && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "hazard pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "hazard pulse alpha",
        ).value
    } else {
        0f
    }

    val description = buildString {
        append(deviceName)
        append(", ")
        append(state.spoken)
        if (showPulse) append(", approaching its maximum on time")
        if (selected) append(", selected")
    }

    val clickable = onClick != null && enabled
    // Never negative: on a zoomed-in plan the marker can be larger than the minimum touch
    // target, and a negative padding would pull its neighbours' hit areas into it.
    val touchTargetPadding = ((Spacing.minTouchTarget - size) / 2).coerceAtLeast(0.dp)

    Box(
        modifier = modifier
            .then(
                if (clickable) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onClick?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            // Expands the hit area to 48 dp without moving the 40 dp visual (section 2).
            .padding(touchTargetPadding)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Pulse halo, drawn behind the marker so it never shifts the marker's own bounds.
        if (pulseAlpha > 0f) {
            Box(
                Modifier
                    .requiredSize(size + 8.dp)
                    .background(
                        colors.stateOn.copy(alpha = pulseAlpha * 0.5f),
                        RoundedCornerShape(radius + 4.dp),
                    ),
            )
        }

        // Selection ring, 4 dp outside the state border rather than on top of it, so the
        // two edges never compete: state is the inner edge, selection is the outer one.
        if (selected) {
            Box(
                Modifier
                    .requiredSize(size + 8.dp)
                    .border(2.dp, colors.primary, RoundedCornerShape(radius + 4.dp)),
            )
        }

        Box(
            modifier = Modifier
                .requiredSize(size)
                .alpha(if (enabled) 1f else DisabledAlpha)
                .background(fill, RoundedCornerShape(radius))
                // Pressed feedback is a tone shift, not a ripple — ripples on a dense
                // floor plan read as the whole grid flickering. Drawn between the fill
                // and the borders so it tints the face without dulling the state edge.
                .background(
                    if (pressed) colors.textPrimary.copy(alpha = 0.08f) else Color.Transparent,
                    RoundedCornerShape(radius),
                )
                .stateBorder(
                    color = animatedBorder,
                    // Reduced motion trades the pulse for a permanently emphasised border.
                    width = if (showPulse && reducedMotion) AppBorders.emphasis else borderWidth,
                    cornerRadius = radius,
                    dashed = state == DeviceState.DISCONNECTED,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state == DeviceState.ERROR) {
                    DeviceState.ERROR.icon
                } else if (state == DeviceState.DISCONNECTED) {
                    DeviceState.DISCONNECTED.icon
                } else {
                    type.icon(state)
                },
                contentDescription = null,
                tint = animatedIcon,
                modifier = Modifier.size(iconSize),
            )
        }

        // The switch bank's `2/3`. Sits on the corner of the marker, tinted amber only
        // when something is actually on -- an all-off gang plate is an ordinary OFF
        // device and its badge should not be the brightest thing in the cell.
        if (channelBadge != null && size >= 32.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .background(colors.surface, RoundedCornerShape(6.dp))
                    .border(AppBorders.hairline, colors.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 3.dp),
            ) {
                Text(
                    text = channelBadge,
                    style = AppType.label.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                    color = if (state == DeviceState.ON) colors.stateOn else colors.textSecondary,
                )
            }
        }
    }
}

/** How long the external-change flash holds before settling back (screen prompt 03 §8). */
private const val ExternalFlashMillis = 400L

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "DeviceMarker · all states", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun DeviceMarkerStatesPreview() = GalleryPreview(dark = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DeviceState.entries.forEach { state ->
            DeviceMarker(type = DeviceType.OUTLET, state = state, onClick = {})
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DeviceMarker(DeviceType.APPLIANCE, DeviceState.ON, hazardActive = true, onClick = {})
        DeviceMarker(DeviceType.LIGHT, DeviceState.ON, selected = true, onClick = {})
        DeviceMarker(DeviceType.CAMERA, DeviceState.ON, enabled = false, onClick = {})
        DeviceMarker(DeviceType.MULTI_SWITCH, DeviceState.OFF, onClick = {})
    }
}

@Preview(name = "DeviceMarker · types", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun DeviceMarkerTypesPreview() = GalleryPreview(dark = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DeviceType.entries.forEach { type ->
            DeviceMarker(type = type, state = DeviceState.ON, onClick = {})
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DeviceType.entries.forEach { type ->
            DeviceMarker(type = type, state = DeviceState.OFF, onClick = {})
        }
    }
}

@Preview(name = "DeviceMarker · light", showBackground = true, backgroundColor = 0xFFF5F7F8)
@Composable
private fun DeviceMarkerLightPreview() = GalleryPreview(dark = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DeviceState.entries.forEach { state ->
            DeviceMarker(type = DeviceType.LIGHT, state = state, onClick = {})
        }
    }
}
