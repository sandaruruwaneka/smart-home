package com.smarthome.control.ui.floor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.common.relativeTime
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.common.rememberNowMillis
import com.smarthome.control.ui.components.AlertBanner
import com.smarthome.control.ui.components.CountdownRing
import com.smarthome.control.ui.components.DeviceMarker
import com.smarthome.control.ui.components.EmptyState
import com.smarthome.control.ui.components.formatDuration
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.components.PriorityContainer
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.navigation.AppBottomBar
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.Motion
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.entryDuration
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Outlet
import java.util.concurrent.TimeUnit

/**
 * Screen prompt 03 — the floor dashboard.
 *
 * The centrepiece. It has three jobs in strict order: let the user grasp the state of the
 * whole floor in under two seconds without reading anything, let them spot a
 * safety-critical device before they have located anything else, and let them open any
 * device's controls in one tap.
 *
 * Everything here serves the first job. The plan takes about 70 % of the viewport because
 * that is the surface the answer is read off; the summary is one line because it is a
 * caption to the picture, not a second way of saying the same thing; and the legend is
 * collapsed because it is a learning aid, not daily furniture.
 *
 * The hazard strip sits *above* the plan for a reason worth stating plainly: a burning
 * iron outranks knowing where the iron is.
 */
@Composable
fun FloorDashboardScreen(
    floorId: String,
    onBack: () -> Unit,
    onOpenDevice: (deviceId: String) -> Unit,
    onEditFloor: (floorId: String) -> Unit,
    onSwitchFloor: (floorId: String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FloorDashboardViewModel = viewModel(
        factory = FloorDashboardViewModel.factory(floorId),
        key = floorId,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    FloorDashboardContent(
        state = state,
        isOffline = rememberIsOffline(),
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenDevice = onOpenDevice,
        onEditFloor = { onEditFloor(floorId) },
        onSwitchFloor = onSwitchFloor,
        onNavigate = onNavigate,
        onToggleDevice = viewModel::toggle,
        onTurnAllOff = viewModel::turnAllOff,
        onRenameFloor = viewModel::renameFloor,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloorDashboardContent(
    state: FloorDashboardUiState,
    isOffline: Boolean,
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onEditFloor: () -> Unit,
    onSwitchFloor: (String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onToggleDevice: (String) -> Unit,
    onTurnAllOff: () -> Unit,
    onRenameFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewport: FloorPlanViewport = rememberFloorPlanViewport(),
) {
    val colors = SmartHomeTheme.colors

    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var quickActionsFor by remember { mutableStateOf<MarkerUiState?>(null) }
    var detailsFor by remember { mutableStateOf<MarkerUiState?>(null) }
    var showSwitcher by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var confirmingTurnAllOff by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Rounded.ArrowBack,
                                contentDescription = "Back to floors",
                                tint = colors.textPrimary,
                            )
                        }
                    },
                    title = {
                        // Tappable: switching floors keeps the user on this screen rather
                        // than routing them back through Home to come straight here again.
                        Text(
                            text = state.floorName,
                            style = AppType.display,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable(enabled = state.floors.size > 1) { showSwitcher = true }
                                .semantics {
                                    contentDescription = "${state.floorName}. Switch floor"
                                },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                    ),
                    actions = {
                        IconButton(onClick = onEditFloor) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit floor",
                                tint = colors.textSecondary,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "More actions",
                                    tint = colors.textSecondary,
                                )
                            }
                            OverflowMenu(
                                expanded = menuOpen,
                                canTurnOff = state.switchableOnCount > 0,
                                viewport = viewport,
                                onDismiss = { menuOpen = false },
                                onTurnAllOff = { menuOpen = false; confirmingTurnAllOff = true },
                                onRename = { menuOpen = false; renaming = true },
                                onShowLegend = { menuOpen = false; showLegend = true },
                            )
                        }
                    },
                )

                // Never a blank frame: the canvas is already drawing the plan and the grid
                // underneath while this line is up.
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant,
                    )
                }

                StatusStrip(isOffline = isOffline, error = state.error)
            }
        },
        bottomBar = {
            AppBottomBar(current = AppDestination.Home, onSelect = onNavigate)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(scaffoldPadding)
                .fillMaxSize()
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            BannerSlot(
                banner = state.banner,
                nowMillis = state.nowMillis,
                onOpenDevice = onOpenDevice,
                onViewAlerts = { onNavigate(AppDestination.Alerts) },
            )

            HazardStrip(hazards = state.hazards, onOpenDevice = onOpenDevice)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The working surface takes the room. Everything else on this screen
                    // is a caption to it.
                    .weight(1f)
                    .padding(vertical = Spacing.md),
            ) {
                FloorPlanCanvas(
                    state = state,
                    viewport = viewport,
                    selectedDeviceId = selectedDeviceId,
                    onSelectMarker = { marker ->
                        selectedDeviceId = marker.deviceId
                        onOpenDevice(marker.deviceId)
                    },
                    onLongPressMarker = { quickActionsFor = it },
                    onTapEmptySpace = { selectedDeviceId = null },
                    modifier = Modifier.fillMaxSize(),
                    dimmed = isOffline,
                    // During loading the plan and the grid render, but not markers whose
                    // positions are not known yet.
                    showMarkers = !state.isLoading,
                )

                if (!state.isLoading && state.markers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surface.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.Outlet,
                            message = "No devices on this floor yet.",
                            actionLabel = "Place a device",
                            onAction = onEditFloor,
                        )
                    }
                }
            }

            SummaryStrip(
                line = state.summaryLine,
                legendOpen = showLegend,
                onToggleLegend = { showLegend = !showLegend },
            )

            AnimatedVisibility(visible = showLegend) { Legend() }
        }
    }

    if (showSwitcher) {
        FloorSwitcherSheet(
            floors = state.floors,
            sheetState = rememberModalBottomSheetState(),
            onDismiss = { showSwitcher = false },
            onSelect = { showSwitcher = false; onSwitchFloor(it) },
        )
    }

    quickActionsFor?.let { marker ->
        QuickActionsSheet(
            marker = marker,
            sheetState = rememberModalBottomSheetState(),
            onDismiss = { quickActionsFor = null },
            onTurnOff = { quickActionsFor = null; onToggleDevice(marker.deviceId) },
            onOpenControls = { quickActionsFor = null; onOpenDevice(marker.deviceId) },
            onDetails = { quickActionsFor = null; detailsFor = marker },
        )
    }

    detailsFor?.let { marker ->
        DeviceDetailsDialog(marker = marker, onDismiss = { detailsFor = null })
    }

    if (confirmingTurnAllOff) {
        TurnAllOffDialog(
            count = state.switchableOnCount,
            floorName = state.floorName,
            onDismiss = { confirmingTurnAllOff = false },
            onConfirm = { confirmingTurnAllOff = false; onTurnAllOff() },
        )
    }

    if (renaming) {
        RenameFloorDialog(
            currentName = state.floorName,
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRenameFloor(it) },
        )
    }
}

