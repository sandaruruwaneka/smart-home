package com.smarthome.control.ui.device

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.common.relativeTime
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.components.CountdownRing
import com.smarthome.control.ui.components.PriorityContainer
import com.smarthome.control.ui.components.StatCard
import com.smarthome.control.ui.components.dashedBorder
import com.smarthome.control.ui.components.formatDuration
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion
import com.smarthome.control.ui.theme.stateChangeSpec

/**
 * Screen prompt 07 — the hazard device config sheet.
 *
 * The one screen where the brief's safety requirement becomes something a person can look
 * at. Everything else in the app can be merely competent; this has to be convincing.
 *
 * ### Slot 3 changes completely between OFF and ON
 *
 * It is the only sheet in the app that does this. While the device is off, slot 3 is the
 * duration picker and the primary control is the usual switch card. While it is on, the
 * countdown ring takes the space the card would have had and the switch becomes a plain
 * button underneath. That inversion is deliberate: when a fire-risk appliance is running,
 * the time remaining *is* the primary information, and leaving it below a switch would be
 * the wrong priority on the one screen that cannot afford one.
 *
 * ### The cutoff gap is shown, not hidden
 *
 * The worker ticks every 60 seconds, so the real cutoff lands up to a minute after the
 * countdown hits zero. Rather than let the ring sit at `00:00` against a device that is
 * plainly still on — which reads as a feature that failed — it switches to an indeterminate
 * sweep and says `Switching off…` until the worker's write actually arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardControlSheet(
    deviceId: String,
    onDismiss: () -> Unit,
    onMoveDevice: (deviceId: String) -> Unit,
    onViewHistory: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HazardSheetViewModel = viewModel(
        factory = HazardSheetViewModel.factory(deviceId),
        key = deviceId,
    ),
) {
    val colors = SmartHomeTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        HazardSheetContent(
            state = state,
            isOffline = rememberIsOffline(),
            onToggle = viewModel::toggle,
            onSetLimit = viewModel::setLimit,
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
internal fun HazardSheetContent(
    state: HazardSheetUiState,
    isOffline: Boolean,
    onToggle: () -> Unit,
    onSetLimit: (Long) -> Unit,
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
    var editingLimitWhileOn by remember { mutableStateOf(false) }
    var pendingShortLimit by remember { mutableStateOf<Long?>(null) }

    val applyLimit: (Long) -> Unit = { seconds ->
        // Section 6: shortening below what has already elapsed is legitimate but has to be
        // confirmed. Cutting off a running iron on a stray tap is the outcome this exists
        // to prevent.
        if (state.isOn && wouldCutOffImmediately(seconds, state.elapsedSeconds)) {
            pendingShortLimit = seconds
        } else {
            onSetLimit(seconds)
        }
    }

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
                SheetMenuItem("View history") { dismiss(); onViewHistory() }
                if (state.state == DeviceState.ERROR) {
                    SheetMenuItem("Clear error") { dismiss(); onClearError() }
                }
                SheetMenuItem("Delete device", destructive = true) { dismiss(); confirmingDelete = true }
                // Section 9: there is no `Disable safety cutoff`, at any limit, for any
                // device. Offering an off-switch for the feature the brief describes as
                // protecting life and property would undercut the whole project.
            }
        }

        if (state.loadError != null) {
            SheetLoadFailure(message = state.loadError, onRetry = onRetry)
            return@Column
        }

        state.cutoffNotice?.let { notice ->
            CutoffNotice(text = notice, atMillis = state.lastCutoffMillis, state = state)
        }

        if (state.isCounting) {
            // ---- slots 2 and 3, inverted: the ring is the headline ------------
            CountdownBlock(state = state, isOffline = isOffline)

            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isReachable && !isOffline,
                shape = AppShapes.buttonPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text("Turn off", style = AppType.sectionHeader)
            }

            state.runFooter?.let {
                Text(
                    text = it,
                    style = AppType.label,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            // Changing the limit mid-run is allowed, but it is not the reason the sheet is
            // open, so it stays behind one tap.
            TextButton(
                onClick = { editingLimitWhileOn = !editingLimitWhileOn },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = if (editingLimitWhileOn) "Done" else "Change",
                    style = AppType.label,
                    color = colors.primary,
                )
            }

            if (editingLimitWhileOn) {
                DurationPicker(
                    selectedSeconds = state.maxOnSeconds,
                    enabled = !isOffline,
                    onSelect = applyLimit,
                )
            }
        } else {
            // ---- slot 2: the usual control card ------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ApplianceCard(state = state, isOffline = isOffline, onToggle = onToggle)
                StatusLine(state = state, isOffline = isOffline)
            }

            // ---- slot 3: the duration picker ---------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SheetSectionHeader("MAXIMUM ON TIME")
                DurationPicker(
                    selectedSeconds = state.maxOnSeconds,
                    // Section 5: a disconnected device still takes configuration. The limit
                    // applies when it comes back, and the worker enforces it server-side
                    // either way.
                    enabled = state.state != DeviceState.ERROR,
                    onSelect = applyLimit,
                )
                Text(
                    text = state.helperLine,
                    style = AppType.label,
                    // The line that asks for a limit is the one thing standing between the
                    // user and a usable device, so it gets the attention colour.
                    color = if (state.canSwitchOn) colors.textSecondary else colors.stateOn,
                )
            }
        }

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

    pendingShortLimit?.let { seconds ->
        SheetConfirmDialog(
            title = "Switch off almost immediately?",
            body = cutOffWarning(seconds, state.elapsedSeconds ?: 0L),
            confirmLabel = "Set anyway",
            onDismiss = { pendingShortLimit = null },
            onConfirm = { pendingShortLimit = null; onSetLimit(seconds) },
        )
    }
}

// ---------------------------------------------------------------------------
// The ring
// ---------------------------------------------------------------------------

@Composable
private fun CountdownBlock(state: HazardSheetUiState, isOffline: Boolean) {
    val colors = SmartHomeTheme.colors
    val remaining = state.remainingSeconds ?: 0L
    val limit = state.maxOnSeconds ?: 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Section 5: offline the ring keeps counting, dimmed, because the worker runs
            // server-side and the guarantee genuinely holds while the phone is away.
            .alpha(if (isOffline) OfflineAlpha else if (state.pendingWrite) PendingAlpha else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.isExpired) {
            SwitchingOffRing()
        } else {
            Box(contentAlignment = Alignment.Center) {
                CountdownRing(
                    elapsedSeconds = state.elapsedSeconds ?: 0L,
                    maxOnSeconds = limit,
                    size = RingSize,
                    strokeWidth = RingStroke,
                    showLabel = false,
                    depleting = true,
                    pulse = true,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDuration(remaining),
                        style = AppType.numericLarge.copy(fontSize = RingFigureSize),
                        color = if (state.inFinalTenth) colors.stateError else colors.textPrimary,
                    )
                    Text(text = "remaining", style = AppType.label, color = colors.textSecondary)
                }
            }
        }

        state.limitCaption?.let {
            Text(text = it, style = AppType.label, color = colors.textSecondary)
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

        if (isOffline) {
            Text(
                text = "You're offline. This device will still switch off on schedule.",
                style = AppType.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // The two announcements section 11 asks for. The ring's own description is polite;
        // crossing into the final tenth is assertive, because it is news.
        Box(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = state.finalTenthAnnouncement ?: state.ringSpoken
                liveRegion = if (state.finalTenthAnnouncement != null) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        )
    }
}

/**
 * The indeterminate sweep for the gap between zero and the worker's write.
 *
 * Deliberately not a countdown at a standstill. A ring frozen on `00:00` while the iron is
 * visibly still on is the app claiming a failure it has not had; a moving sweep says "this
 * is in hand", which is the truth — the write is on its way.
 */
