package com.smarthome.control.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.common.relativeTime
import com.smarthome.control.ui.components.AlertBanner
import com.smarthome.control.ui.components.AlertBannerCollapsed
import com.smarthome.control.ui.components.AlertRow
import com.smarthome.control.ui.components.AlertType
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.components.EmptyStates
import com.smarthome.control.ui.components.FloorCard
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.components.SummaryTileGroup
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.navigation.AppBottomBar
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.Motion
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.entryDuration

/**
 * Screen prompt 02 — the floor list, and the app's home.
 *
 * ### One question, two seconds
 *
 * *Is anything in my house wrong right now?* Everything below is ordered by how directly it
 * answers that. The banner is first because it is the answer when the answer is yes; the
 * four-number summary is second because it is the answer when the answer is no; the floors
 * are third because they are what the user came to touch; the recent events are last
 * because they are a record of questions already answered.
 *
 * There is no greeting header. A time-of-day salutation would take the most valuable row on
 * the screen to tell the user something they can see out of the window, and it pulls the
 * product toward the lifestyle register the master prompt rules out.
 *
 * ### Live, and visibly so
 *
 * Three Firestore listeners drive this screen and there is deliberately **no
 * pull-to-refresh**. The gesture would imply the data can go stale, which quietly undercuts
 * the sync mechanism the brief marks. What replaces it is motion the user can see: values
 * cross-fade, status dots grow in, the banner slides down from the top.
 */
@Composable
fun FloorListScreen(
    onOpenFloor: (String) -> Unit,
    onAddFloor: () -> Unit,
    onOpenDevice: (deviceId: String, floorId: String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FloorListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    FloorListContent(
        state = state,
        isOffline = rememberIsOffline(),
        snackbarHostState = snackbarHostState,
        onOpenFloor = { onOpenFloor(it.id) },
        onAddFloor = onAddFloor,
        onOpenDevice = onOpenDevice,
        onNavigate = onNavigate,
        onRenameFloor = viewModel::renameFloor,
        onDeleteFloor = viewModel::deleteFloor,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * The screen without its ViewModel, so the artboards in section 10 of the prompt are plain
 * values rather than a Firestore emulator loaded with the right documents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloorListContent(
    state: FloorListUiState,
    isOffline: Boolean,
    onOpenFloor: (FloorRow) -> Unit,
    onAddFloor: () -> Unit,
    onOpenDevice: (String, String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onRenameFloor: (String, String) -> Unit,
    onDeleteFloor: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = SmartHomeTheme.colors

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Home", style = AppType.display) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                    ),
                    actions = {
                        IconButton(onClick = { onNavigate(AppDestination.Settings) }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = colors.textSecondary,
                            )
                        }
                    },
                )
                StatusStrip(isOffline = isOffline, error = state.error, onRetry = onRetry)
            }
        },
        bottomBar = {
            AppBottomBar(
                current = AppDestination.Home,
                onSelect = onNavigate,
                unacknowledgedAlerts = state.unacknowledgedCount,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddFloor,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = AppShapes.card,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add floor")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.padding(scaffoldPadding),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = Spacing.sm,
                // Clear of the FAB, which floats over the end of the list.
                bottom = 88.dp,
            ),
            // 8 dp between cards. The 24 dp that separates major sections is added by the
            // section headers themselves — a single arrangement cannot say both, and a
            // lazy list has no way to ask which of its neighbours is a section boundary.
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Always emitted, never conditionally: an item that appears and disappears
            // cannot animate its own entry, and an absent banner has to cost no space at
            // all rather than leave a reserved gap.
            item(key = "banner") {
                BannerSlot(
                    banner = state.banner,
                    nowMillis = state.nowMillis,
                    onOpenDevice = onOpenDevice,
                    onViewAlerts = { onNavigate(AppDestination.Alerts) },
                )
            }

            item(key = "summary") {
                Box(Modifier.padding(top = Spacing.sm)) {
                    if (state.isLoading) {
                        SummarySkeleton()
                    } else {
                        SummaryTileGroup(
                            totalDevices = state.summary.totalDevices,
                            activeNow = state.summary.activeNow,
                            errors = state.summary.errors,
                            warnings = state.summary.warnings,
                        )
                    }
                }
            }

            item(key = "floors-header") { SectionHeader("FLOORS") }

            when {
                state.isLoading -> items(2) { FloorCardSkeleton() }

                state.floors.isEmpty() -> item(key = "floors-empty") {
                    // The summary row above still renders its zeroes: the house is empty,
                    // not unknown, and those two look different.
                    EmptyStates.Floors(onAddFloor = onAddFloor)
                }

                else -> items(state.floors, key = { it.id }) { floor ->
                    FloorListItem(
                        floor = floor,
                        onOpen = { onOpenFloor(floor) },
                        onRename = { onRenameFloor(floor.id, it) },
                        onDelete = { onDeleteFloor(floor.id) },
                    )
                }
            }

            if (state.recentEvents.isNotEmpty()) {
                item(key = "events-header") {
                    SectionHeader(
                        title = "RECENT EVENTS",
                        action = "See all",
                        onAction = { onNavigate(AppDestination.Alerts) },
                    )
                }
                items(state.recentEvents, key = { it.id }) { event ->
                    AlertRow(
                        deviceName = event.deviceName,
                        reason = event.reason,
                        timestamp = relativeTime(event.createdAtMillis, state.nowMillis),
                        type = event.type,
                        acknowledged = event.acknowledged,
                    )
                }
            }
        }
    }
}

