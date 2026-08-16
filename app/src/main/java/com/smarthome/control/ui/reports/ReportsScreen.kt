package com.smarthome.control.ui.reports

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.components.EmptyState
import com.smarthome.control.ui.components.PriorityContainer
import com.smarthome.control.ui.components.StatCard
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.navigation.AppBottomBar
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Screen prompt 11 — Reports.
 *
 * The brief leaves the presentation of usage data open, which is the trap: an open brief
 * invites six charts nobody reads. This screen answers three questions and stops — what ran
 * the most, how that compares to before, and whether the safety system is firing more often
 * than it should.
 *
 * The third is the one that makes this more than a usage dashboard, and it is why the
 * cutoffs section renders even when it is empty. Its absence would be a positive result
 * hidden by its own success.
 */
@Composable
fun ReportsScreen(
    onOpenDevice: (deviceId: String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ReportsContent(
        state = state,
        isOffline = rememberIsOffline(),
        onRange = viewModel::setRange,
        onRefresh = viewModel::refresh,
        onOpenDevice = onOpenDevice,
        onNavigate = onNavigate,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportsContent(
    state: ReportsUiState,
    isOffline: Boolean,
    onRange: (ReportRange) -> Unit,
    onRefresh: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Reports", style = AppType.display) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                    ),
                    actions = {
                        // The only manual refresh in the app, and correct precisely because
                        // nothing on this screen claims to be live.
                        IconButton(onClick = onRefresh, enabled = !isOffline) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isOffline) colors.outline else colors.textSecondary,
                            )
                        }
                    },
                )

                if (isOffline) {
                    Text(
                        text = "Showing cached data",
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
        bottomBar = { AppBottomBar(current = AppDestination.Reports, onSelect = onNavigate) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal),
            // More generous than elsewhere in the app: charts need breathing room that
            // lists do not.
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            RangeSelector(
                current = state.range,
                onSelect = onRange,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            when {
                state.loadError != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(state.loadError, style = AppType.body, color = colors.stateError)
                    TextButton(onClick = onRefresh) {
                        Text("Try again", style = AppType.label, color = colors.primary)
                    }
                }

                state.isLoading -> SkeletonSections()

                state.isEmpty -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        icon = Icons.Rounded.QueryStats,
                        message = state.emptyMessage,
                        actionLabel = if (state.showsRangeReset) "Show today" else null,
                        onAction = { onRange(ReportRange.Today) },
                    )
                    // Even with nothing to chart, the safety section stays. Its absence
                    // would hide the feature from the user who has never triggered it.
                    CutoffsSection(state = state, onOpenDevice = onOpenDevice)
                }

                else -> {
                    HeadlineStats(state)

                    Section("DEVICES BY ON-TIME") {
                        DeviceBarChart(
                            bars = state.deviceBars.take(TopBars),
                            spokenSummary = state.barsSpoken,
                            onShowAll = null.takeIf { state.deviceBars.size <= TopBars },
                        )
                    }

                    if (state.showsTrend) {
                        Section("DAILY TREND") {
                            DailyTrendChart(days = state.dailyTrend)
                        }
                    }

                    CutoffsSection(state = state, onOpenDevice = onOpenDevice)
                }
            }

            Box(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun HeadlineStats(state: ReportsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatCard(
            value = state.totalOnLabel,
            unit = "",
            label = "Total on",
            // The one sparkline in the app that earns its place: daily totals are
            // continuous values, unlike a binary device's on/off day.
            trend = state.dailyTotals,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = state.devicesUsed.toString(),
            unit = "",
            label = "Devices used",
            modifier = Modifier.weight(1f),
        )
        // Attention tier above zero. A device hitting its cutoff is not a neutral statistic.
        PriorityContainer(
            tier = if (state.cutoffCount > 0) PriorityTier.ATTENTION else PriorityTier.NORMAL,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = state.cutoffCount.toString(),
                    style = AppType.numericLarge,
                    color = SmartHomeTheme.colors.textPrimary,
                )
                Text(
                    text = "Cutoffs",
                    style = AppType.label,
                    color = SmartHomeTheme.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The smallest section on the screen and the most valuable.
 *
 * A device appearing here repeatedly means either its limit is too short for how it is
 * really used, or it is genuinely being left on. Both are actionable, which is why a row
 * opens that device's hazard sheet — the user adjusts the limit from the evidence, in one
 * tap, without hunting for it on a floor plan.
 */
@Composable
private fun CutoffsSection(state: ReportsUiState, onOpenDevice: (String) -> Unit) {
    val colors = SmartHomeTheme.colors

    Section("AUTOMATIC CUTOFFS") {
        if (state.cutoffRows.isEmpty()) {
            Text(
                text = "No automatic cutoffs in this period.",
                style = AppType.body,
                color = colors.textSecondary,
            )
            return@Section
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.cutoffRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDevice(row.deviceId) }
                        .padding(vertical = Spacing.xs)
                        .semantics {
                            contentDescription =
                                "${row.deviceName}, ${row.count} cutoffs. Double tap to adjust its limit."
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        Icons.Rounded.TimerOff,
                        contentDescription = null,
                        tint = colors.stateError,
                    )
                    Text(
                        text = row.deviceName,
                        style = AppType.body,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${row.count} ×",
                        style = AppType.numeric,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(
    current: ReportRange,
    onSelect: (ReportRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Report range" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ReportRange.entries.forEach { range ->
            val selected = range == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) colors.primary else colors.surfaceVariant,
                        RoundedCornerShape(percent = 50),
                    )
                    .border(
                        AppBorders.hairline,
                        if (selected) colors.primary else colors.outline,
                        RoundedCornerShape(percent = 50),
                    )
                    .clickable { onSelect(range) }
                    .padding(vertical = Spacing.sm)
                    .semantics {
                        contentDescription = "${range.label}${if (selected) ", selected" else ""}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = range.label,
                    style = AppType.label,
                    color = if (selected) colors.onPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Section(header: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = header,
            style = AppType.label,
            color = SmartHomeTheme.colors.textSecondary,
        )
        content()
    }
}

/** Sized to the sections they stand in for, and static — see the class comment on animation. */
@Composable
private fun SkeletonSections() {
    val colors = SmartHomeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        listOf(96.dp, 180.dp, 140.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(colors.surfaceVariant, AppShapes.card),
            )
        }
    }
}

private const val TopBars = 5

// ---------------------------------------------------------------------------
// Artboards — the section 13 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_267_300_000L

private val PreviewBars = listOf(
    DeviceBar("d1", "Ceiling fan", DeviceType.OUTLET, 6 * 3600L + 12 * 60L, 1f),
    DeviceBar("d2", "Porch light", DeviceType.LIGHT, 4 * 3600L + 30 * 60L, 0.72f),
    DeviceBar("d3", "Kitchen outlet", DeviceType.OUTLET, 3 * 3600L + 5 * 60L, 0.49f),
    DeviceBar("d4", "Bedroom iron", DeviceType.APPLIANCE, 1 * 3600L + 20 * 60L, 0.21f),
    DeviceBar("d5", "Hall light", DeviceType.LIGHT, 48 * 60L, 0.13f),
)

private val PreviewTrend = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { index, label ->
    DayStack(
        label = label,
        secondsByType = mapOf(
            DeviceType.OUTLET to (2 + index % 3) * 3600L,
            DeviceType.LIGHT to (1 + index % 2) * 3600L,
            DeviceType.APPLIANCE to (index % 2) * 1800L,
        ),
        topDeviceName = "Ceiling fan",
    )
}

private val PreviewToday = ReportsUiState(
    isLoading = false,
    range = ReportRange.Today,
    totalOnSeconds = 18 * 3600L + 42 * 60L,
    devicesUsed = 12,
    cutoffCount = 3,
    dailyTotals = listOf(3f, 5f, 4f, 6f, 5f, 7f, 6.2f),
    deviceBars = PreviewBars,
    dailyTrend = PreviewTrend,
    cutoffRows = listOf(
        CutoffRow("d4", "Bedroom Iron", 3),
        CutoffRow("d6", "Space Heater", 1),
    ),
    hasAnyUsage = true,
    nowMillis = PreviewNow,
)

@Composable
private fun Artboard(state: ReportsUiState, dark: Boolean = true, isOffline: Boolean = false) {
    SmartHomeTheme(darkTheme = dark) {
        ReportsContent(
            state = state,
            isOffline = isOffline,
            onRange = {},
            onRefresh = {},
            onOpenDevice = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Reports · today with data", widthDp = 412, heightDp = 915)
@Composable
private fun ReportsTodayPreview() = Artboard(PreviewToday)

@Preview(name = "Reports · 7 days with trend", widthDp = 412, heightDp = 1100)
@Composable
private fun ReportsWeekPreview() = Artboard(PreviewToday.copy(range = ReportRange.Week))

@Preview(name = "Reports · no data at all", widthDp = 412, heightDp = 915)
@Composable
private fun ReportsEmptyPreview() = Artboard(
    ReportsUiState(isLoading = false, hasAnyUsage = false, nowMillis = PreviewNow),
)

@Preview(name = "Reports · no data in range", widthDp = 412, heightDp = 915)
@Composable
private fun ReportsRangeEmptyPreview() = Artboard(
    ReportsUiState(
        isLoading = false,
        range = ReportRange.Week,
        hasAnyUsage = true,
        nowMillis = PreviewNow,
    ),
)

@Preview(name = "Reports · zero cutoffs", widthDp = 412, heightDp = 915)
@Composable
private fun ReportsZeroCutoffsPreview() = Artboard(
    PreviewToday.copy(cutoffCount = 0, cutoffRows = emptyList()),
)

@Preview(name = "Reports · offline", widthDp = 412, heightDp = 915)
@Composable
private fun ReportsOfflinePreview() = Artboard(PreviewToday, isOffline = true)

@Preview(name = "Reports · 7 days, light", widthDp = 412, heightDp = 1100)
@Composable
private fun ReportsLightPreview() = Artboard(PreviewToday.copy(range = ReportRange.Week), dark = false)