/**
 * The hazard strip: one chip per device running against its maximum on-duration.
 *
 * Horizontally scrolling rather than wrapping, so three simultaneous hazards cannot push
 * the floor plan off the bottom of the screen. Present only when something is running;
 * when nothing is, it occupies no height at all.
 */
@Composable
private fun HazardStrip(hazards: List<HazardChipUiState>, onOpenDevice: (String) -> Unit) {
    AnimatedVisibility(
        visible = hazards.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = Spacing.sm)
                // Something is running that will be cut off. That is worth interrupting a
                // screen reader for; ordinary state changes are not.
                .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            hazards.forEach { hazard ->
                HazardChip(hazard = hazard, onClick = { onOpenDevice(hazard.deviceId) })
            }
        }
    }
}

@Composable
private fun HazardChip(hazard: HazardChipUiState, onClick: () -> Unit) {
    val colors = SmartHomeTheme.colors
    // Its own one-second clock. The screen state ticks every five seconds, which is right
    // for deciding *whether* something is running and far too coarse for the digits.
    val now by rememberNowMillis(intervalMillis = 1_000L)

    val elapsed = TimeUnit.MILLISECONDS
        .toSeconds(now - hazard.turnedOnAtMillis)
        .coerceAtLeast(0L)

    PriorityContainer(
        tier = PriorityTier.ATTENTION,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CountdownRing(
                elapsedSeconds = elapsed,
                maxOnSeconds = hazard.maxOnSeconds,
                size = 28.dp,
                strokeWidth = 3.dp,
                showLabel = false,
            )
            Column {
                Text(
                    text = formatDuration((hazard.maxOnSeconds - elapsed).coerceAtLeast(0L)),
                    // Tabular figures are mandatory on a number that changes every second:
                    // proportional digits make the chip breathe in and out as it counts.
                    style = AppType.numeric.copy(fontSize = 14.sp),
                    color = colors.textPrimary,
                )
                Text(
                    text = hazard.name,
                    style = AppType.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The Critical banner, sliding in beneath the top bar and above the hazard strip. */
@Composable
private fun BannerSlot(
    banner: DashboardBanner?,
    nowMillis: Long,
    onOpenDevice: (String) -> Unit,
    onViewAlerts: () -> Unit,
) {
    val lastBanner = remember { mutableStateOf(banner) }
    SideEffect { if (banner != null) lastBanner.value = banner }
    val duration = entryDuration()

    AnimatedVisibility(
        visible = banner != null,
        enter = slideInVertically(
            animationSpec = tween(duration, easing = Motion.EmphasisedDecelerate),
            initialOffsetY = { -it },
        ) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        val shown = banner ?: lastBanner.value
        if (shown != null) {
            AlertBanner(
                cause = shown.cause,
                reason = shown.reason,
                timestamp = relativeTime(shown.createdAtMillis, nowMillis),
                modifier = Modifier.padding(top = Spacing.sm),
                actionLabel = "View",
                onAction = {
                    if (shown.collapsedCount > 1) onViewAlerts() else onOpenDevice(shown.deviceId)
                },
            )
        }
    }
}

@Composable
private fun StatusStrip(isOffline: Boolean, error: String?) {
    val colors = SmartHomeTheme.colors

    AnimatedVisibility(
        visible = error != null || isOffline,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(horizontal = Spacing.screenHorizontal)
                .height(32.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error ?: "Showing last known state",
                style = AppType.label,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `7 devices · 2 active · 1 offline`, with the legend behind an info affordance. */
@Composable
private fun SummaryStrip(line: String, legendOpen: Boolean, onToggleLegend: () -> Unit) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line,
            style = AppType.label,
            color = colors.textSecondary,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onToggleLegend) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = if (legendOpen) "Hide the key" else "Show the key",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The four-state key.
 *
 * `Offline` rather than `Disconnected`: the data layer's enum name is not the user's word
 * for it, and this row is read by somebody trying to learn what a dashed border means.
 */
@Composable
private fun Legend() {
    val colors = SmartHomeTheme.colors

    Column(
        modifier = Modifier.padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        listOf(
            DeviceState.ON to "On",
            DeviceState.OFF to "Off",
            DeviceState.ERROR to "Error",
            DeviceState.DISCONNECTED to "Offline",
        ).forEach { (deviceState, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                DeviceMarker(type = DeviceType.OUTLET, state = deviceState, size = 32.dp)
                Text(label, style = AppType.body, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    canTurnOff: Boolean,
    viewport: FloorPlanViewport,
    onDismiss: () -> Unit,
    onTurnAllOff: () -> Unit,
    onRename: () -> Unit,
    onShowLegend: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = colors.surface) {
        DropdownMenuItem(
            text = {
                Text(
                    "Turn all off",
                    style = AppType.body,
                    color = if (canTurnOff) colors.textPrimary else colors.textSecondary,
                )
            },
            enabled = canTurnOff,
            onClick = onTurnAllOff,
        )
        DropdownMenuItem(
            text = { Text("Rename floor", style = AppType.body, color = colors.textPrimary) },
            onClick = onRename,
        )
        DropdownMenuItem(
            text = { Text("Show legend", style = AppType.body, color = colors.textPrimary) },
            onClick = onShowLegend,
        )

        HorizontalDivider(color = colors.outline)

        // The non-gesture equivalents of pinch. Required, not a nicety: a user who cannot
        // perform a two-finger gesture otherwise cannot reach a 26 dp marker at all.
        DropdownMenuItem(
            text = { Text("Zoom in", style = AppType.body, color = colors.textPrimary) },
            onClick = { viewport.zoomIn() },
        )
        DropdownMenuItem(
            text = { Text("Zoom out", style = AppType.body, color = colors.textPrimary) },
            onClick = { viewport.zoomOut() },
        )
        DropdownMenuItem(
            text = { Text("Fit", style = AppType.body, color = colors.textPrimary) },
            onClick = { viewport.fit() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorSwitcherSheet(
    floors: List<FloorSwitcherEntry>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = SmartHomeTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(
                "Switch floor",
                style = AppType.sectionHeader,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            floors.forEach { floor ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.minTouchTarget)
                        .clickable(enabled = !floor.isCurrent) { onSelect(floor.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val dot = when (floor.tier) {
                        PriorityTier.NORMAL -> null
                        PriorityTier.ATTENTION -> colors.stateOn
                        PriorityTier.CRITICAL -> colors.stateError
                    }
                    Box(Modifier.size(8.dp)) {
                        if (dot != null) {
                            Box(Modifier.fillMaxSize().background(dot, CircleShape))
                        }
                    }
                    Text(
                        text = floor.name,
                        style = AppType.body,
                        color = if (floor.isCurrent) colors.primary else colors.textPrimary,
                    )
                }
            }
            Box(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Long-press quick actions.
 *
 * A sheet rather than a dropdown anchored to the marker: the marker lives inside a canvas
 * that pans and zooms, and a menu pinned to a moving, scaled coordinate space lands in the
 * wrong place as soon as anybody touches the plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsSheet(
    marker: MarkerUiState,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onTurnOff: () -> Unit,
    onOpenControls: () -> Unit,
    onDetails: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(marker.name, style = AppType.sectionHeader, color = colors.textPrimary)
            Text(
                text = "${marker.type.label} · ${marker.state.spoken}",
                style = AppType.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )

            if (marker.canSwitch && marker.state == DeviceState.ON) {
                SheetAction("Turn off", onTurnOff)
            }
            SheetAction("Open controls", onOpenControls)
            SheetAction("Details", onDetails)
            Box(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppType.body,
        color = SmartHomeTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun DeviceDetailsDialog(marker: MarkerUiState, onDismiss: () -> Unit) {
    val colors = SmartHomeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text(marker.name, style = AppType.sectionHeader) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Type: ${marker.type.label}", style = AppType.body)
                Text("State: ${marker.state.spoken}", style = AppType.body)
                Text(
                    "Position: row ${marker.gridY + 1}, column ${marker.gridX + 1}",
                    style = AppType.body,
                )
                if (marker.channelBadge != null) {
                    Text("Channels on: ${marker.channelBadge}", style = AppType.body)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = AppType.label, color = colors.primary)
            }
        },
    )
}

/**
 * `Turn off 3 devices on Ground Floor?`
 *
 * The count is in the question because this is a bulk action taken from a menu, and a
 * mis-tap during a recorded demo is expensive. Naming the number gives the user something
 * to disagree with.
 */
@Composable
private fun TurnAllOffDialog(
    count: Int,
    floorName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                "Turn off $count ${if (count == 1) "device" else "devices"} on $floorName?",
                style = AppType.sectionHeader,
            )
        },
        text = {
            Text(
                "Every device that is currently on will be switched off.",
                style = AppType.body,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Turn all off", style = AppType.label, color = colors.stateError)
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
private fun RenameFloorDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Rename floor", style = AppType.sectionHeader) },
        text = {
            LabeledTextField(label = "Floor name", value = name, onValueChange = { name = it })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
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

// ---------------------------------------------------------------------------
// Artboards — the section 12 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_264_000_000L

private val PreviewMarkers = listOf(
    MarkerUiState("d1", "Hall lamp", DeviceType.LIGHT, DeviceState.ON, gridX = 1, gridY = 1),
    MarkerUiState("d2", "TV outlet", DeviceType.OUTLET, DeviceState.OFF, gridX = 5, gridY = 1),
    MarkerUiState(
        "d3", "Switch bank", DeviceType.MULTI_SWITCH, DeviceState.ON,
        gridX = 3, gridY = 3, channelBadge = "2/3", canSwitch = false,
    ),
    MarkerUiState(
        "d4", "Porch camera", DeviceType.CAMERA, DeviceState.DISCONNECTED,
        gridX = 7, gridY = 3, canSwitch = false,
    ),
    MarkerUiState("d5", "Kitchen outlet", DeviceType.OUTLET, DeviceState.OFF, gridX = 1, gridY = 5),
)

private val PreviewNormal = FloorDashboardUiState(
    isLoading = false,
    floorName = "Ground Floor",
    gridRows = 6,
    gridCols = 10,
    markers = PreviewMarkers,
    floors = listOf(
        FloorSwitcherEntry("f1", "Ground Floor", isCurrent = true),
        FloorSwitcherEntry("f2", "First Floor"),
    ),
    nowMillis = PreviewNow,
)

private val PreviewHazard = PreviewNormal.copy(
    markers = PreviewMarkers + MarkerUiState(
        "d6", "Iron", DeviceType.APPLIANCE, DeviceState.ON,
        gridX = 8, gridY = 4, hazardActive = true,
    ),
    hazards = listOf(
        HazardChipUiState("d6", "Iron", turnedOnAtMillis = PreviewNow - 106_000L, maxOnSeconds = 240),
        HazardChipUiState("d7", "Heater", turnedOnAtMillis = PreviewNow - 60_000L, maxOnSeconds = 520),
    ),
)

private val PreviewCritical = PreviewNormal.copy(
    markers = PreviewMarkers.map {
        if (it.deviceId == "d2") it.copy(state = DeviceState.ERROR) else it
    },
    banner = DashboardBanner(
        alertId = "a1",
        deviceId = "d2",
        cause = "TV outlet reported a fault",
        reason = "Device reported a fault",
        createdAtMillis = PreviewNow - 4 * 60_000L,
    ),
)

@Composable
private fun Artboard(
    state: FloorDashboardUiState,
    dark: Boolean = true,
    isOffline: Boolean = false,
    zoom: Float = 1f,
) {
    val viewport = rememberFloorPlanViewport()
    LaunchedEffect(zoom) { repeat(((zoom - 1f) / 0.5f).toInt()) { viewport.zoomIn() } }

    SmartHomeTheme(darkTheme = dark) {
        FloorDashboardContent(
            state = state,
            isOffline = isOffline,
            onBack = {},
            onOpenDevice = {},
            onEditFloor = {},
            onSwitchFloor = {},
            onNavigate = {},
            onToggleDevice = {},
            onTurnAllOff = {},
            onRenameFloor = {},
            viewport = viewport,
        )
    }
}

@Preview(name = "Floor dashboard · normal", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardNormal() = Artboard(PreviewNormal)

@Preview(name = "Floor dashboard · hazard active", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardHazard() = Artboard(PreviewHazard)

@Preview(name = "Floor dashboard · critical", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardCritical() = Artboard(PreviewCritical)

@Preview(name = "Floor dashboard · empty", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardEmpty() = Artboard(
    PreviewNormal.copy(markers = emptyList(), hazards = emptyList()),
)

@Preview(name = "Floor dashboard · zoomed 2x", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardZoomed() = Artboard(PreviewNormal, zoom = 2f)

@Preview(name = "Floor dashboard · offline", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardOffline() = Artboard(PreviewNormal, isOffline = true)

@Preview(name = "Floor dashboard · normal, light", widthDp = 412, heightDp = 915)
@Composable
private fun DashboardLight() = Artboard(PreviewNormal, dark = false)