/**
 * One floor card, with the long-press menu anchored to it and the two dialogs it can open.
 *
 * The state is per card rather than one selection held by the list: a menu belongs to the
 * thing it was opened on, and hoisting it would mean the list re-composing every row each
 * time somebody long-presses one.
 */
@Composable
private fun FloorListItem(
    floor: FloorRow,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Box {
        FloorCard(
            name = floor.name,
            deviceCount = floor.deviceCount,
            activeCount = floor.activeCount,
            planImageUrl = floor.planImageUrl,
            highestTier = floor.tier,
            flaggedDevices = floor.flaggedDevices,
            onClick = onOpen,
            onLongClick = { menuOpen = true },
        )

        FloorContextMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onRename = { menuOpen = false; renaming = true },
            onDelete = { menuOpen = false; confirmingDelete = true },
        )
    }

    if (renaming) {
        RenameFloorDialog(
            currentName = floor.name,
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRename(it) },
        )
    }

    if (confirmingDelete) {
        DeleteFloorDialog(
            floor = floor,
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; onDelete() },
        )
    }
}

/**
 * The banner, and the 300 ms it takes to arrive.
 *
 * Slides down from above with the emphasised decelerate curve (master prompt section 10):
 * it comes from off the top of the screen because that is where it belongs in the hierarchy
 * — above everything, including the summary — and arriving from its own place reads as
 * urgent without anything having to flash.
 *
 * The last banner is kept for the length of the exit animation so that a cleared alert
 * slides away rather than blinking out. It is never swipe-dismissible; it goes when the
 * condition goes or when the user acts on it.
 */
@Composable
private fun BannerSlot(
    banner: HomeBanner?,
    nowMillis: Long,
    onOpenDevice: (String, String) -> Unit,
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
        when (val shown = banner ?: lastBanner.value) {
            is HomeBanner.Single -> AlertBanner(
                cause = shown.cause,
                reason = shown.reason,
                timestamp = relativeTime(shown.createdAtMillis, nowMillis),
                // Straight to the device, not to the log: what the user needs in that
                // moment is the appliance that misbehaved.
                actionLabel = "View",
                onAction = { onOpenDevice(shown.deviceId, shown.floorId) },
            )

            is HomeBanner.Multiple -> AlertBannerCollapsed(
                alertCount = shown.count,
                timestamp = relativeTime(shown.createdAtMillis, nowMillis),
                onViewAll = onViewAlerts,
            )

            null -> Unit
        }
    }
}

/**
 * The thin strip under the top bar: offline, or a listener that failed.
 *
 * Nothing on this screen is interactive enough to disable while offline, so the strip is
 * the only change — Firestore keeps serving its cache underneath, and the user is told that
 * what they are reading is the last thing the app was sure of.
 *
 * A failure takes precedence over offline, because it is the one the user can do something
 * about.
 */
