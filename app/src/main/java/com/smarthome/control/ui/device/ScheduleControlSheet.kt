package com.smarthome.control.ui.device

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.data.model.TimeOfDay
import com.smarthome.control.ui.common.rememberIsOffline
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
import java.time.ZoneId

/**
 * Screen prompt 08 — the schedule editor.
 *
 * Two jobs, and the second is the one that usually goes wrong. Toggling a light is an
 * outlet sheet with a different icon; setting the window in which it manages *itself* is
 * where most schedule UIs hand the user two time pickers and leave them to do the
 * arithmetic. This sheet does the arithmetic first: the next-event line under the ring says
 * what happens next in words, and it is the most-read text on the screen.
 *
 * The ring is the signature element and the reason the layout works — see [WindowRing] for
 * why a circle rather than a bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleControlSheet(
    deviceId: String,
    onDismiss: () -> Unit,
    onMoveDevice: (deviceId: String) -> Unit,
    onViewHistory: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleSheetViewModel = viewModel(
        factory = ScheduleSheetViewModel.factory(deviceId),
        key = deviceId,
    ),
) {
    val colors = SmartHomeTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Skips the partial detent: the schedule block plus the ring is more than half a screen,
    // and section 2 is explicit that the window controls must not open below the fold.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.isMissing) { if (state.isMissing) onDismiss() }

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
        ScheduleSheetContent(
            state = state,
            isOffline = rememberIsOffline(),
            onToggle = viewModel::toggle,
            onScheduleEnabled = viewModel::setScheduleEnabled,
            onSetOnTime = viewModel::setOnTime,
            onSetOffTime = viewModel::setOffTime,
            onClearSchedule = viewModel::clearSchedule,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onMove = { onMoveDevice(deviceId) },
            onViewHistory = { onViewHistory(deviceId) },
            onRetry = viewModel::retry,
        )
    }
}

/** Which edge the inline picker is editing, if any. */
internal enum class EditingEdge { On, Off }

