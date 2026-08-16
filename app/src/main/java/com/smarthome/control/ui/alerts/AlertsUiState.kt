package com.smarthome.control.ui.alerts

import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Floor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Everything the alerts screen draws.
 *
 * This screen is the app's evidence that the server-side cutoff works — it is where the
 * worker's output accumulates visibly, and the thing an examiner opens after watching an
 * iron switch itself off. So the derivation is arranged around one idea: an unacknowledged
 * event gets prominence, and everything older is a plain record. Two jobs pulling opposite
 * ways, and the screen has to serve the urgent one without making the leisurely one feel
 * like an emergency.
 */
data class AlertsUiState(
    val isLoading: Boolean = true,
    val sections: List<AlertSection> = emptyList(),
    val banner: AlertsBanner? = null,
    val filter: AlertFilter = AlertFilter.All,
    val unacknowledgedCount: Int = 0,
    /** True when the account has never had an alert, as opposed to none matching the filter. */
    val hasAnyAlerts: Boolean = false,
    val loadError: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean get() = sections.isEmpty()

    /**
     * The two empty states are different facts and must read differently.
     *
     * A user has to be able to tell "nothing matched this filter" from "nothing has ever
     * happened" — the first means try another filter, the second means the safety system
     * has had nothing to report, which is good news rather than a broken screen.
     */
    val emptyMessage: String
        get() = when {
            hasAnyAlerts -> filter.emptyLine
            else -> "No alerts yet. Devices that switch off automatically will appear here."
        }

    val showsShowAllAction: Boolean get() = isEmpty && hasAnyAlerts
}

/** `All`, `Cutoffs`, `Faults` — the only three, because there are only two alert types. */
enum class AlertFilter(val label: String, val emptyLine: String) {
    All("All", "No alerts recorded."),
    Cutoffs("Cutoffs", "No cutoffs recorded."),
    Faults("Faults", "No faults recorded.");

    fun accepts(type: AlertType): Boolean = when (this) {
        All -> true
        Cutoffs -> type == AlertType.MAX_DURATION_EXCEEDED
        Faults -> type == AlertType.DEVICE_ERROR
    }
}

/** `TODAY`, `YESTERDAY`, `12 AUGUST` — a day's worth of alerts under its own header. */
data class AlertSection(val header: String, val rows: List<AlertRowUiState>)

data class AlertRowUiState(
    val alertId: String,
    val deviceId: String,
    val floorId: String,
    val deviceName: String,
    val message: String,
    val type: AlertType,
    /** `First Floor · 17:42`. */
    val locationLine: String,
    val acknowledged: Boolean,
    val createdAtMillis: Long,
    /**
     * Changes identity when this alert arrived while the screen was open, flashing the row
     * once — the same external-change convention the dashboard's markers use.
     */
    val arrivalToken: Long? = null,
) {
    /**
     * `Unacknowledged. Bedroom Iron, maximum on time reached, First Floor, 17:42. Double tap
     * to open device.`
     *
     * The word leads, because the dot is never allowed to be the only signal that something
     * is outstanding (section 11).
     */
    val spoken: String
        get() = buildString {
            if (!acknowledged) append("Unacknowledged. ")
            append("$deviceName, ${message.lowercase()}, ${locationLine.replace(" · ", ", ")}. ")
            append("Double tap to open device.")
        }
}

/**
 * The Critical banner, present only while something is outstanding.
 *
 * Shaped to `AlertBanner`'s three slots rather than to a title, so the component stays the
 * single place that decides how a Critical notice is laid out.
 */
data class AlertsBanner(
    val cause: String,
    val reason: String,
    val timestamp: String,
    val count: Int,
)

/**
 * Turns the alert collection into the screen.
 *
 * @param floors used only to name the floor on each row. Alerts denormalise `device_name`
 *   for exactly this reason but not the floor, so the one listener the app already holds is
 *   read here rather than fetching a floor per row.
 */