@Composable
private fun StatusStrip(isOffline: Boolean, error: String?, onRetry: () -> Unit) {
    val colors = SmartHomeTheme.colors
    val visible = error != null || isOffline

    AnimatedVisibility(
        visible = visible,
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = error ?: "Showing last known state",
                style = AppType.label,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (error != null) {
                Text(
                    text = "Retry",
                    style = AppType.label,
                    color = colors.primary,
                    // A plain clickable rather than a TextButton: the strip is 32 dp and a
                    // button's own minimum height would force it open to 40.
                    modifier = Modifier
                        .clickable(onClick = onRetry)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

/** `FLOORS`, `RECENT EVENTS` — and the one text action a section is allowed. */
@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Carries the 24 dp section gap: 8 dp from the list arrangement plus 16 here.
            .padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            // Section headers are uppercase with +0.2 letter spacing here: at 16 sp in the
            // same weight as a floor name, a sentence-case header would compete with the
            // cards beneath it for the same reading.
            style = AppType.sectionHeader.copy(letterSpacing = 0.2.sp),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action, style = AppType.label, color = colors.primary)
            }
        }
    }
}

@Composable
private fun FloorContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
    ) {
        DropdownMenuItem(
            text = { Text("Rename", style = AppType.body, color = colors.textPrimary) },
            onClick = onRename,
        )
        DropdownMenuItem(
            // The destructive item in stateError, and last, so the finger that overshoots
            // lands on nothing rather than on the delete.
            text = { Text("Delete floor", style = AppType.body, color = colors.stateError) },
            onClick = onDelete,
        )
    }
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
        shape = AppShapes.card,
        title = { Text("Rename floor", style = AppType.sectionHeader) },
        text = {
            LabeledTextField(
                label = "Floor name",
                value = name,
                onValueChange = { name = it },
            )
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

/**
 * The confirmation a delete has to pass.
 *
 * It names what goes with the floor rather than asking "are you sure": deleting a floor
 * cascades to every device placed on it and to their usage history, and somebody who
 * thought they were tidying up a list needs to be told that before they tap, not after.
 */
@Composable
private fun DeleteFloorDialog(
    floor: FloorRow,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = AppShapes.card,
        title = { Text("Delete ${floor.name}?", style = AppType.sectionHeader) },
        text = {
            Text(
                text = if (floor.deviceCount == 0) {
                    "This floor and its grid will be removed."
                } else {
                    "${floor.deviceCount} device${if (floor.deviceCount == 1) "" else "s"} " +
                        "placed on this floor will be deleted too, along with their usage " +
                        "history. This can't be undone."
                },
                style = AppType.body,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete floor", style = AppType.label, color = colors.stateError)
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
// Loading skeletons
// ---------------------------------------------------------------------------

/**
 * A block of `surfaceVariant` where content is about to be.
 *
 * **No shimmer.** A sweeping highlight is an animation that says "something is happening"
 * in a design system where animation means "something has changed" — and on a screen whose
 * whole promise is that movement is real, a decorative one is a lie the user learns to
 * discount.
 */
@Composable
private fun SkeletonBlock(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width, height)
            .background(SmartHomeTheme.colors.surfaceVariant, AppShapes.chip),
    )
}

/**
 * The summary row, before the numbers arrive.
 *
 * It is the real card with grey blocks inside rather than a plain grey rectangle, so that
 * nothing moves when the data lands — the tiles are already the right size in the right
 * places, and the only change is that they start saying something.
 */
@Composable
private fun SummarySkeleton() {
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            repeat(4) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SkeletonBlock(18.dp, 18.dp)
                    SkeletonBlock(32.dp, 26.dp)
                    SkeletonBlock(44.dp, 10.dp)
                }
            }
        }
    }
}

