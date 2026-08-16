package com.smarthome.control.ui.device

import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.ui.model.DeviceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Everything the hazard device sheet draws.
 *
 * This is the screen where the brief's safety requirement becomes something a person can
 * see, so the derivation is deliberately conservative: every figure comes from
 * `turned_on_at` and `config.max_on_duration`, which are the same two fields the safety
 * worker reads. If the ring and the worker ever disagree, the ring is lying about
 * something that matters.
 */
data class HazardSheetUiState(
    val isLoading: Boolean = true,
    val deviceName: String = "",
    val locationLine: String = "",
    val state: DeviceState = DeviceState.OFF,
    /** Null means no limit has been set, which is the one state that blocks switching on. */
    val maxOnSeconds: Long? = null,
    val turnedOnAtMillis: Long? = null,
    val lastChangedMillis: Long? = null,
    val usage: DayUsage? = null,
    /** Today's `MAX_DURATION_EXCEEDED` alerts for this device. */
    val autoCutoffsToday: Int = 0,
    /** The hours those cutoffs landed in, for the timeline's ticks. */
    val cutoffHours: Set<Int> = emptySet(),
    /** The most recent cutoff, shown as a Critical line until the user acts. */
    val lastCutoffMillis: Long? = null,
    val pendingWrite: Boolean = false,
    val actionError: String? = null,
    val loadError: String? = null,
    val isMissing: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isOn: Boolean get() = state == DeviceState.ON
    val isReachable: Boolean get() = state != DeviceState.DISCONNECTED && state != DeviceState.ERROR

    /**
     * Whether the ring has anything to draw.
     *
     * A device that is on with no `turned_on_at` is a contract violation upstream. The
     * sheet shows no ring rather than an invented one — the same call the dashboard's
     * hazard chips make.
     */
    val isCounting: Boolean get() = isOn && turnedOnAtMillis != null && maxOnSeconds != null

    val elapsedSeconds: Long?
        get() = turnedOnAtMillis?.let {
            TimeUnit.MILLISECONDS.toSeconds((nowMillis - it).coerceAtLeast(0L))
        }

    val remainingSeconds: Long?
        get() {
            val limit = maxOnSeconds ?: return null
            val elapsed = elapsedSeconds ?: return null
            return (limit - elapsed).coerceAtLeast(0L)
        }

    /**
     * The countdown has run out but the device is still `ON`.
     *
     * The worker ticks once a minute, so the real cutoff lands up to sixty seconds after
     * zero. Showing `00:00` against a device that is still running reads as a feature that
     * failed, which is why this state exists and says `Switching off…` instead.
     */
    val isExpired: Boolean get() = isCounting && remainingSeconds == 0L

    val inFinalTenth: Boolean
        get() {
            val limit = maxOnSeconds ?: return false
            val remaining = remainingSeconds ?: return false
            return isCounting && remaining > 0 && remaining <= limit / 10
        }

    /** Section 3: without a limit there is nothing for the worker to enforce, so no switching on. */
    val canSwitchOn: Boolean get() = maxOnSeconds != null

    /** `Switches off automatically after 30 minutes.` — or the line that asks for a limit. */
    val helperLine: String
        get() = maxOnSeconds
            ?.let { "Switches off automatically after ${spellLimit(it)}." }
            ?: "Set a maximum on time before using this device."

    /** `of 30 minutes`, under the ring. */
    val limitCaption: String? get() = maxOnSeconds?.let { "of ${spellLimit(it)}" }

    /** `Started 17:12 · Cuts off 17:42`. */
    val runFooter: String?
        get() {
            val started = turnedOnAtMillis ?: return null
            val limit = maxOnSeconds ?: return null
            val cutsOff = started + TimeUnit.SECONDS.toMillis(limit)
            return "Started ${clockTime(started, zone)} · Cuts off ${clockTime(cutsOff, zone)}"
        }

    /** `12 minutes 47 seconds remaining of 30 minutes` — section 11. */
    val ringSpoken: String
        get() {
            val remaining = remainingSeconds ?: return ""
            val limit = maxOnSeconds ?: return ""
            if (remaining == 0L) return "Maximum on time reached. Switching off."
            return "${spellRemaining(remaining)} remaining of ${spellLimit(limit)}"
        }

    /** `Bedroom Iron switches off in under 3 minutes` — announced assertively. */
    val finalTenthAnnouncement: String?
        get() {
            if (!inFinalTenth) return null
            val remaining = remainingSeconds ?: return null
            val minutes = (remaining / 60) + 1
            return "$deviceName switches off in under $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }

    /** The Critical line, which persists until the device is used again or the sheet closes. */
    val cutoffNotice: String?
        get() = lastCutoffMillis?.takeIf { !isOn }
            ?.let { "Switched off automatically — maximum on time reached" }
}

/**
 * `12 minutes 47 seconds` — what the ring reads out.
 *
 * Seconds are spoken only under the hour. `1 hour 5 minutes 12 seconds` is a mouthful that
 * has changed by the time it finishes, and at that range nobody is counting seconds; under
 * an hour they are exactly what the user is watching.
 */
internal fun spellRemaining(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    if (seconds >= 3600) return spellLimit(seconds)

    val minutes = seconds / 60
    val rest = seconds % 60
    val minuteWord = if (minutes == 1L) "minute" else "minutes"
    val secondWord = if (rest == 1L) "second" else "seconds"
    return when {
        minutes > 0 && rest > 0 -> "$minutes $minuteWord $rest $secondWord"
        minutes > 0 -> "$minutes $minuteWord"
        else -> "$rest $secondWord"
    }
}

/**
 * `30 minutes`, `1 hour`, `1 hour 30 minutes`.
 *
 * Screen prompt 07 section 10 is firm that user-facing copy says `maximum on time`, never
 * `max_on_duration`; the same instinct applies to the value. `1800s` is a database reading,
 * not something a person says about their iron.
 */
internal fun spellLimit(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val hourWord = if (hours == 1L) "hour" else "hours"
    val minuteWord = if (minutes == 1L) "minute" else "minutes"
    return when {
        hours > 0 && minutes > 0 -> "$hours $hourWord $minutes $minuteWord"
        hours > 0 -> "$hours $hourWord"
        minutes > 0 -> "$minutes $minuteWord"
        else -> "$seconds ${if (seconds == 1L) "second" else "seconds"}"
    }
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun clockTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(ClockFormat)

/**
 * The presets on the duration picker, in the order section 3 lists them.
 *
 * Four presets and a custom escape hatch. Enough that the common cases are one tap, few
 * enough that the row does not wrap on a narrow phone.
 */
val DurationPresets: List<Long> = listOf(5 * 60L, 15 * 60L, 30 * 60L, 60 * 60L)

/** Section 3: one minute to four hours. Beyond four hours the cutoff stops being a safeguard. */
val CustomLimitRange: LongRange = 60L..(4 * 60 * 60L)

fun presetLabel(seconds: Long): String =
    if (seconds >= 3600) "${seconds / 3600}h" else "${seconds / 60}m"

/**
 * Whether a proposed limit would cut the device off almost immediately.
 *
 * Section 6: shortening the limit below what has already elapsed is legitimate — the user
 * may have realised the iron has been on far too long — but it must be confirmed, because
 * silently switching off a running appliance on a stray tap is a genuinely bad outcome.
 */
fun wouldCutOffImmediately(newLimitSeconds: Long, elapsedSeconds: Long?): Boolean =
    elapsedSeconds != null && newLimitSeconds <= elapsedSeconds

/** The confirmation's copy, which has to name both numbers to be worth reading. */
fun cutOffWarning(newLimitSeconds: Long, elapsedSeconds: Long): String =
    "The device has already been on for ${spellLimit(elapsedSeconds)}. " +
        "Setting a ${spellLimit(newLimitSeconds)} limit will switch it off within a minute."

/**
 * Turns one appliance's live documents into the sheet.
 *
 * @param alerts this device's alerts. Only `MAX_DURATION_EXCEEDED` ones count towards the
 *   cutoff figures — a device that reported a fault has a different story to tell, and the
 *   `Auto cutoffs` card would be lying if it counted both.
 */
internal fun buildHazardSheetState(
    device: Live<Device>?,
    floor: Floor?,
    alerts: List<Alert>,
    events: List<UsageEvent>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): HazardSheetUiState {
    if (device == null) {
        return HazardSheetUiState(isLoading = false, isMissing = true, nowMillis = nowMillis)
    }

    val value = device.value
    val dayStart = startOfDayMillis(nowMillis, zone)

    val cutoffs = alerts
        .filter { it.deviceId == value.id && it.type == AlertType.MAX_DURATION_EXCEEDED }
        .mapNotNull { it.createdAt?.toDate()?.time }
        .filter { it >= dayStart && it <= nowMillis }

    return HazardSheetUiState(
        isLoading = false,
        deviceName = value.name,
        locationLine = hazardLocationLine(value, floor),
        state = value.status,
        maxOnSeconds = value.applianceConfig?.maxOnDurationSeconds?.toLong(),
        turnedOnAtMillis = value.turnedOnAt?.toDate()?.time,
        lastChangedMillis = value.lastChangedAt?.toDate()?.time,
        usage = buildDayUsage(events, dayStart, nowMillis),
        autoCutoffsToday = cutoffs.size,
        cutoffHours = cutoffs.map { hourOfDay(it, dayStart) }.toSet(),
        lastCutoffMillis = cutoffs.maxOrNull(),
        pendingWrite = !device.isFromServer,
        nowMillis = nowMillis,
        zone = zone,
    )
}

private fun hazardLocationLine(device: Device, floor: Floor?): String {
    val cell = "R${device.gridY + 1} C${device.gridX + 1}"
    return floor?.name?.takeIf { it.isNotBlank() }?.let { "$it · $cell" } ?: cell
}

private fun hourOfDay(millis: Long, dayStartMillis: Long): Int =
    (((millis - dayStartMillis) / (60L * 60L * 1000L)).toInt()).coerceIn(0, HoursInDay - 1)

private fun startOfDayMillis(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone)
        .toInstant().toEpochMilli()
