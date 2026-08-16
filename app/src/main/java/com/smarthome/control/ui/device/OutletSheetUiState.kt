package com.smarthome.control.ui.device

import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.ui.model.DeviceState
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Everything the outlet control sheet draws.
 *
 * Plain values, no Firestore: the six artboards in section 11 of screen prompt 04 are
 * built by hand from this class, and so are the tests.
 */
data class OutletSheetUiState(
    val isLoading: Boolean = true,
    val deviceName: String = "",
    /** `Ground Floor · R2 C5`. */
    val locationLine: String = "",
    val state: DeviceState = DeviceState.OFF,
    /**
     * The app has written and Firestore has not confirmed yet.
     *
     * Straight from [Live.isFromServer], for the same reason the floor dashboard uses it:
     * comparing `last_changed_by` cannot tell this app's write coming back from somebody
     * else's.
     */
    val pendingWrite: Boolean = false,
    val lastChangedMillis: Long? = null,
    /** Null means nothing ran today, which is a line of copy rather than an empty chart. */
    val usage: DayUsage? = null,
    /** A failed write, rename or delete, shown under the control card. */
    val actionError: String? = null,
    val loadError: String? = null,
    /** The document is gone — deleted here or elsewhere. The sheet dismisses itself. */
    val isMissing: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    /**
     * Whether the switch does anything.
     *
     * A device in `ERROR` or `DISCONNECTED` is not one the app can reach, and a control
     * that invites a tap it cannot honour is worse than a disabled one.
     */
    val canSwitch: Boolean get() = state == DeviceState.ON || state == DeviceState.OFF

    /**
     * The word on the control card.
     *
     * `DISCONNECTED` reads `OFFLINE`: the badge and the data layer keep the contract's
     * spelling, user-facing prose does not (section 9).
     */
    val stateLabel: String
        get() = when (state) {
            DeviceState.ON -> "ON"
            DeviceState.OFF -> "OFF"
            DeviceState.ERROR -> "ERROR"
            DeviceState.DISCONNECTED -> "OFFLINE"
        }

    /** `Kitchen Outlet, on. Double tap to turn off.` — section 10. */
    val spokenControl: String
        get() = buildString {
            append("$deviceName, ${state.spoken}.")
            if (canSwitch) {
                append(" Double tap to turn ${if (state == DeviceState.ON) "off" else "on"}.")
            }
        }
}

/**
 * One device's day, as the usage section shows it.
 *
 * @param hourFractions exactly 24 values in 0..1 — the share of each hour the device spent
 *   on. Hours still to come are zero, which is what makes the bar read as a clock rather
 *   than as a chart that has run out of data.
 * @param periodCount how many separate runs touched today. This is the `Switches` figure,
 *   and it counts the same things the bar draws: a run that began before midnight is one
 *   period showing on today's timeline, so it is one period in the count. Counting only
 *   runs that *started* today would leave a bar with a visible segment above the number 0.
 */
data class DayUsage(
    val onSeconds: Long,
    val periodCount: Int,
    val hourFractions: List<Float>,
) {
    /** `4h 12m`, or `12m` for anything under the hour. */
    val timeOnLabel: String get() = formatOnDuration(onSeconds)

    /** `On for 4 hours 12 minutes today, across 6 periods.` — the timeline's text alternative. */
    val spokenSummary: String
        get() {
            val hours = onSeconds / 3600
            val minutes = (onSeconds % 3600) / 60
            val duration = when {
                hours > 0 && minutes > 0 -> "$hours ${plural(hours, "hour")} $minutes ${plural(minutes, "minute")}"
                hours > 0 -> "$hours ${plural(hours, "hour")}"
                else -> "$minutes ${plural(minutes, "minute")}"
            }
            return "On for $duration today, across $periodCount ${plural(periodCount.toLong(), "period")}."
        }

    private fun plural(count: Long, noun: String) = if (count == 1L) noun else "${noun}s"
}

