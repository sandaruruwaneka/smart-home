package com.smarthome.control.ui.reports

import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.ui.device.formatOnDuration
import com.smarthome.control.ui.device.startOfDay
import com.smarthome.control.ui.model.DeviceType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Everything the reports screen draws.
 *
 * The brief leaves the presentation of usage data open, and that freedom is the trap: an
 * open brief invites six charts nobody reads. This screen answers three questions and
 * stops — what ran the most, how that compares to before, and whether the safety system is
 * firing more often than it should.
 *
 * The third one is what separates this from a generic usage dashboard. A device repeatedly
 * hitting its cutoff is a real signal about a real hazard, and it is the only analysis in
 * the project the data actually justifies.
 *
 * Everything here is aggregated client-side from one query per range. Thirty days at this
 * project's scale is a few hundred documents, so server-side aggregation would be
 * infrastructure built for a problem nobody has.
 */
data class ReportsUiState(
    val isLoading: Boolean = true,
    val range: ReportRange = ReportRange.Today,
    val totalOnSeconds: Long = 0,
    val devicesUsed: Int = 0,
    val cutoffCount: Int = 0,
    /** Daily totals in hours, oldest first — the one sparkline in the app that earns its place. */
    val dailyTotals: List<Float> = emptyList(),
    val deviceBars: List<DeviceBar> = emptyList(),
    val dailyTrend: List<DayStack> = emptyList(),
    val cutoffRows: List<CutoffRow> = emptyList(),
    /** False only when the account has never recorded a single usage event. */
    val hasAnyUsage: Boolean = false,
    val loadError: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val totalOnLabel: String get() = formatOnDuration(totalOnSeconds)

    /** `Today` renders no trend at all rather than a chart with one bar in it. */
    val showsTrend: Boolean get() = range != ReportRange.Today && dailyTrend.isNotEmpty()

    val isEmpty: Boolean get() = deviceBars.isEmpty()

    /**
     * The two empty states say different things, as on the alerts screen.
     *
     * "Nothing in this range" is a prompt to widen it; "nothing ever" is a statement that
     * the house has not been used yet, and offering a range button for that would be
     * pointing at the wrong problem.
     */
    val emptyMessage: String
        get() = if (hasAnyUsage) {
            // "No activity in the last today." is what the obvious one-liner produces.
            if (range == ReportRange.Today) {
                "No activity today."
            } else {
                "No activity in the last ${range.label.lowercase()}."
            }
        } else {
            "No usage recorded yet. Turn a device on and its activity will appear here."
        }

    val showsRangeReset: Boolean get() = isEmpty && hasAnyUsage && range != ReportRange.Today

    /** `Ceiling fan 6 hours 12 minutes, Porch light 4 hours 30 minutes…` — section 12. */
    val barsSpoken: String
        get() = if (deviceBars.isEmpty()) {
            "No device usage in this period."
        } else {
            deviceBars.joinToString(", ") { "${it.name} ${spellDuration(it.onSeconds)}" }
        }
}

enum class ReportRange(val label: String, val days: Int) {
    Today("Today", 1),
    Week("7 days", 7),
    Month("30 days", 30),
}

/**
 * One bar in the primary chart.
 *
 * @param fraction of the largest bar, not of the total. The chart scales to its own
 *   maximum so the shortest bar is still visible; scaling to a fixed ceiling would render
 *   a quiet week as five hairlines.
 */
data class DeviceBar(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val onSeconds: Long,
    val fraction: Float,
) {
    val label: String get() = formatOnDuration(onSeconds)

    /**
     * Appliances draw in `stateOn` rather than `primary`.
     *
     * A visual reminder that the tallest bars on this chart may be the ones worth worrying
     * about rather than the ones worth being pleased with.
     */
    val isHazardClass: Boolean get() = type.isHazardClass
}

/** One day of the trend, split by device type. */
data class DayStack(
    val label: String,
    val secondsByType: Map<DeviceType, Long>,
    val topDeviceName: String?,
) {
    val totalSeconds: Long get() = secondsByType.values.sum()
    val totalLabel: String get() = formatOnDuration(totalSeconds)
}

/** One device that hit its limit, and how often. */
data class CutoffRow(val deviceId: String, val deviceName: String, val count: Int)

/**
 * Aggregates a range's events into the whole screen.
 *
 * Pure, and given every input explicitly, so a month of usage can be asserted on without a
 * Firestore anywhere near it — which matters more here than on any other screen, because a
 * chart that is subtly wrong looks exactly like a chart that is right.
 */