@Composable
private fun FloorCardSkeleton() {
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(64.dp, 64.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SkeletonBlock(120.dp, 14.dp)
                SkeletonBlock(90.dp, 10.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Artboards — the section 10 deliverable
// ---------------------------------------------------------------------------

/** A fixed clock, so every artboard renders the same `4 min ago` every time. */
private const val PreviewNow = 1_755_264_000_000L

private fun minutesAgo(minutes: Long) = PreviewNow - minutes * 60_000L

private val PreviewFloors = listOf(
    FloorRow(id = "ground", name = "Ground Floor", deviceCount = 7, activeCount = 2),
    FloorRow(id = "first", name = "First Floor", deviceCount = 5, activeCount = 1),
)

private val PreviewCalm = FloorListUiState(
    isLoading = false,
    summary = HouseSummary(totalDevices = 12, activeNow = 3, errors = 0, warnings = 0),
    floors = PreviewFloors,
    recentEvents = listOf(
        EventRow(
            id = "e1",
            deviceName = "Iron",
            reason = "Maximum active time exceeded",
            type = AlertType.MAX_DURATION_EXCEEDED,
            createdAtMillis = minutesAgo(320),
            acknowledged = true,
        ),
        EventRow(
            id = "e2",
            deviceName = "Water heater",
            reason = "Maximum active time exceeded",
            type = AlertType.MAX_DURATION_EXCEEDED,
            createdAtMillis = minutesAgo(400),
            acknowledged = true,
        ),
    ),
    nowMillis = PreviewNow,
)

private val PreviewCritical = PreviewCalm.copy(
    summary = HouseSummary(totalDevices = 12, activeNow = 4, errors = 1, warnings = 1),
    floors = listOf(
        PreviewFloors[0].copy(tier = PriorityTier.CRITICAL, flaggedDevices = 1),
        PreviewFloors[1].copy(tier = PriorityTier.ATTENTION, flaggedDevices = 1),
    ),
    banner = HomeBanner.Single(
        alertId = "a1",
        deviceId = "d1",
        floorId = "ground",
        cause = "Iron switched off automatically",
        reason = "Maximum active time exceeded",
        createdAtMillis = minutesAgo(4),
    ),
    recentEvents = listOf(
        EventRow(
            id = "e1",
            deviceName = "Iron",
            reason = "Maximum active time exceeded",
            type = AlertType.MAX_DURATION_EXCEEDED,
            createdAtMillis = minutesAgo(4),
            acknowledged = false,
        ),
        EventRow(
            id = "e2",
            deviceName = "Kitchen outlet",
            reason = "Device reported a fault",
            type = AlertType.DEVICE_ERROR,
            createdAtMillis = minutesAgo(90),
            acknowledged = true,
        ),
    ),
    unacknowledgedCount = 1,
)

@Composable
private fun Artboard(
    state: FloorListUiState,
    dark: Boolean = true,
    isOffline: Boolean = false,
) {
    SmartHomeTheme(darkTheme = dark) {
        FloorListContent(
            state = state,
            isOffline = isOffline,
            onOpenFloor = {},
            onAddFloor = {},
            onOpenDevice = { _, _ -> },
            onNavigate = {},
            onRenameFloor = { _, _ -> },
            onDeleteFloor = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Floor list · loading", widthDp = 412, heightDp = 915)
@Composable
private fun FloorListLoading() = Artboard(FloorListUiState(nowMillis = PreviewNow))

@Preview(name = "Floor list · empty", widthDp = 412, heightDp = 915)
@Composable
private fun FloorListEmpty() = Artboard(
    FloorListUiState(isLoading = false, nowMillis = PreviewNow),
)

@Preview(name = "Floor list · populated calm", widthDp = 412, heightDp = 915)
@Composable
private fun FloorListCalm() = Artboard(PreviewCalm)

@Preview(name = "Floor list · populated critical", widthDp = 412, heightDp = 915)
@Composable
private fun FloorListCritical() = Artboard(PreviewCritical)

@Preview(name = "Floor list · offline", widthDp = 412, heightDp = 915)
@Composable
private fun FloorListOffline() = Artboard(PreviewCalm, isOffline = true)

@Preview(name = "Floor list · populated calm, light", widthDp = 412, heightDp = 915)
@Composable
private fun FloorListCalmLight() = Artboard(PreviewCalm, dark = false)
