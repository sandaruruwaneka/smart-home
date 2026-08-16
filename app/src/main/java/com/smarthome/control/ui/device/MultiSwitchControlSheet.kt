package com.smarthome.control.ui.device

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.common.relativeTime
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.components.ChannelRow
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

/**
 * Screen prompt 06 — the multi-switch control sheet.
 *
 * The user tapped a gang box and wants one specific channel, identified by name rather than
 * by counting positions on a plate. Everything below the identity row is arranged to make
 * that a single unambiguous tap.
 *
 * ### It inherits the anatomy rather than resembling it
 *
 * The five slots, the drag handle, the identity row, the section headers and the footer all
 * come from [DeviceSheetScaffold] — the same code the outlet sheet draws. Slot 3, empty for
 * an outlet, is where this sheet does its work.
 *
 * ### The count is the headline, not the word
 *
 * The unit-level rule says a gang box is ON if any channel is on, which is right for the
 * marker on the plan and wrong as a headline here: `ON` cannot tell one lit channel from
 * three. So the primary card carries `2 of 3 on`, and the two ends get the phrasing a person
 * would use — `All off` and `All 3 on` rather than `0 of 3` and `3 of 3`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSwitchControlSheet(
    deviceId: String,
    onDismiss: () -> Unit,
    onMoveDevice: (deviceId: String) -> Unit,
    onViewHistory: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MultiSwitchSheetViewModel = viewModel(
        factory = MultiSwitchSheetViewModel.factory(deviceId),
        key = deviceId,
    ),
) {
    val colors = SmartHomeTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Section 2 asks for a medium detent that grows with channel count. Material 3 has one
    // partial detent and it sits at half the screen, which cuts the channel list even on a
    // three-gang unit -- and the same section says the channels must never start below the
    // fold, because they are the reason the sheet exists. The constraint outranks the
    // percentage, so the sheet opens expanded.
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
        MultiSwitchSheetContent(
            state = state,
            isOffline = rememberIsOffline(),
            onToggleChannel = viewModel::toggleChannel,
            onAllOn = { viewModel.setAllChannels(turnOn = true) },
            onAllOff = { viewModel.setAllChannels(turnOn = false) },
            onRenameUnit = viewModel::renameUnit,
            onRenameChannel = viewModel::renameChannel,
            onDelete = viewModel::deleteUnit,
            onMove = { onMoveDevice(deviceId) },
            onViewHistory = { onViewHistory(deviceId) },
            onRetry = viewModel::retry,
        )
    }
}

@Composable
internal fun MultiSwitchSheetContent(
    state: MultiSwitchSheetUiState,
    isOffline: Boolean,
    onToggleChannel: (String) -> Unit,
    onAllOn: () -> Unit,
    onAllOff: () -> Unit,
    onRenameUnit: (String) -> Unit,
    onRenameChannel: (String, String) -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onViewHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    var renamingUnit by remember { mutableStateOf(false) }
    var renamingChannels by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val controlsEnabled = state.isUnitReachable && !isOffline

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
                SheetMenuItem("Rename unit") { dismiss(); renamingUnit = true }
                SheetMenuItem("Rename channels") { dismiss(); renamingChannels = true }
                SheetMenuItem("Move to another cell") { dismiss(); onMove() }
                SheetMenuItem("View history") { dismiss(); onViewHistory() }
                SheetMenuItem("Delete unit", destructive = true) { dismiss(); confirmingDelete = true }
            }
        }

        if (state.loadError != null) {
            SheetLoadFailure(message = state.loadError, onRetry = onRetry)
            return@Column
        }

        // ---- slot 2: primary control -----------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            UnitCard(
                state = state,
                enabled = controlsEnabled,
                onAllOn = onAllOn,
                onAllOff = onAllOff,
            )
            UnitStatusLine(state = state, isOffline = isOffline)
        }

        // ---- slot 3: the channels --------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SheetSectionHeader("CHANNELS")

            // No dividers. The state bars already give the block its rhythm, and rules
            // between rows at this density chop it into fragments.
            state.channels.forEach { channel ->
                ChannelRow(
                    name = channel.name,
                    index = channel.index,
                    state = channel.state,
                    enabled = controlsEnabled,
                    caption = channel.caption,
                    pendingWrite = channel.pendingWrite,
                    externalChangeToken = channel.externalChangeToken,
                    rowIsTarget = true,
                    contentDescription = channel.spoken,
                    onToggle = { onToggleChannel(channel.id) },
                    modifier = Modifier
                        .height(ChannelRowHeight)
                        // A fault on one gang must not lock the others, so only the
                        // unreachable unit dims the whole list.
                        .alpha(if (state.isUnitReachable) 1f else DisabledAlpha),
                )
            }
        }

        // ---- slot 4: usage ---------------------------------------------------
        UsageSection(usage = state.usage)

        // ---- slot 5: metadata footer -----------------------------------------
        SheetFooter(lastChangedMillis = state.lastChangedMillis, nowMillis = state.nowMillis)
    }

    if (renamingUnit) {
        RenameDeviceDialog(
            currentName = state.deviceName,
            label = "Unit name",
            title = "Rename unit",
            onDismiss = { renamingUnit = false },
            onConfirm = { renamingUnit = false; onRenameUnit(it) },
        )
    }

    if (renamingChannels) {
        RenameChannelsDialog(
            channels = state.channels,
            onDismiss = { renamingChannels = false },
            onConfirm = { renamed ->
                renamingChannels = false
                renamed.forEach { (id, name) -> onRenameChannel(id, name) }
            },
        )
    }

    if (confirmingDelete) {
        SheetConfirmDialog(
            title = "Delete ${state.deviceName} and its ${state.channelCount} channels?",
            // Section 9: say why the gang count is fixed here rather than leaving the user
            // to discover that deleting is the only way to change it.
            body = "Their usage history goes with them. Channel count is set when the unit " +
                "is placed, so changing it means deleting and re-placing the unit.",
            confirmLabel = "Delete",
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; onDelete() },
        )
    }
}

// ---------------------------------------------------------------------------
// Slot 2 — the unit card
// ---------------------------------------------------------------------------

/**
 * 80 dp rather than the outlet's 96, and not tappable.
 *
 * Shorter because it is no longer the main target — the channel rows are — and inert
 * because there are three possible intents on this card. A large ambiguous target is worse
 * than no target: the user who meant `All off` and got `All on` has to undo something they
 * did not choose to do.
 */
