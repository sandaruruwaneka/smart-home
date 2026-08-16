package com.smarthome.control.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.data.model.AlertType as ContractAlertType
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.components.AlertBanner
import com.smarthome.control.ui.components.AlertRow
import com.smarthome.control.ui.components.AlertType
import com.smarthome.control.ui.components.EmptyState
import com.smarthome.control.ui.navigation.AppBottomBar
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Screen prompt 09 — Alerts.
 *
 * The app's evidence that the server-side cutoff works: the only place the safety worker's
 * output accumulates where a person can see it, and the screen an examiner opens after
 * watching an iron switch itself off.
 *
 * Two jobs pulling opposite ways. Something just happened — understand what, on which
 * device, and get there in five seconds. Nothing just happened — scan the history and
 * confirm the system has been working. The screen serves the first with a Critical banner
 * and a dot per outstanding row, and the second by leaving every older row at Normal tier
 * as a plain record. Escalating the whole list would make the calm reading feel like an
 * emergency, which is the failure mode of every log screen that shouts.
 *
 * There is no delete, anywhere, by design — see [AlertsListItem].
 */
@Composable
fun AlertsScreen(
    onOpenDevice: (deviceId: String, floorId: String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    AlertsContent(
        state = state,
        isOffline = rememberIsOffline(),
        snackbarHostState = snackbarHostState,
        onOpenDevice = onOpenDevice,
        onNavigate = onNavigate,
        onFilter = viewModel::setFilter,
        onAcknowledge = viewModel::acknowledge,
        onAcknowledgeAll = viewModel::acknowledgeAll,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertsContent(
    state: AlertsUiState,
    isOffline: Boolean,
    onOpenDevice: (String, String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onFilter: (AlertFilter) -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    forcePill: Int = 0,
) {
    val colors = SmartHomeTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var longPressed by remember { mutableStateOf<AlertRowUiState?>(null) }

    val newestId = state.sections.firstOrNull()?.rows?.firstOrNull()?.alertId
    var pillCount by remember { mutableStateOf(forcePill) }
    var lastTopId by remember { mutableStateOf(newestId) }
    val scrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Section 8: the list never reorders under a scrolling finger. An alert arriving while
    // the user is reading further down is inserted silently and announced by a pill instead.
    LaunchedEffect(newestId) {
        if (newestId != null && newestId != lastTopId) {
            if (scrolledAway) pillCount += 1 else lastTopId = newestId
        }
    }
    LaunchedEffect(scrolledAway) {
        if (!scrolledAway) {
            pillCount = 0
            lastTopId = newestId
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Alerts", style = AppType.display) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                    ),
                    actions = {
                        if (state.unacknowledgedCount > 0) {
                            IconButton(onClick = onAcknowledgeAll) {
                                Icon(
                                    Icons.Rounded.DoneAll,
                                    contentDescription = "Acknowledge all",
                                    tint = colors.primary,
                                )
                            }
                        }
                    },
                )

                // Acknowledgement is never disabled offline: Firestore queues the write and
                // syncs it on reconnect, so the only thing disabling it would achieve is
                // making a working action look broken.
                if (isOffline) {
                    Text(
                        text = "Showing last known state",
                        style = AppType.label,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant)
                            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                    )
                }
            }
        },
        bottomBar = {
            AppBottomBar(
                current = AppDestination.Alerts,
                onSelect = onNavigate,
                unacknowledgedAlerts = state.unacknowledgedCount,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                state.banner?.let { banner ->
                    AlertBanner(
                        cause = banner.cause,
                        reason = banner.reason,
                        timestamp = banner.timestamp,
                        actionLabel = "Acknowledge",
                        onAction = onAcknowledgeAll,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenHorizontal,
                            vertical = Spacing.sm,
                        ),
                    )
                }

                FilterChips(
                    current = state.filter,
                    onSelect = onFilter,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenHorizontal,
                        vertical = Spacing.sm,
                    ),
                )

                when {
                    state.loadError != null -> LoadFailure(
                        message = state.loadError,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.isLoading -> SkeletonRows()

                    state.isEmpty -> EmptyAlerts(
                        state = state,
                        onShowAll = { onFilter(AlertFilter.All) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.screenHorizontal,
                            vertical = Spacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        state.sections.forEach { section ->
                            item(key = "header-${section.header}") {
                                Text(
                                    text = section.header,
                                    style = AppType.sectionHeader,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                                )
                            }
                            items(section.rows, key = { it.alertId }) { row ->
                                AlertsListItem(
                                    row = row,
                                    onOpen = { onOpenDevice(row.deviceId, row.floorId) },
                                    onLongPress = { longPressed = row },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = pillCount > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.sm),
            ) {
                NewAlertPill(count = pillCount) {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        }
    }

    longPressed?.let { row ->
        RowActionsDialog(
            row = row,
            onDismiss = { longPressed = null },
            onAcknowledge = { longPressed = null; onAcknowledge(row.alertId) },
            onOpenDevice = { longPressed = null; onOpenDevice(row.deviceId, row.floorId) },
        )
    }
}

/**
 * One alert.
 *
 * There is no delete here and none anywhere else on the screen. The alert history is the
 * evidence that the safety system fired; letting a user clear it removes the only record
 * proving the feature works — which matters for the product and for defending it at the
 * demo. Acknowledging is the only state change, and it deletes nothing.
 */
@Composable
private fun AlertsListItem(
    row: AlertRowUiState,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    Row(verticalAlignment = Alignment.CenterVertically) {
        // The leading gutter. Present or absent, never coloured differently — an
        // acknowledged row simply has nothing here.
        Box(
            modifier = Modifier
                .size(DotGutter)
                .padding(end = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            if (!row.acknowledged) {
                Box(
                    modifier = Modifier
                        .size(DotSize)
                        .background(colors.stateError, CircleShape),
                )
            }
        }

        AlertRow(
            deviceName = row.deviceName,
            reason = row.message,
            timestamp = row.locationLine,
            type = row.type.toComponentType(),
            acknowledged = row.acknowledged,
            arrivalToken = row.arrivalToken,
            contentDescription = row.spoken,
            onClick = onOpen,
            onLongClick = onLongPress,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterChips(
    current: AlertFilter,
    onSelect: (AlertFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AlertFilter.entries.forEach { filter ->
            val selected = filter == current
            Box(
                modifier = Modifier
                    .background(
                        if (selected) colors.primary else colors.surfaceVariant,
                        RoundedCornerShape(percent = 50),
                    )
                    .border(
                        AppBorders.hairline,
                        if (selected) colors.primary else colors.outline,
                        RoundedCornerShape(percent = 50),
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .semantics {
                        contentDescription = "${filter.label}${if (selected) ", selected" else ""}"
                    },
            ) {
                Text(
                    text = filter.label,
                    style = AppType.label,
                    color = if (selected) colors.onPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun NewAlertPill(count: Int, onClick: () -> Unit) {
    val colors = SmartHomeTheme.colors
    Box(
        modifier = Modifier
            .background(colors.primary, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = if (count == 1) "1 new alert" else "$count new alerts",
            style = AppType.label,
            color = colors.onPrimary,
        )
    }
}

@Composable
private fun EmptyAlerts(
    state: AlertsUiState,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.NotificationsNone,
            message = state.emptyMessage,
            // A true-empty screen offers nothing to do, because there is nothing useful to
            // do; a filtered-empty one offers the way back.
            actionLabel = if (state.showsShowAllAction) "Show all" else null,
            onAction = onShowAll,
        )
    }
}

/** Three bars, no shimmer. A pulsing skeleton on a safety screen reads as activity. */
@Composable
private fun SkeletonRows() {
    val colors = SmartHomeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SkeletonHeight)
                    .background(colors.surfaceVariant, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun RowActionsDialog(
    row: AlertRowUiState,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onOpenDevice: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text(row.deviceName, style = AppType.sectionHeader) },
        text = {
            Column {
                // The non-gesture equivalent of the swipe (section 11).
                if (!row.acknowledged) {
                    TextButton(onClick = onAcknowledge) {
                        Text("Acknowledge", style = AppType.body, color = colors.textPrimary)
                    }
                }
                TextButton(onClick = onOpenDevice) {
                    Text("Go to device", style = AppType.body, color = colors.textPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun LoadFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SmartHomeTheme.colors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, style = AppType.body, color = colors.stateError)
            TextButton(onClick = onRetry) {
                Text("Try again", style = AppType.label, color = colors.primary)
            }
        }
    }
}

private fun ContractAlertType.toComponentType(): AlertType = when (this) {
    ContractAlertType.MAX_DURATION_EXCEEDED -> AlertType.MAX_DURATION_EXCEEDED
    ContractAlertType.DEVICE_ERROR -> AlertType.DEVICE_ERROR
}

private val DotGutter = 16.dp
private val DotSize = 8.dp
private val SkeletonHeight = 72.dp

// ---------------------------------------------------------------------------
// Artboards — the section 12 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_267_300_000L

private fun row(
    id: String,
    name: String,
    message: String,
    type: ContractAlertType,
    location: String,
    acknowledged: Boolean,
) = AlertRowUiState(
    alertId = id,
    deviceId = "dev-$id",
    floorId = "first",
    deviceName = name,
    message = message,
    type = type,
    locationLine = location,
    acknowledged = acknowledged,
    createdAtMillis = PreviewNow,
)

private val PreviewUnacknowledged = AlertsUiState(
    isLoading = false,
    sections = listOf(
        AlertSection(
            header = "TODAY",
            rows = listOf(
                row("a1", "Bedroom Iron", "Maximum on time reached", ContractAlertType.MAX_DURATION_EXCEEDED, "First Floor · 17:42", false),
                row("a2", "Porch Light", "Device reported a fault", ContractAlertType.DEVICE_ERROR, "Ground Floor · 14:03", true),
            ),
        ),
        AlertSection(
            header = "YESTERDAY",
            rows = listOf(
                row("a3", "Bedroom Iron", "Maximum on time reached", ContractAlertType.MAX_DURATION_EXCEEDED, "First Floor · 19:11", true),
            ),
        ),
    ),
    banner = AlertsBanner(
        cause = "2 alerts need your attention",
        reason = "Devices were switched off automatically or reported a fault",
        timestamp = "17:42",
        count = 2,
    ),
    unacknowledgedCount = 2,
    hasAnyAlerts = true,
    nowMillis = PreviewNow,
)

@Composable
private fun Artboard(
    state: AlertsUiState,
    dark: Boolean = true,
    isOffline: Boolean = false,
    pill: Int = 0,
) {
    SmartHomeTheme(darkTheme = dark) {
        AlertsContent(
            state = state,
            isOffline = isOffline,
            onOpenDevice = { _, _ -> },
            onNavigate = {},
            onFilter = {},
            onAcknowledge = {},
            onAcknowledgeAll = {},
            onRetry = {},
            forcePill = pill,
        )
    }
}

@Preview(name = "Alerts · unacknowledged", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsUnacknowledgedPreview() = Artboard(PreviewUnacknowledged)

@Preview(name = "Alerts · all acknowledged", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsAcknowledgedPreview() = Artboard(
    PreviewUnacknowledged.copy(
        banner = null,
        unacknowledgedCount = 0,
        sections = PreviewUnacknowledged.sections.map { section ->
            section.copy(rows = section.rows.map { it.copy(acknowledged = true) })
        },
    ),
)

@Preview(name = "Alerts · empty, never had any", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsEmptyPreview() = Artboard(
    AlertsUiState(isLoading = false, nowMillis = PreviewNow),
)

@Preview(name = "Alerts · filtered empty", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsFilteredEmptyPreview() = Artboard(
    AlertsUiState(
        isLoading = false,
        filter = AlertFilter.Faults,
        hasAnyAlerts = true,
        nowMillis = PreviewNow,
    ),
)

@Preview(name = "Alerts · new arrival pill", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsPillPreview() = Artboard(PreviewUnacknowledged, pill = 1)

@Preview(name = "Alerts · offline", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsOfflinePreview() = Artboard(PreviewUnacknowledged, isOffline = true)

@Preview(name = "Alerts · unacknowledged, light", widthDp = 412, heightDp = 915)
@Composable
private fun AlertsLightPreview() = Artboard(PreviewUnacknowledged, dark = false)
