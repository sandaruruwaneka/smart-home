package com.smarthome.control.ui.device

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.common.relativeTime
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.components.StatCard
import com.smarthome.control.ui.components.dashedBorder
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.stateChangeSpec

/**
 * Screen prompt 04 — the outlet control sheet.
 *
 * The user has tapped an outlet on the floor plan. They want to toggle it and be gone in
 * under three seconds, with a glance at how long it has been running today. Everything in
 * here is arranged around that: the control block is the biggest thing on the sheet, and
 * the usage figures are a caption to it rather than a report.
 *
 * ### The five slots are normative
 *
 * Identity, primary control, type-specific configuration, usage, metadata footer — in that
 * order, in every device sheet. The multi-switch channel list, the appliance's duration
 * picker and the light's schedule editor all land in slot three, which is empty here. That
 * is the whole reason this sheet is built before the other three: an outlet is the version
 * of the anatomy with nothing in the variable slot, so getting it right settles the
 * skeleton before there is anything complicated standing on it.
 *
 * ### The card repeats the marker's encoding
 *
 * Fill, border weight and border style say the same thing here as they do on the marker the
 * user just tapped — muted for off, amber-bordered for on, red for a fault, dashed for
 * offline. The sheet is a continuation of that tap, not a different place, and re-encoding
 * the same four states a second way would make the user learn them twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutletControlSheet(
    deviceId: String,
    onDismiss: () -> Unit,
    onMoveDevice: (deviceId: String) -> Unit,
    onViewHistory: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutletSheetViewModel = viewModel(
        factory = OutletSheetViewModel.factory(deviceId),
        key = deviceId,
    ),
) {
    val colors = SmartHomeTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    // The document has gone -- deleted from here, from another phone, or by the examiner in
    // the console. A sheet for a device that no longer exists has nothing to show and no
    // control that would work, so it closes.
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = AppShapes.bottomSheet,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        scrimColor = colors.background.copy(alpha = ScrimAlpha),
        dragHandle = { SheetDragHandle() },
    ) {
        OutletSheetContent(
            state = state,
            isOffline = rememberIsOffline(),
            onToggle = viewModel::toggle,
            onClearError = viewModel::clearError,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onMove = { onMoveDevice(deviceId) },
            onViewHistory = { onViewHistory(deviceId) },
            onRetry = viewModel::retry,
        )
    }
}

@Composable
internal fun OutletSheetContent(
    state: OutletSheetUiState,
    isOffline: Boolean,
    onToggle: () -> Unit,
    onClearError: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onViewHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // ---- slot 1: identity ------------------------------------------------
        IdentityRow(
            state = state,
            onRename = { renaming = true },
            onMove = onMove,
            onViewHistory = onViewHistory,
            onClearError = onClearError,
            onDelete = { confirmingDelete = true },
        )

        if (state.loadError != null) {
            SheetLoadFailure(message = state.loadError, onRetry = onRetry)
            return@Column
        }

        // ---- slot 2: primary control -----------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ControlCard(state = state, isOffline = isOffline, onToggle = onToggle)
            ControlStatusLine(state = state, isOffline = isOffline)
        }

        // ---- slot 3: type-specific configuration -----------------------------
        // Empty by design. An outlet configures nothing (`DeviceConfig.Outlet` is an empty
        // map), and the slot is left visibly empty rather than closed up so the sheets that
        // do fill it keep the same rhythm above and below.

        // ---- slot 4: usage ---------------------------------------------------
        UsageSection(state = state)

        // ---- slot 5: metadata footer -----------------------------------------
        SheetFooter(lastChangedMillis = state.lastChangedMillis, nowMillis = state.nowMillis)
    }

    if (renaming) {
        RenameDeviceDialog(
            currentName = state.deviceName,
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRename(it) },
        )
    }

    if (confirmingDelete) {
        SheetConfirmDialog(
            title = "Delete ${state.deviceName}?",
            body = "Its usage history goes with it. This can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; onDelete() },
        )
    }
}

// ---------------------------------------------------------------------------
// Slot 1 — identity
// ---------------------------------------------------------------------------

@Composable
private fun IdentityRow(
    state: OutletSheetUiState,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onViewHistory: () -> Unit,
    onClearError: () -> Unit,
    onDelete: () -> Unit,
) {
    SheetIdentity(name = state.deviceName, subtitle = state.locationLine) {
        SheetOverflowButton { dismiss ->
            SheetMenuItem("Rename") { dismiss(); onRename() }
            SheetMenuItem("Move to another cell") { dismiss(); onMove() }
            SheetMenuItem("View history") { dismiss(); onViewHistory() }
            // Only offered when there is a fault to clear. An action that is always present
            // and usually inert teaches the user to stop reading the menu.
            if (state.state == DeviceState.ERROR) {
                SheetMenuItem("Clear error") { dismiss(); onClearError() }
            }
            SheetMenuItem("Delete device", destructive = true) { dismiss(); onDelete() }
        }
    }
}

// ---------------------------------------------------------------------------
// Slot 2 — the primary control
// ---------------------------------------------------------------------------

/**
 * The reason the sheet was opened, and the largest thing on it.
 *
 * The whole 96 dp card is the touch target, not just the switch. The user's intent on
 * opening this sheet is unambiguous, and a 96 dp target hit at a glance beats a 52 dp one
 * hit carefully. The switch inside is drawn but not announced — it would otherwise be a
 * second interactive node saying the same thing as the card that contains it (section 10).
 */