@Composable
private fun UnitCard(
    state: MultiSwitchSheetUiState,
    enabled: Boolean,
    onAllOn: () -> Unit,
    onAllOff: () -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val anyOn = state.onCount > 0
    val disconnected = !state.isUnitReachable

    val accent = when {
        disconnected -> colors.textSecondary
        anyOn -> colors.stateOn
        else -> colors.stateOff
    }
    val fill = if (disconnected) colors.stateDisconnected else colors.surfaceVariant
    val shape = RoundedCornerShape(CardRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UnitCardHeight)
            .background(fill, shape)
            .then(
                if (disconnected) {
                    Modifier.dashedBorder(colors.textSecondary, AppBorders.hairline, CardRadius)
                } else {
                    Modifier.border(AppBorders.hairline, colors.outline, shape)
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = state.spokenPrimary },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = DeviceType.MULTI_SWITCH.icon(
                    if (anyOn) DeviceState.ON else DeviceState.OFF,
                ),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(UnitIconSize),
            )
            Spacer(Modifier.width(Spacing.md))

            Text(
                text = if (disconnected) {
                    buildAnnotatedString { append("OFFLINE") }
                } else {
                    // Only the numeral is amber. Colouring the whole phrase would make
                    // "of 3 on" look like part of the reading rather than its frame.
                    buildAnnotatedString {
                        val highlight = state.summaryHighlight
                        if (highlight == null) {
                            append(state.summaryLine)
                        } else {
                            withStyle(SpanStyle(color = colors.stateOn)) { append(highlight) }
                            append(state.summaryLine.removePrefix(highlight))
                        }
                    }
                },
                style = AppType.display,
                color = if (disconnected) accent else colors.textPrimary,
                modifier = Modifier.weight(1f),
            )

            // No confirmation on either. Neither is destructive, and a dialog on a light
            // switch is noise the user learns to tap through.
            TextButton(onClick = onAllOff, enabled = enabled && state.canTurnAllOff) {
                Text(
                    "All off",
                    style = AppType.label,
                    color = if (enabled && state.canTurnAllOff) colors.textPrimary else colors.outline,
                )
            }
            TextButton(onClick = onAllOn, enabled = enabled && state.canTurnAllOn) {
                Text(
                    "All on",
                    style = AppType.label,
                    color = if (enabled && state.canTurnAllOn) colors.stateOn else colors.outline,
                )
            }
        }
    }
}