@Composable
private fun SwitchingOffRing() {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "switching off")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SweepMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "switching off sweep",
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(RingSize)) {
            val strokePx = RingStroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = colors.outline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.stateError,
                startAngle = angle - 90f,
                sweepAngle = IndeterminateSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "Switching off…",
            style = AppType.sectionHeader,
            color = colors.stateError,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Slot 3 — the picker
// ---------------------------------------------------------------------------

/**
 * Five chips, one radio group (section 11) — not five independent buttons, because exactly
 * one of them is true at a time and a screen reader should say so.
 */
@Composable
private fun DurationPicker(
    selectedSeconds: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var showCustom by remember { mutableStateOf(false) }
    val isCustom = selectedSeconds != null && selectedSeconds !in DurationPresets

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DurationPresets.forEach { seconds ->
            DurationChip(
                label = presetLabel(seconds),
                selected = selectedSeconds == seconds,
                enabled = enabled,
                onClick = { onSelect(seconds) },
                modifier = Modifier.weight(1f),
            )
        }
        DurationChip(
            label = "Custom",
            selected = isCustom,
            enabled = enabled,
            onClick = { showCustom = true },
            modifier = Modifier.weight(1.4f),
        )
    }

    if (showCustom) {
        CustomLimitDialog(
            initialSeconds = selectedSeconds ?: DurationPresets.last(),
            onDismiss = { showCustom = false },
            onConfirm = { showCustom = false; onSelect(it) },
        )
    }
}