@Composable
internal fun ScheduleSheetContent(
    state: ScheduleSheetUiState,
    isOffline: Boolean,
    onToggle: () -> Unit,
    onScheduleEnabled: (Boolean) -> Unit,
    onSetOnTime: (TimeOfDay) -> Unit,
    onSetOffTime: (TimeOfDay) -> Unit,
    onClearSchedule: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onViewHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initialEditing: EditingEdgePreview? = null,
) {
    val colors = SmartHomeTheme.colors
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(initialEditing?.edge) }
    var draft by remember { mutableStateOf<TimeOfDay?>(initialEditing?.draft) }

    // What the ring should show *right now*, which while the picker is open is the value
    // under the user's thumb rather than the value on the server. This is the whole reason
    // the ring exists (section 4).
    val previewOn = if (editing == EditingEdge.On) draft ?: state.scheduleOn else state.scheduleOn
    val previewOff = if (editing == EditingEdge.Off) draft ?: state.scheduleOff else state.scheduleOff

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // ---- slot 1: identity ------------------------------------------------
        SheetIdentity(name = state.deviceName, subtitle = state.locationLine) {
            SheetOverflowButton { dismiss ->
                SheetMenuItem("Rename") { dismiss(); renaming = true }
                SheetMenuItem("Move to another cell") { dismiss(); onMove() }
                SheetMenuItem("Clear schedule") { dismiss(); confirmingClear = true }
                SheetMenuItem("View history") { dismiss(); onViewHistory() }
                SheetMenuItem("Delete device", destructive = true) { dismiss(); confirmingDelete = true }
            }
        }

        if (state.loadError != null) {
            SheetLoadFailure(message = state.loadError, onRetry = onRetry)
            return@Column
        }

        // ---- slot 2: primary control -----------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryCard(state = state, isOffline = isOffline, onToggle = onToggle)
            StatusLine(state = state, isOffline = isOffline)
        }

        // ---- slot 3: the schedule block --------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetSectionHeader("SCHEDULE", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.scheduleEnabled,
                    onCheckedChange = onScheduleEnabled,
                    enabled = state.hasWindow,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        checkedBorderColor = colors.primary,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface,
                        uncheckedBorderColor = colors.outline,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Schedule" },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (state.pendingWrite) PendingAlpha else 1f),
                contentAlignment = Alignment.Center,
            ) {
                WindowRing(
                    startFraction = previewOn?.let { it.minutesSinceMidnight / MinutesPerDay },
                    sweepFraction = if (previewOn != null && previewOff != null) {
                        windowMinutes(previewOn, previewOff) / MinutesPerDay
                    } else {
                        null
                    },
                    nowFraction = state.nowFraction,
                    enabled = state.scheduleEnabled,
                    insideWindow = state.isInsideWindow,
                    spokenDescription = state.ringSpoken,
                    overrideFraction = state.overrideFraction,
                    dimmed = !state.scheduleEnabled,
                ) {
                    RingTimes(
                        onTime = previewOn?.wireValue,
                        offTime = previewOff?.wireValue,
                        enabled = state.scheduleEnabled,
                    )
                }
            }

            if (state.pendingWrite) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PendingBarHeight),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant,
                )
            }

            state.timezoneCaption?.let {
                Text(
                    text = it,
                    style = AppType.label,
                    color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TimeCard(
                    label = "Turns on",
                    time = previewOn,
                    enabled = state.isReachable,
                    selected = editing == EditingEdge.On,
                    spoken = state.timeCardSpoken(isOnEdge = true),
                    onClick = { editing = EditingEdge.On; draft = state.scheduleOn },
                    modifier = Modifier.weight(1f),
                )
                TimeCard(
                    label = "Turns off",
                    time = previewOff,
                    enabled = state.isReachable,
                    selected = editing == EditingEdge.Off,
                    spoken = state.timeCardSpoken(isOnEdge = false),
                    onClick = { editing = EditingEdge.Off; draft = state.scheduleOff },
                    modifier = Modifier.weight(1f),
                )
            }

            editing?.let { edge ->
                InlineTimePicker(
                    initial = draft ?: TimeOfDay.of(18, 30),
                    onChange = { draft = it },
                    onCancel = { editing = null; draft = null },
                    onConfirm = { chosen ->
                        if (edge == EditingEdge.On) onSetOnTime(chosen) else onSetOffTime(chosen)
                        editing = null
                        draft = null
                    },
                )
            }

            state.sameTimeError?.let {
                Text(text = it, style = AppType.body, color = colors.stateError)
            }
            state.overnightHelper?.let {
                Text(text = it, style = AppType.label, color = colors.textSecondary)
            }
            state.shortWindowWarning?.let {
                Text(text = it, style = AppType.label, color = colors.stateOn)
            }

            // The line the user actually came for. A polite live region, so a screen reader
            // reports the change at the next pause rather than interrupting.
            Text(
                text = state.nextEventLine,
                style = AppType.body,
                color = colors.textPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // ---- slot 4: usage ---------------------------------------------------
        UsageSection(state = state, previewOn = previewOn, previewOff = previewOff)

        // ---- slot 5: metadata footer -----------------------------------------
        state.footerLine?.let {
            Text(text = it, style = AppType.label, color = colors.textSecondary)
        }
    }

    if (renaming) {
        RenameDeviceDialog(
            currentName = state.deviceName,
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRename(it) },
        )
    }

    if (confirmingClear) {
        SheetConfirmDialog(
            title = "Clear this schedule?",
            body = "Both times are removed and the schedule switches off. " +
                "You can set it again at any time.",
            confirmLabel = "Clear",
            onDismiss = { confirmingClear = false },
            onConfirm = { confirmingClear = false; onClearSchedule() },
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
// Slot 2
// ---------------------------------------------------------------------------

@Composable
private fun PrimaryCard(
    state: ScheduleSheetUiState,
    isOffline: Boolean,
    onToggle: () -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val on = state.isOn
    val enabled = state.isReachable && !isOffline

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
    val borderColor = when (state.state) {
        DeviceState.ON -> colors.stateOn
        DeviceState.OFF -> colors.outline
        DeviceState.ERROR -> colors.stateError
        DeviceState.DISCONNECTED -> colors.textSecondary
    }
    val borderWidth = when (state.state) {
        DeviceState.ON, DeviceState.ERROR -> AppBorders.emphasis
        else -> AppBorders.hairline
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (state.pendingWrite) PendingAlpha else 1f,
        animationSpec = stateChangeSpec(),
        label = "primary card alpha",
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = stateChangeSpec(),
        label = "primary card border",
    )

    val shape = RoundedCornerShape(CardRadius)
    val dashed = state.state == DeviceState.DISCONNECTED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
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
                contentDescription = "${state.deviceName}, ${state.state.spoken}." +
                    (state.chipLabel?.let { " $it." } ?: "")
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = DeviceType.LIGHT.icon(state.state),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(IconSize),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = if (state.state == DeviceState.DISCONNECTED) "OFFLINE" else state.state.label,
                style = AppType.display,
                color = accent,
            )
            Spacer(Modifier.weight(1f))

            // The chip is the whole of section 6's visible half: a light that flips back at
            // the next edge with nothing on screen explaining why reads as a bug.
            state.chipLabel?.let { label ->
                Text(
                    text = label,
                    style = AppType.label,
                    color = colors.stateOn,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }

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
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun StatusLine(state: ScheduleSheetUiState, isOffline: Boolean) {
    val colors = SmartHomeTheme.colors

    val (message, tint) = when {
        // The schedule runs server-side, so being offline genuinely does not stop it. Say
        // so rather than implying the feature is down with the connection.
        isOffline -> "You're offline. The schedule still runs." to colors.textSecondary
        state.actionError != null -> state.actionError to colors.stateError
        state.state == DeviceState.ERROR -> "The device reported a fault." to colors.stateError
        state.state == DeviceState.DISCONNECTED ->
            "The schedule will apply when this light reconnects." to colors.textSecondary
        else -> return
    }

    Text(text = message, style = AppType.body, color = tint)
}

// ---------------------------------------------------------------------------
// Slot 3 controls
// ---------------------------------------------------------------------------

@Composable
private fun TimeCard(
    label: String,
    time: TimeOfDay?,
    enabled: Boolean,
    selected: Boolean,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Column(
        modifier = modifier
            .background(colors.surfaceVariant, AppShapes.card)
            .border(
                if (selected) AppBorders.selected else AppBorders.hairline,
                if (selected) colors.primary else colors.outline,
                AppShapes.card,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, style = AppType.label, color = colors.textSecondary)
        Text(
            text = time?.wireValue ?: "--:--",
            style = AppType.display,
            color = if (enabled) colors.textPrimary else colors.textSecondary,
        )
    }
}

/**
 * The picker, inline rather than in a dialog.
 *
 * A dialog would cover the ring, and the ring updating live as the dial turns is the entire
 * argument for having a ring — section 2 expands the sheet to its large detent for exactly
 * this. 24-hour format throughout: the storage is `"HH:mm"`, the simulator shows the same,
 * and rendering `18:30` as `6:30 PM` invites transcription errors when comparing the two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineTimePicker(
    initial: TimeOfDay,
    onChange: (TimeOfDay) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (TimeOfDay) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    // Reports every movement of the dial upward, so the ring redraws before the user
    // commits rather than after.
    LaunchedEffect(pickerState.hour, pickerState.minute) {
        onChange(TimeOfDay.of(pickerState.hour, pickerState.minute))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TimePicker(state = pickerState)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onCancel) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
            TextButton(onClick = { onConfirm(TimeOfDay.of(pickerState.hour, pickerState.minute)) }) {
                Text("Set", style = AppType.label, color = colors.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Slot 4
// ---------------------------------------------------------------------------

@Composable
private fun UsageSection(
    state: ScheduleSheetUiState,
    previewOn: TimeOfDay?,
    previewOff: TimeOfDay?,
) {
    val usage = state.usage

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SheetSectionHeader("TODAY")

        if (usage == null) {
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
                value = state.scheduleRunsToday.toString(),
                unit = "",
                label = "Schedule runs",
                modifier = Modifier.weight(1f),
            )
        }

        DayTimeline(
            hourFractions = usage.hourFractions,
            contentDescription = usage.spokenSummary,
            scheduledWindow = if (state.scheduleEnabled && previewOn != null && previewOff != null) {
                (previewOn.minutesSinceMidnight / MinutesPerDay) to
                    (windowMinutes(previewOn, previewOff) / MinutesPerDay)
            } else {
                null
            },
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

private val CardHeight = 96.dp
private val IconSize = 32.dp
private val CardRadius = 16.dp
private val PendingBarHeight = 2.dp
private const val PendingAlpha = 0.5f
private const val MinutesPerDay = 24f * 60f

/** Lets an artboard open the picker, which is otherwise local interaction state. */
internal data class EditingEdgePreview(val edge: EditingEdge, val draft: TimeOfDay?) {
    companion object {
        fun onEdge(draft: TimeOfDay) = EditingEdgePreview(EditingEdge.On, draft)
    }
}

// ---------------------------------------------------------------------------
// Artboards — the section 14 deliverable
// ---------------------------------------------------------------------------

private val PreviewZone: ZoneId = ZoneId.of("Asia/Colombo")

/** 19:45 in Colombo on 15 August 2026 — inside an 18:30–23:00 window. */
private const val PreviewNow = 1_755_268_500_000L

private val PreviewHours = List(HoursInDay) { hour ->
    when (hour) {
        18 -> 0.5f
        19 -> 1f
        20 -> 1f
        21 -> 0.4f
        else -> 0f
    }
}

private val PreviewUsage = DayUsage(
    onSeconds = 2 * 3600L + 54 * 60L,
    periodCount = 2,
    hourFractions = PreviewHours,
)

private val PreviewBase = ScheduleSheetUiState(
    isLoading = false,
    deviceName = "Porch Light",
    locationLine = "Ground Floor · R1 C2",
    state = DeviceState.ON,
    scheduleEnabled = true,
    scheduleOn = TimeOfDay.of(18, 30),
    scheduleOff = TimeOfDay.of(23, 0),
    lastChangedMillis = PreviewNow - 75 * 60_000L,
    lastChangedBy = com.smarthome.control.data.model.ChangeSource.WORKER,
    usage = PreviewUsage,
    scheduleRunsToday = 1,
    nowMillis = PreviewNow,
    zone = PreviewZone,
)

@Composable
private fun Artboard(
    state: ScheduleSheetUiState,
    dark: Boolean = true,
    isOffline: Boolean = false,
    editing: EditingEdgePreview? = null,
) {
    SmartHomeTheme(darkTheme = dark) {
        SheetArtboardFrame {
            ScheduleSheetContent(
                state = state,
                isOffline = isOffline,
                onToggle = {},
                onScheduleEnabled = {},
                onSetOnTime = {},
                onSetOffTime = {},
                onClearSchedule = {},
                onRename = {},
                onDelete = {},
                onMove = {},
                onViewHistory = {},
                onRetry = {},
                initialEditing = editing,
            )
        }
    }
}

@Preview(name = "Schedule · off", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleOffPreview() = Artboard(
    PreviewBase.copy(scheduleEnabled = false, state = DeviceState.OFF),
)

@Preview(name = "Schedule · on, outside window", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleOutsidePreview() = Artboard(
    // 14:00 Colombo — five hours before the window opens.
    PreviewBase.copy(state = DeviceState.OFF, nowMillis = PreviewNow - 5 * 3600_000L - 45 * 60_000L),
)

@Preview(name = "Schedule · on, inside window", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleInsidePreview() = Artboard(PreviewBase)

@Preview(name = "Schedule · overnight window", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleOvernightPreview() = Artboard(
    PreviewBase.copy(scheduleOn = TimeOfDay.of(22, 0), scheduleOff = TimeOfDay.of(6, 0)),
)

@Preview(name = "Schedule · manually overridden", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleOverriddenPreview() = Artboard(
    // Inside the window but switched off by hand: the schedule wants ON, the light is OFF.
    PreviewBase.copy(state = DeviceState.OFF),
)

@Preview(name = "Schedule · picker open, ring live", widthDp = 412, heightDp = 1200)
@Composable
private fun SchedulePickerPreview() = Artboard(
    PreviewBase,
    editing = EditingEdgePreview.onEdge(TimeOfDay.of(20, 15)),
)

@Preview(name = "Schedule · offline", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleOfflinePreview() = Artboard(PreviewBase, isOffline = true)

@Preview(name = "Schedule · inside window, light", widthDp = 412, heightDp = 1000)
@Composable
private fun ScheduleLightPreview() = Artboard(PreviewBase, dark = false)