/**
 * The line under the card, and there is only ever one.
 *
 * Same priority as the outlet sheet: being offline outranks a device fault, because it
 * changes what the user can do about it.
 */
@Composable
private fun UnitStatusLine(state: MultiSwitchSheetUiState, isOffline: Boolean) {
    val colors = SmartHomeTheme.colors
    val faultLine = state.faultLine

    val (message, tint) = when {
        isOffline -> "You're offline. Reconnect to control this device." to colors.textSecondary

        state.actionError != null -> state.actionError to colors.stateError

        !state.isUnitReachable -> {
            val seen = state.lastChangedMillis
                ?.let { relativeTime(it, state.nowMillis).lowercase() }
                ?: "a while ago"
            "Last seen $seen." to colors.textSecondary
        }

        faultLine != null -> faultLine to colors.stateError

        else -> return
    }

    Text(text = message, style = AppType.body, color = tint)
}

// ---------------------------------------------------------------------------
// Slot 4 — usage
// ---------------------------------------------------------------------------

@Composable
private fun UsageSection(usage: MultiSwitchUsage?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SheetSectionHeader("TODAY")

        if (usage == null) {
            NoUsageLine()
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatCard(
                value = usage.combinedLabel,
                unit = "",
                // "Combined" is doing real work in this label: three channels on for two
                // hours reads as six, and without the word that looks like a bug.
                label = "Combined on",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = usage.mostUsedLabel,
                unit = "",
                label = "Most used",
                modifier = Modifier.weight(1f),
            )
        }

        StackedDayTimeline(
            bands = usage.bands,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/**
 * The whole plate at once, because that is how the user thinks about naming it.
 *
 * Names are collected here and written per channel on save, so a user who fixes one and
 * abandons the rest keeps the fix.
 */
@Composable
private fun RenameChannelsDialog(
    channels: List<ChannelUiState>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var names by remember { mutableStateOf(channels.associate { it.id to it.name }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Rename channels", style = AppType.sectionHeader) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                channels.forEach { channel ->
                    LabeledTextField(
                        label = "Channel ${channel.index + 1}",
                        value = names[channel.id].orEmpty(),
                        onValueChange = { value -> names = names + (channel.id to value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Only what actually changed, so an untouched row costs no write.
                    onConfirm(
                        names.filter { (id, name) ->
                            channels.first { it.id == id }.name != name.trim() && name.isNotBlank()
                        },
                    )
                },
            ) {
                Text("Save", style = AppType.label, color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

private val UnitCardHeight = 80.dp
private val UnitIconSize = 32.dp
private val CardRadius = 16.dp
private val ChannelRowHeight = 56.dp
private const val DisabledAlpha = 0.6f

// ---------------------------------------------------------------------------
// Artboards — the section 12 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_264_000_000L

private fun hours(vararg spans: Pair<Int, Float>): List<Float> {
    val filled = spans.toMap()
    return List(HoursInDay) { filled[it] ?: 0f }
}

private val CeilingBand = TimelineBand(
    label = "Ceiling light",
    hourFractions = hours(18 to 0.5f, 19 to 1f, 20 to 1f, 21 to 0.4f),
    onSeconds = 2 * 3600L + 54 * 60L,
)

private val WallBand = TimelineBand(
    label = "Wall light",
    hourFractions = hours(19 to 0.6f, 20 to 1f),
    onSeconds = 1 * 3600L + 36 * 60L,
)

private val FanBand = TimelineBand(
    label = "Ceiling fan",
    hourFractions = hours(12 to 0.4f, 13 to 1f, 14 to 1f, 15 to 0.7f),
    onSeconds = 3 * 3600L + 6 * 60L,
)

private val PreviewUsage = MultiSwitchUsage(
    combinedOnSeconds = 6 * 3600L + 40 * 60L,
    mostUsedChannel = "Ceiling fan",
    bands = listOf(CeilingBand, WallBand, FanBand),
)

private fun channel(
    index: Int,
    name: String,
    state: DeviceState,
    runningSeconds: Long? = null,
) = ChannelUiState(
    id = "c$index",
    index = index,
    name = name,
    state = state,
    runningSeconds = runningSeconds,
)

private val PreviewPartial = MultiSwitchSheetUiState(
    isLoading = false,
    deviceName = "Living Room Switch",
    locationLine = "Ground Floor · R4 C3 · 3 gang",
    unitState = DeviceState.ON,
    channels = listOf(
        channel(0, "Ceiling light", DeviceState.ON, 2 * 3600L + 14 * 60L),
        channel(1, "Wall light", DeviceState.OFF),
        channel(2, "Ceiling fan", DeviceState.ON, 46 * 60L),
    ),
    lastChangedMillis = PreviewNow - 3 * 60_000L,
    usage = PreviewUsage,
    nowMillis = PreviewNow,
)

private val FiveGang = PreviewPartial.copy(
    deviceName = "Kitchen Switch",
    locationLine = "Ground Floor · R2 C7 · 5 gang",
    channels = listOf(
        channel(0, "Ceiling light", DeviceState.ON, 3 * 3600L),
        channel(1, "Under cabinet", DeviceState.ON, 40 * 60L),
        channel(2, "Extractor fan", DeviceState.OFF),
        channel(3, "Pantry light", DeviceState.OFF),
        channel(4, "Outside light", DeviceState.ON, 12 * 60L),
    ),
    usage = MultiSwitchUsage(
        combinedOnSeconds = 9 * 3600L + 12 * 60L,
        mostUsedChannel = "Ceiling light",
        bands = listOf(
            CeilingBand.copy(label = "Ceiling light"),
            WallBand.copy(label = "Under cabinet"),
            FanBand.copy(label = "Extractor fan"),
            TimelineBand("Pantry light", hours(7 to 0.2f), 12 * 60L),
            TimelineBand("Outside light", hours(5 to 0.8f, 21 to 1f, 22 to 0.5f), 2 * 3600L),
        ),
    ),
)

@Composable
private fun Artboard(
    state: MultiSwitchSheetUiState,
    dark: Boolean = true,
    isOffline: Boolean = false,
) {
    SmartHomeTheme(darkTheme = dark) {
        SheetArtboardFrame {
            MultiSwitchSheetContent(
                state = state,
                isOffline = isOffline,
                onToggleChannel = {},
                onAllOn = {},
                onAllOff = {},
                onRenameUnit = {},
                onRenameChannel = { _, _ -> },
                onDelete = {},
                onMove = {},
                onViewHistory = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Multi-switch · partial (2 of 3)", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchPartial() = Artboard(PreviewPartial)

@Preview(name = "Multi-switch · all off", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchAllOff() = Artboard(
    PreviewPartial.copy(
        unitState = DeviceState.OFF,
        channels = PreviewPartial.channels.map {
            it.copy(state = DeviceState.OFF, runningSeconds = null)
        },
    ),
)

@Preview(name = "Multi-switch · all on", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchAllOn() = Artboard(
    PreviewPartial.copy(
        channels = PreviewPartial.channels.map {
            it.copy(state = DeviceState.ON, runningSeconds = it.runningSeconds ?: 30 * 60L)
        },
    ),
)

@Preview(name = "Multi-switch · 5-gang", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchFiveGang() = Artboard(FiveGang)

@Preview(name = "Multi-switch · channel error", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchChannelError() = Artboard(
    PreviewPartial.copy(
        channels = PreviewPartial.channels.map {
            if (it.index == 1) it.copy(state = DeviceState.ERROR) else it
        },
    ),
)

@Preview(name = "Multi-switch · unit disconnected", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchDisconnected() = Artboard(
    PreviewPartial.copy(
        unitState = DeviceState.DISCONNECTED,
        lastChangedMillis = PreviewNow - 12 * 60_000L,
    ),
)

@Preview(name = "Multi-switch · no usage today", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchNoUsage() = Artboard(PreviewPartial.copy(usage = null))

@Preview(name = "Multi-switch · partial, light", widthDp = 412, heightDp = 915)
@Composable
private fun MultiSwitchLight() = Artboard(PreviewPartial, dark = false)

/** Section 12 asks for the stack at both sizes, side by side, to check it holds up. */
@Preview(name = "Stacked timeline · 3 and 5 bands", widthDp = 412, heightDp = 320)
@Composable
private fun StackedTimelineSizes() {
    SmartHomeTheme(darkTheme = true) {
        val colors = SmartHomeTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SheetSectionHeader("3 CHANNELS")
            StackedDayTimeline(bands = PreviewUsage.bands)
            SheetSectionHeader("5 CHANNELS")
            StackedDayTimeline(bands = FiveGang.usage?.bands.orEmpty())
        }
    }
}