/**
 * `4h 12m`.
 *
 * Not [com.smarthome.control.ui.components.formatDuration], which renders `4:12:00` — that
 * is the shape a countdown wants, because a countdown is read like a clock. A total for the
 * day is read like a quantity, and `4:12` invites being read as twelve minutes past four.
 */
internal fun formatOnDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Turns one device's live documents into the sheet.
 *
 * Pure, and separate from the ViewModel for the same reason as the floor dashboard's: the
 * arithmetic that decides what the examiner reads off the card is all here, and a function
 * over a device and a list of events can be tested without a listener anywhere.
 */
internal fun buildOutletSheetState(
    device: Live<Device>?,
    floor: Floor?,
    events: List<UsageEvent>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): OutletSheetUiState {
    if (device == null) {
        return OutletSheetUiState(isLoading = false, isMissing = true, nowMillis = nowMillis)
    }

    val value = device.value
    return OutletSheetUiState(
        isLoading = false,
        deviceName = value.name,
        locationLine = locationLine(value, floor),
        state = value.status,
        pendingWrite = !device.isFromServer,
        lastChangedMillis = value.lastChangedAt?.toDate()?.time,
        usage = buildDayUsage(events, startOfDayMillis(nowMillis, zone), nowMillis),
        nowMillis = nowMillis,
    )
}

/**
 * `Ground Floor · R2 C5`, or the coordinates alone while the floor document is still on
 * its way.
 *
 * Rows and columns are shown 1-based although they are stored 0-based. The grid is a
 * physical thing the user counts across a floor plan, and nobody counts from zero out loud.
 */
private fun locationLine(device: Device, floor: Floor?): String {
    val cell = "R${device.gridY + 1} C${device.gridX + 1}"
    return floor?.name?.takeIf { it.isNotBlank() }?.let { "$it · $cell" } ?: cell
}

private fun startOfDayMillis(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone)
        .toInstant().toEpochMilli()

/**
 * The day's usage, clipped to the day.
 *
 * Every figure here is computed from the *overlap* between a period and the window that
 * runs from midnight to now, never from `duration_seconds` alone:
 *
 * - An open period has no duration yet, and its elapsed time is the value that has to keep
 *   climbing while the sheet is open.
 * - A period that started before midnight would otherwise contribute last night's hours to
 *   today's total — an iron switched on at 11pm would read as nine hours on at 8am.
 *
 * The denormalised `duration_seconds` still earns its place in Reports, where the window is
 * whole days and the clipping never bites.
 *
 * Returns null when nothing ran today.
 */
internal fun buildDayUsage(
    events: List<UsageEvent>,
    dayStartMillis: Long,
    nowMillis: Long,
): DayUsage? {
    val windowEnd = maxOf(nowMillis, dayStartMillis)

    val periods = events.mapNotNull { event ->
        val start = event.startedAt?.toDate()?.time ?: return@mapNotNull null
        val end = event.endedAt?.toDate()?.time ?: nowMillis
        val from = maxOf(start, dayStartMillis)
        val to = minOf(maxOf(end, start), windowEnd)
        if (to > from) from to to else null
    }

    if (periods.isEmpty()) return null

    val onMillis = periods.sumOf { (from, to) -> to - from }
    val fractions = (0 until HoursInDay).map { hour ->
        val hourStart = dayStartMillis + hour * MillisPerHour
        val hourEnd = hourStart + MillisPerHour
        val covered = periods.sumOf { (from, to) ->
            (minOf(to, hourEnd) - maxOf(from, hourStart)).coerceAtLeast(0L)
        }
        (covered.toFloat() / MillisPerHour).coerceIn(0f, 1f)
    }

    return DayUsage(
        onSeconds = TimeUnit.MILLISECONDS.toSeconds(onMillis),
        periodCount = periods.size,
        hourFractions = fractions,
    )
}

internal const val HoursInDay = 24
private const val MillisPerHour = 60L * 60L * 1000L