@Composable
private fun ControlCard(
    state: OutletSheetUiState,
    isOffline: Boolean,
    onToggle: () -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val on = state.state == DeviceState.ON
    val enabled = state.canSwitch && !isOffline

    val accent = when (state.state) {
        DeviceState.ON -> colors.stateOn
        DeviceState.OFF -> colors.stateOff
        DeviceState.ERROR -> colors.stateError
        DeviceState.DISCONNECTED -> colors.textSecondary
    }
    val fill = when (state.state) {
        DeviceState.ON -> colors.stateOn.copy(alpha = 0.20f)
        DeviceState.OFF -> colors.surfaceVariant
        DeviceState.ERROR -> colors.surface
        DeviceState.DISCONNECTED -> colors.stateDisconnected
    }
    val borderWidth = when (state.state) {
        DeviceState.ON, DeviceState.ERROR -> AppBorders.emphasis
        else -> AppBorders.hairline
    }
    val borderColor = when (state.state) {
        DeviceState.ON -> colors.stateOn
        DeviceState.OFF -> colors.outline
        DeviceState.ERROR -> colors.stateError
        // Same reasoning as DeviceMarker: on the dark theme `outline` is darker than the
        // disconnected fill it sits on and the dashes vanish, and the dash is the
        // non-colour half of this state's encoding.
        DeviceState.DISCONNECTED -> colors.textSecondary
    }

    // The pending treatment. The card is already showing the target state -- Firestore's
    // local cache saw to that -- so the half opacity says "not confirmed" rather than
    // "not applied". Instant under reduced motion, which is what `stateChangeSpec` does.
    val cardAlpha by animateFloatAsState(
        targetValue = if (state.pendingWrite) PendingAlpha else 1f,
        animationSpec = stateChangeSpec(),
        label = "control card alpha",
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = stateChangeSpec(),
        label = "control card border",
    )

    val shape = RoundedCornerShape(CardRadius)
    val dashed = state.state == DeviceState.DISCONNECTED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ControlCardHeight)
            .alpha(cardAlpha)
            .background(fill, shape)
            .then(
                if (dashed) {
                    Modifier.dashedBorder(animatedBorder, borderWidth, CardRadius)
                } else {
                    Modifier.border(borderWidth, animatedBorder, shape)
                },
            )
            .toggleable(
                value = on,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = state.spokenControl
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = DeviceType.OUTLET.icon(state.state),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(ControlIconSize),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = state.stateLabel,
                style = AppType.display,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = on,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onStateOn,
                    checkedTrackColor = colors.stateOn,
                    checkedBorderColor = colors.stateOn,
                    uncheckedThumbColor = colors.stateOff,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.outline,
                ),
                // Decorative inside the card's own semantics.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }

        if (state.pendingWrite) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PendingBarHeight)
                    .align(Alignment.BottomCenter),
                color = colors.primary,
                trackColor = fill,
            )
        }
    }
}

/**
 * The one line under the card, and there is only ever one.
 *
 * Order matters. Being offline outranks a device fault, because it changes what the user
 * can do about it: a phone with no connection cannot control a healthy device either, and
 * telling somebody their outlet is broken when the real problem is the café wifi sends them
 * to the wrong place entirely.
 */