@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Box(
        modifier = modifier
            .height(ChipHeight)
            .background(
                color = if (selected) colors.primary else colors.surfaceVariant,
                shape = AppShapes.chip,
            )
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(AppBorders.hairline, colors.outline, AppShapes.chip)
                },
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else DisabledAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppType.label,
            color = if (selected) colors.onPrimary else colors.textPrimary,
        )
    }
}

/**
 * The custom limit, as two steppers rather than a wheel.
 *
 * Section 3 asks for a wheel picker; Compose has no stock one and hand-rolling a fling-able
 * wheel for a field most users will never open would be a poor trade. The steppers hit the
 * same range with the same ceiling stated, and they are usable one-handed.
 */
@Composable
private fun CustomLimitDialog(
    initialSeconds: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var hours by remember { mutableStateOf((initialSeconds / 3600).toInt()) }
    var minutes by remember { mutableStateOf(((initialSeconds % 3600) / 60).toInt()) }
    val total = (hours * 3600L + minutes * 60L)
    val valid = total in CustomLimitRange

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Custom limit", style = AppType.sectionHeader) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StepperBox("Hours", hours, 0, 4) { hours = it }
                    StepperBox("Minutes", minutes, 0, 59) { minutes = it }
                }
                Text(
                    // The ceiling is stated rather than enforced silently: a user who wants
                    // six hours should be told why they cannot have it.
                    text = "Between 1 minute and 4 hours. Longer than that and the cutoff " +
                        "stops being a safeguard.",
                    style = AppType.label,
                    color = if (valid) colors.textSecondary else colors.stateError,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total) }, enabled = valid) {
                Text(
                    "Set",
                    style = AppType.label,
                    color = if (valid) colors.primary else colors.outline,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun StepperBox(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val colors = SmartHomeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−", style = AppType.numeric, color = colors.textPrimary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = AppType.numeric, color = colors.textPrimary)
            Text(label, style = AppType.label, color = colors.textSecondary)
        }
        TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Text("+", style = AppType.numeric, color = colors.textPrimary)
        }
    }
}

// ---------------------------------------------------------------------------
// Slot 2 while off, and the lines beneath
// ---------------------------------------------------------------------------

@Composable
private fun ApplianceCard(state: HazardSheetUiState, isOffline: Boolean, onToggle: () -> Unit) {
    val colors = SmartHomeTheme.colors
    val disconnected = state.state == DeviceState.DISCONNECTED
    val faulted = state.state == DeviceState.ERROR
    val enabled = state.isReachable && !isOffline && state.canSwitchOn

    val accent = when {
        faulted -> colors.stateError
        disconnected -> colors.textSecondary
        else -> colors.stateOff
    }
    val fill = when {
        faulted -> colors.surface
        disconnected -> colors.stateDisconnected
        else -> colors.surfaceVariant
    }
    val shape = RoundedCornerShape(CardRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .alpha(if (state.pendingWrite) PendingAlpha else 1f)
            .background(fill, shape)
            .then(
                when {
                    disconnected -> Modifier.dashedBorder(colors.textSecondary, AppBorders.hairline, CardRadius)
                    faulted -> Modifier.border(AppBorders.emphasis, colors.stateError, shape)
                    else -> Modifier.border(AppBorders.hairline, colors.outline, shape)
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${state.deviceName}, ${state.state.spoken}."
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = DeviceType.APPLIANCE.icon(state.state),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(IconSize),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = when {
                    faulted -> "ERROR"
                    disconnected -> "OFFLINE"
                    else -> "OFF"
                },
                style = AppType.display,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = false,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onStateOn,
                    checkedTrackColor = colors.stateOn,
                    uncheckedThumbColor = colors.stateOff,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.outline,
                ),
            )
        }
    }
}