internal fun buildAlertsState(
    alerts: List<Alert>,
    floors: List<Floor>,
    filter: AlertFilter,
    arrivals: Map<String, Long>,
    nowMillis: Long,
    zone: ZoneId,
): AlertsUiState {
    val floorNames = floors.associate { it.id to it.name }
    val outstanding = alerts.count { !it.acknowledged }

    val visible = alerts.filter { filter.accepts(it.type) }

    // Grouped by the day each alert landed on, newest day first, and newest within each
    // day. The repository already orders by `created_at` descending, so this preserves that
    // rather than re-sorting and risking a different answer.
    val sections = visible
        .groupBy { dayKey(it.createdAtMillisOrNow(nowMillis), zone) }
        .map { (day, dayAlerts) ->
            AlertSection(
                header = sectionHeader(day, nowMillis, zone),
                rows = dayAlerts.map { alert ->
                    val millis = alert.createdAtMillisOrNow(nowMillis)
                    AlertRowUiState(
                        alertId = alert.id,
                        deviceId = alert.deviceId,
                        floorId = alert.floorId,
                        deviceName = alert.deviceName,
                        message = alert.message,
                        type = alert.type,
                        locationLine = locationLine(
                            floorName = floorNames[alert.floorId],
                            millis = millis,
                            nowMillis = nowMillis,
                            zone = zone,
                        ),
                        acknowledged = alert.acknowledged,
                        createdAtMillis = millis,
                        arrivalToken = arrivals[alert.id],
                    )
                },
            )
        }

    return AlertsUiState(
        isLoading = false,
        sections = sections,
        banner = bannerFor(alerts.filter { !it.acknowledged }, nowMillis, zone),
        filter = filter,
        unacknowledgedCount = outstanding,
        hasAnyAlerts = alerts.isNotEmpty(),
        nowMillis = nowMillis,
    )
}

/**
 * `Bedroom Iron switched off automatically` for one, a count for several.
 *
 * Naming the device is worth more than a number while there is only one thing wrong; past
 * that, the list below is the place to read the detail and the banner's job is only to say
 * how much of it there is.
 */
private fun bannerFor(
    unacknowledged: List<Alert>,
    nowMillis: Long,
    zone: ZoneId,
): AlertsBanner? {
    if (unacknowledged.isEmpty()) return null
    val newest = unacknowledged.first()
    val single = unacknowledged.size == 1

    return AlertsBanner(
        cause = if (single) {
            when (newest.type) {
                AlertType.MAX_DURATION_EXCEEDED -> "${newest.deviceName} switched off automatically"
                AlertType.DEVICE_ERROR -> "${newest.deviceName} reported a fault"
            }
        } else {
            "${unacknowledged.size} alerts need your attention"
        },
        // The worker's own words for one alert; for several, what they have in common.
        reason = if (single) {
            newest.message
        } else {
            "Devices were switched off automatically or reported a fault"
        },
        timestamp = locationLine(
            floorName = null,
            millis = newest.createdAtMillisOrNow(nowMillis),
            nowMillis = nowMillis,
            zone = zone,
        ),
        count = unacknowledged.size,
    )
}

/**
 * `First Floor · 17:42`, or `First Floor · 14 min ago` inside the hour.
 *
 * Section 4 wants relative under an hour and absolute beyond. The absolute half is 24-hour
 * to match every other clock time in the app and the simulator's own display; the relative
 * half uses the same `min ago` wording the rest of the app has used since the floor list,
 * rather than inventing a second phrasing here.
 */
private fun locationLine(floorName: String?, millis: Long, nowMillis: Long, zone: ZoneId): String {
    val age = (nowMillis - millis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(age)
    val time = when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        else -> Instant.ofEpochMilli(millis).atZone(zone).format(ClockFormat)
    }
    val place = floorName?.takeIf { it.isNotBlank() }
    return if (place == null) time else "$place · $time"
}

private fun Alert.createdAtMillisOrNow(nowMillis: Long): Long =
    createdAt?.toDate()?.time ?: nowMillis

/** The local date an alert belongs to, as an ordinal day number in [zone]. */
private fun dayKey(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

private fun sectionHeader(day: Long, nowMillis: Long, zone: ZoneId): String {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
    return when (day) {
        today -> "TODAY"
        today - 1 -> "YESTERDAY"
        else -> java.time.LocalDate.ofEpochDay(day).format(DateFormat).uppercase(Locale.ROOT)
    }
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/**
 * `12 AUGUST`.
 *
 * Explicitly English rather than the device locale, because every other word on this screen
 * is: a Sinhala month name between `TODAY` and `YESTERDAY` would be a half-translated
 * screen, which reads worse than a consistently untranslated one. `Locale.ROOT` is not the
 * answer either — it abbreviates `MMMM` to `AUG`.
 */
private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