@Composable
private fun ControlStatusLine(state: OutletSheetUiState, isOffline: Boolean) {
    val colors = SmartHomeTheme.colors

    val (message, tint) = when {
        isOffline ->
            "You're offline. Reconnect to control this device." to colors.textSecondary

        state.actionError != null -> state.actionError to colors.stateError

        state.state == DeviceState.ERROR ->
            // The contract has no field for a fault's cause -- `status` carries the fault
            // and nothing carries the reason -- so this is the only honest line available.
            "The device reported a fault." to colors.stateError

        state.state == DeviceState.DISCONNECTED -> {
            val seen = state.lastChangedMillis
                ?.let { relativeTime(it, state.nowMillis).lowercase() }
                ?: "a while ago"
            "Last seen $seen." to colors.textSecondary
        }

        else -> return
    }

    Text(text = message, style = AppType.body, color = tint)
}

// ---------------------------------------------------------------------------
// Slot 4 — usage
// ---------------------------------------------------------------------------

@Composable
private fun UsageSection(state: OutletSheetUiState) {
    val colors = SmartHomeTheme.colors
    val usage = state.usage

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SheetSectionHeader("TODAY")

        if (usage == null) {
            // Section 5: one line, no empty chart. A 24-segment bar with nothing in it
            // looks like a rendering failure rather than a quiet day.
            NoUsageLine()
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatCard(
                value = usage.timeOnLabel,
                unit = "",
                label = "Time on",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = usage.periodCount.toString(),
                unit = "",
                label = "Switches",
                modifier = Modifier.weight(1f),
            )
        }

        DayTimeline(
            hourFractions = usage.hourFractions,
            contentDescription = usage.spokenSummary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

private val ControlCardHeight = 96.dp
private val ControlIconSize = 32.dp
private val CardRadius = 16.dp
private val PendingBarHeight = 2.dp
private const val PendingAlpha = 0.5f

// ---------------------------------------------------------------------------
// Artboards — the section 11 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_264_000_000L

/**
 * A day with six runs in it, ending in one that is still going.
 *
 * Written as fractions rather than derived from events so the artboards do not depend on a
 * clock; [OutletSheetStateTest] is where the derivation itself is checked.
 */
private val PreviewHours = List(HoursInDay) { hour ->
    when (hour) {
        7 -> 0.5f
        8 -> 1f
        9 -> 0.4f
        12 -> 0.75f
        13 -> 1f
        14 -> 0.3f
        18 -> 0.6f
        19 -> 1f
        20 -> 0.15f
        else -> 0f
    }
}

private val PreviewUsage = DayUsage(
    onSeconds = 4 * 3600L + 12 * 60L,
    periodCount = 6,
    hourFractions = PreviewHours,
)

private val PreviewOn = OutletSheetUiState(
    isLoading = false,
    deviceName = "Kitchen Outlet",
    locationLine = "Ground Floor · R2 C5",
    state = DeviceState.ON,
    lastChangedMillis = PreviewNow - 14 * 60_000L,
    usage = PreviewUsage,
    nowMillis = PreviewNow,
)

@Composable
private fun Artboard(state: OutletSheetUiState, dark: Boolean = true, isOffline: Boolean = false) {
    SmartHomeTheme(darkTheme = dark) {
        SheetArtboardFrame {
            OutletSheetContent(
                state = state,
                isOffline = isOffline,
                onToggle = {},
                onClearError = {},
                onRename = {},
                onDelete = {},
                onMove = {},
                onViewHistory = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Outlet sheet · ON with usage", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetOn() = Artboard(PreviewOn)

@Preview(name = "Outlet sheet · OFF with usage", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetOff() = Artboard(PreviewOn.copy(state = DeviceState.OFF))

@Preview(name = "Outlet sheet · no usage today", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetUnused() = Artboard(
    PreviewOn.copy(state = DeviceState.OFF, usage = null),
)

@Preview(name = "Outlet sheet · pending", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetPending() = Artboard(PreviewOn.copy(pendingWrite = true))

@Preview(name = "Outlet sheet · ERROR", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetError() = Artboard(PreviewOn.copy(state = DeviceState.ERROR))

@Preview(name = "Outlet sheet · DISCONNECTED", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetDisconnected() = Artboard(
    PreviewOn.copy(
        state = DeviceState.DISCONNECTED,
        lastChangedMillis = PreviewNow - 12 * 60_000L,
    ),
)

@Preview(name = "Outlet sheet · write failed", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetWriteFailed() = Artboard(
    PreviewOn.copy(actionError = OutletSheetViewModel.WriteFailed),
)

@Preview(name = "Outlet sheet · app offline", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetAppOffline() = Artboard(PreviewOn, isOffline = true)

@Preview(name = "Outlet sheet · ON, light", widthDp = 412, heightDp = 915)
@Composable
private fun OutletSheetLight() = Artboard(PreviewOn, dark = false)