@Composable
private fun StatusLine(state: HazardSheetUiState, isOffline: Boolean) {
    val colors = SmartHomeTheme.colors

    val (message, tint) = when {
        isOffline -> "You're offline. Reconnect to control this device." to colors.textSecondary
        state.actionError != null -> state.actionError to colors.stateError
        state.state == DeviceState.ERROR -> "The device reported a fault." to colors.stateError
        state.state == DeviceState.DISCONNECTED -> {
            val seen = state.lastChangedMillis
                ?.let { relativeTime(it, state.nowMillis).lowercase() } ?: "a while ago"
            "Last seen $seen." to colors.textSecondary
        }
        else -> return
    }

    Text(text = message, style = AppType.body, color = tint)
}

/** The Critical-tier line that stays up until the device is used again (section 5). */
@Composable
private fun CutoffNotice(text: String, atMillis: Long?, state: HazardSheetUiState) {
    val colors = SmartHomeTheme.colors

    PriorityContainer(
        tier = PriorityTier.CRITICAL,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${state.deviceName}. $text"
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(text = text, style = AppType.body, color = colors.stateError)
            atMillis?.let {
                Text(
                    text = relativeTime(it, state.nowMillis),
                    style = AppType.label,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Slot 4
// ---------------------------------------------------------------------------

@Composable
private fun UsageSection(state: HazardSheetUiState) {
    val colors = SmartHomeTheme.colors
    val usage = state.usage

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SheetSectionHeader("TODAY")

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatCard(
                value = usage?.timeOnLabel ?: "0m",
                unit = "",
                label = "Time on",
                modifier = Modifier.weight(1f),
            )
            // Section 7: above zero this card rises to Attention tier. A device that keeps
            // hitting its limit is telling the user something, and a plain number would let
            // them read past it.
            PriorityContainer(
                tier = if (state.autoCutoffsToday > 0) PriorityTier.ATTENTION else PriorityTier.NORMAL,
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = state.autoCutoffsToday.toString(),
                        style = AppType.numericLarge,
                        color = if (state.autoCutoffsToday > 0) colors.stateOn else colors.textPrimary,
                    )
                    Text(text = "Auto cutoffs", style = AppType.label, color = colors.textSecondary)
                }
            }
        }

        if (usage == null) {
            NoUsageLine()
        } else {
            DayTimeline(
                hourFractions = usage.hourFractions,
                contentDescription = usage.spokenSummary +
                    if (state.autoCutoffsToday > 0) {
                        " Switched off automatically ${state.autoCutoffsToday} times."
                    } else {
                        ""
                    },
                cutoffHours = state.cutoffHours,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

private val RingSize = 160.dp
private val RingStroke = 10.dp
private val RingFigureSize = 32.sp
private val CardHeight = 96.dp
private val CardRadius = 16.dp
private val IconSize = 32.dp
private val ChipHeight = 44.dp
private val PendingBarHeight = 2.dp
private const val PendingAlpha = 0.5f
private const val OfflineAlpha = 0.6f
private const val DisabledAlpha = 0.5f
private const val SweepMillis = 1200
private const val IndeterminateSweep = 90f

// ---------------------------------------------------------------------------
// Artboards — the section 12 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_264_000_000L

private val PreviewUsage = DayUsage(
    onSeconds = 1 * 3600L + 5 * 60L,
    periodCount = 3,
    hourFractions = List(HoursInDay) { hour ->
        when (hour) {
            9 -> 0.5f
            10 -> 0.4f
            13 -> 0.25f
            else -> 0f
        }
    },
)

private val OffNoLimit = HazardSheetUiState(
    isLoading = false,
    deviceName = "Bedroom Iron",
    locationLine = "First Floor · R3 C7",
    state = DeviceState.OFF,
    maxOnSeconds = null,
    lastChangedMillis = PreviewNow - 2 * 60 * 60_000L,
    usage = PreviewUsage,
    nowMillis = PreviewNow,
)

private val OffWithLimit = OffNoLimit.copy(maxOnSeconds = 30 * 60L, autoCutoffsToday = 2, cutoffHours = setOf(10, 13))

private val RunningNormal = OffWithLimit.copy(
    state = DeviceState.ON,
    turnedOnAtMillis = PreviewNow - (17 * 60 + 13) * 1000L,
)

private val RunningFinalTenth = OffWithLimit.copy(
    state = DeviceState.ON,
    turnedOnAtMillis = PreviewNow - (28 * 60 + 30) * 1000L,
)

private val RunningExpired = OffWithLimit.copy(
    state = DeviceState.ON,
    turnedOnAtMillis = PreviewNow - 31 * 60 * 1000L,
)

private val JustCutOff = OffWithLimit.copy(
    state = DeviceState.OFF,
    lastCutoffMillis = PreviewNow - 90_000L,
    autoCutoffsToday = 3,
)

@Composable
private fun Artboard(
    state: HazardSheetUiState,
    dark: Boolean = true,
    isOffline: Boolean = false,
) {
    SmartHomeTheme(darkTheme = dark) {
        SheetArtboardFrame {
            HazardSheetContent(
                state = state,
                isOffline = isOffline,
                onToggle = {},
                onSetLimit = {},
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

@Preview(name = "Hazard · OFF, no limit set", widthDp = 412, heightDp = 915)
@Composable
private fun HazardOffNoLimit() = Artboard(OffNoLimit)

@Preview(name = "Hazard · OFF, limit set", widthDp = 412, heightDp = 915)
@Composable
private fun HazardOffWithLimit() = Artboard(OffWithLimit)

@Preview(name = "Hazard · ON, normal", widthDp = 412, heightDp = 915)
@Composable
private fun HazardOnNormal() = Artboard(RunningNormal)

@Preview(name = "Hazard · ON, final 10%", widthDp = 412, heightDp = 915)
@Composable
private fun HazardOnFinalTenth() = Artboard(RunningFinalTenth)

@Preview(name = "Hazard · ON, expired", widthDp = 412, heightDp = 915)
@Composable
private fun HazardOnExpired() = Artboard(RunningExpired)

@Preview(name = "Hazard · cut off automatically", widthDp = 412, heightDp = 915)
@Composable
private fun HazardCutOff() = Artboard(JustCutOff)

@Preview(name = "Hazard · app offline while ON", widthDp = 412, heightDp = 915)
@Composable
private fun HazardOffline() = Artboard(RunningNormal, isOffline = true)

@Preview(name = "Hazard · disconnected", widthDp = 412, heightDp = 915)
@Composable
private fun HazardDisconnected() = Artboard(
    OffWithLimit.copy(state = DeviceState.DISCONNECTED, lastChangedMillis = PreviewNow - 12 * 60_000L),
)

@Preview(name = "Hazard · OFF, limit set, light", widthDp = 412, heightDp = 915)
@Composable
private fun HazardLight() = Artboard(OffWithLimit, dark = false)

/**
 * The seventh artboard: editing the limit while the device is on, with the confirmation up.
 *
 * The dialog is drawn directly rather than triggered through state, because a `Dialog` does
 * not compose into the preview pane behind the sheet it belongs to.
 */
@Preview(name = "Hazard · shortening the limit mid-run", widthDp = 412, heightDp = 420)
@Composable
private fun HazardShortenConfirm() {
    SmartHomeTheme(darkTheme = true) {
        val colors = SmartHomeTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Switch off almost immediately?", style = AppType.sectionHeader, color = colors.textPrimary)
            Text(
                text = cutOffWarning(newLimitSeconds = 15 * 60L, elapsedSeconds = 18 * 60L),
                style = AppType.body,
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
                Text("Set anyway", style = AppType.label, color = colors.stateError)
            }
        }
    }
}