internal fun buildReportsState(
    range: ReportRange,
    events: List<UsageEvent>,
    devices: List<Device>,
    alerts: List<Alert>,
    nowMillis: Long,
    zone: ZoneId,
    hasAnyUsage: Boolean = events.isNotEmpty(),
): ReportsUiState {
    val todayStart = startOfDay(nowMillis, zone)
    val rangeStart = todayStart - (range.days - 1).toLong() * MillisPerDay
    val deviceById = devices.associateBy { it.id }

    // Every figure is the overlap between a period and the window, never `duration_seconds`
    // alone: an open event has no duration yet, and a run that began before the window
    // opened would otherwise donate its whole length to this range.
    val clipped = events.mapNotNull { event ->
        val start = event.startedAt?.toDate()?.time ?: return@mapNotNull null
        val end = event.endedAt?.toDate()?.time ?: nowMillis
        val from = maxOf(start, rangeStart)
        val to = minOf(maxOf(end, start), nowMillis)
        if (to > from) ClippedRun(event.deviceId, from, to) else null
    }.mergePerDevice()

    val totalMillis = clipped.sumOf { it.to - it.from }

    val byDevice = clipped.groupBy { it.deviceId }
        .map { (deviceId, runs) ->
            val seconds = TimeUnit.MILLISECONDS.toSeconds(runs.sumOf { it.to - it.from })
            val device = deviceById[deviceId]
            DeviceBar(
                deviceId = deviceId,
                // A device deleted since its usage was recorded still has history worth
                // showing; naming it "Removed device" beats dropping the hours entirely.
                name = device?.name ?: "Removed device",
                type = device?.type ?: DeviceType.OUTLET,
                onSeconds = seconds,
                fraction = 0f,
            )
        }
        .sortedByDescending { it.onSeconds }

    val largest = byDevice.firstOrNull()?.onSeconds ?: 0L
    val bars = byDevice.map { bar ->
        bar.copy(fraction = if (largest > 0) bar.onSeconds.toFloat() / largest else 0f)
    }

    val days = (0 until range.days).map { offset ->
        val dayStart = rangeStart + offset * MillisPerDay
        dayStack(dayStart, clipped, deviceById, zone, range)
    }

    val cutoffs = alerts
        .filter { it.type == AlertType.MAX_DURATION_EXCEEDED }
        .filter { (it.createdAt?.toDate()?.time ?: nowMillis) >= rangeStart }

    return ReportsUiState(
        isLoading = false,
        range = range,
        totalOnSeconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis),
        devicesUsed = clipped.map { it.deviceId }.distinct().size,
        cutoffCount = cutoffs.size,
        dailyTotals = days.map { it.totalSeconds / SecondsPerHour },
        deviceBars = bars,
        dailyTrend = days,
        cutoffRows = cutoffs
            .groupBy { it.deviceId }
            .map { (deviceId, list) ->
                CutoffRow(deviceId, list.first().deviceName, list.size)
            }
            .sortedByDescending { it.count },
        hasAnyUsage = hasAnyUsage,
        nowMillis = nowMillis,
    )
}

private fun dayStack(
    dayStartMillis: Long,
    runs: List<ClippedRun>,
    deviceById: Map<String, Device>,
    zone: ZoneId,
    range: ReportRange,
): DayStack {
    val dayEnd = dayStartMillis + MillisPerDay
    val byType = mutableMapOf<DeviceType, Long>()
    val byDevice = mutableMapOf<String, Long>()

    runs.forEach { run ->
        val overlap = (minOf(run.to, dayEnd) - maxOf(run.from, dayStartMillis)).coerceAtLeast(0L)
        if (overlap <= 0L) return@forEach
        val type = deviceById[run.deviceId]?.type ?: DeviceType.OUTLET
        byType[type] = (byType[type] ?: 0L) + TimeUnit.MILLISECONDS.toSeconds(overlap)
        byDevice[run.deviceId] = (byDevice[run.deviceId] ?: 0L) + overlap
    }

    return DayStack(
        label = axisLabel(dayStartMillis, zone, range),
        secondsByType = byType,
        topDeviceName = byDevice.maxByOrNull { it.value }?.key?.let { deviceById[it]?.name },
    )
}

/**
 * Weekday initials over a week, dates over a month.
 *
 * Thirty weekday letters would repeat four times over and say nothing; thirty dates would
 * overlap, so the chart labels every fifth one and lets the bars carry the rest.
 */
private fun axisLabel(millis: Long, zone: ZoneId, range: ReportRange): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone)
    return when (range) {
        ReportRange.Today -> date.format(HourFormat)
        ReportRange.Week -> date.format(WeekdayFormat).take(1)
        ReportRange.Month -> date.dayOfMonth.toString()
    }
}

/** `6 hours 12 minutes` — spoken, where `6h 12m` is written. */
private fun spellDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val hourPart = if (hours > 0) "$hours ${if (hours == 1L) "hour" else "hours"}" else null
    val minutePart = if (minutes > 0) "$minutes ${if (minutes == 1L) "minute" else "minutes"}" else null
    return listOfNotNull(hourPart, minutePart).joinToString(" ").ifEmpty { "under a minute" }
}

private data class ClippedRun(val deviceId: String, val from: Long, val to: Long)

/**
 * Collapses overlapping runs of the same device into single intervals.
 *
 * A three-gang plate with every channel on has three concurrent usage events, and summing
 * them makes one device report more hours than the day contains -- 40h in a 24h day, which
 * is the kind of number that costs a screen its credibility no matter how defensible the
 * arithmetic behind it.
 *
 * "How long was this device on" is a wall-clock question, so overlapping periods merge.
 * That is deliberately *not* what the multi-switch sheet does: its `Combined on` is
 * additive on purpose and labelled `Combined` precisely so the two figures cannot be
 * mistaken for each other. Here the label is `Total on`, and it means what it says.
 *
 * Channels of the same unit are already attributed to the parent by `device_id`, so this
 * needs no knowledge of channels at all.
 */
private fun List<ClippedRun>.mergePerDevice(): List<ClippedRun> =
    groupBy { it.deviceId }.flatMap { (deviceId, runs) ->
        val merged = mutableListOf<ClippedRun>()
        runs.sortedBy { it.from }.forEach { run ->
            val last = merged.lastOrNull()
            if (last != null && run.from <= last.to) {
                // Overlapping or touching: extend the interval rather than adding a second.
                merged[merged.lastIndex] = last.copy(to = maxOf(last.to, run.to))
            } else {
                merged += ClippedRun(deviceId, run.from, run.to)
            }
        }
        merged
    }

private const val MillisPerDay = 24L * 60L * 60L * 1000L
private const val SecondsPerHour = 3600f

private val WeekdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val HourFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH", Locale.ENGLISH)
