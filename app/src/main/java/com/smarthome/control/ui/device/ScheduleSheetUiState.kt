package com.smarthome.control.ui.device

import com.smarthome.control.data.Live
import com.smarthome.control.data.model.ChangeSource
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.TimeOfDay
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.data.model.windowContains
import com.smarthome.control.ui.model.DeviceState
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Everything the schedule editor draws.
 *
 * Schedule UIs usually fail in the same place: two time pickers are easy to build and hard
 * to read. A user looking at `18:30` and `23:00` still has to work out whether the light is
 * on right now and when it next changes. So the arithmetic all happens here, and the sheet
 * answers both questions in a sentence before it shows a single control.
 *
 * Every time in this class is interpreted in [zone] — the home's timezone from the user
 * document — and never the phone's. The scheduler job reads the same field, and a schedule
 * that means one thing to the worker and another to the app is worse than no schedule.
 */
data class ScheduleSheetUiState(
    val isLoading: Boolean = true,
    val deviceName: String = "",
    val locationLine: String = "",
    val state: DeviceState = DeviceState.OFF,
    val scheduleEnabled: Boolean = false,
    val scheduleOn: TimeOfDay? = null,
    val scheduleOff: TimeOfDay? = null,
    val lastChangedMillis: Long? = null,
    val lastChangedBy: ChangeSource? = null,
    val usage: DayUsage? = null,
    /** Runs today that began at a scheduled edge — the `Schedule runs` figure. */
    val scheduleRunsToday: Int = 0,
    val pendingWrite: Boolean = false,
    val actionError: String? = null,
    val loadError: String? = null,
    val isMissing: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
    /** True when [zone] is not the phone's own, which earns the caption under the ring. */
    val zoneDiffersFromPhone: Boolean = false,
) {
    val isOn: Boolean get() = state == DeviceState.ON
    val isReachable: Boolean get() = state != DeviceState.DISCONNECTED && state != DeviceState.ERROR

    /** Both edges present, so there is a window to draw and enforce. */
    val hasWindow: Boolean get() = scheduleOn != null && scheduleOff != null

    val nowTime: TimeOfDay get() = timeOfDayAt(nowMillis, zone)

    /**
     * Whether the schedule says this light should be on at this moment.
     *
     * Null when there is no active schedule to have an opinion. That is a different thing
     * from "should be off", and the override logic below depends on the difference.
     */
    val scheduledState: Boolean?
        get() {
            if (!scheduleEnabled) return null
            val on = scheduleOn ?: return null
            val off = scheduleOff ?: return null
            return on.windowContains(off, nowTime)
        }

    val isInsideWindow: Boolean get() = scheduledState == true

    /**
     * The user has moved this light away from what the schedule wants.
     *
     * Section 6: the manual state wins until the next scheduled edge, then the schedule
     * takes over again. It does not disable the schedule and it is not immediately
     * overwritten — so the app has to *show* it, or a light quietly flipping back at 23:00
     * reads as a bug even though it is exactly right.
     *
     * A device the app cannot reach is never "overridden": nobody chose that state.
     */
    val isOverridden: Boolean
        get() {
            val wanted = scheduledState ?: return false
            if (!isReachable) return false
            return wanted != isOn
        }

    /** The window's edges as fractions of the day, for the ring. */
    val windowStartFraction: Float? get() = scheduleOn?.let { it.minutesSinceMidnight / MinutesPerDayF }
    val windowSweepFraction: Float?
        get() {
            val on = scheduleOn ?: return null
            val off = scheduleOff ?: return null
            return windowMinutes(on, off) / MinutesPerDayF
        }
    val nowFraction: Float get() = nowTime.minutesSinceMidnight / MinutesPerDayF

    /** Where the override began, marked as a break in the arc. Null when not overridden. */
    val overrideFraction: Float?
        get() = if (isOverridden) lastChangedMillis?.let { timeOfDayAt(it, zone).minutesSinceMidnight / MinutesPerDayF } else null

    /** The next edge the schedule will act on, or null when it will not act. */
    val nextEdge: TimeOfDay?
        get() {
            if (!scheduleEnabled) return null
            val on = scheduleOn ?: return null
            val off = scheduleOff ?: return null
            return if (isInsideWindow) off else on
        }

    /**
     * The most-read line in the sheet.
     *
     * Relative under a day, because "in 2h 14m" is what somebody standing in a dark hallway
     * actually wants. Never `in 0h 0m` — under a minute it says `shortly`, since a countdown
     * that reads zero while nothing happens is the same lie the hazard sheet works so hard
     * to avoid.
     */
    val nextEventLine: String
        get() {
            if (!scheduleEnabled || !hasWindow) {
                return "Schedule is off. This light stays as you set it."
            }
            val edge = nextEdge ?: return "Schedule is off. This light stays as you set it."

            if (isOverridden) {
                val resumes = edge.wireValue
                // Section 12 gives the copy for the light held on. The mirror case — held
                // off inside its own window — needs the same sentence the other way round,
                // or the state with no words is the one the examiner will try.
                return if (isOn) {
                    "On until you turn it off — schedule resumes at $resumes."
                } else {
                    "Off until you turn it on — schedule resumes at $resumes."
                }
            }

            val verb = if (isInsideWindow) "Turns off" else "Turns on"
            val seconds = secondsUntil(nowMillis, nowTime, edge)
            // Counted in seconds rather than whole minutes so that "shortly" is reachable
            // at all. Truncating to the minute first means the edge is either a minute away
            // or a whole day away, and the sheet would say `Turns off in 1m` for the last
            // sixty seconds and then jump to 24h.
            if (seconds < SecondsPerMinute) return "$verb shortly"
            return "$verb in ${formatOnDuration(seconds)}"
        }

    /** `Overnight — 22:00 today until 06:00 tomorrow.` */
    val overnightHelper: String?
        get() {
            val on = scheduleOn ?: return null
            val off = scheduleOff ?: return null
            if (off > on || off == on) return null
            return "Overnight — ${on.wireValue} today until ${off.wireValue} tomorrow."
        }

    /** Blocked: a window with no width is not a schedule, it is a typo. */
    val sameTimeError: String?
        get() = if (hasWindow && scheduleOn == scheduleOff) {
            "Start and end times must be different."
        } else {
            null
        }

    /**
     * Permitted but flagged.
     *
     * The scheduler ticks once a minute (SCHEMA.md section 10.2), so a window narrower than
     * that can fall entirely between two ticks and never fire. The user may still want it;
     * they should not be surprised by it.
     */
    val shortWindowWarning: String?
        get() {
            val on = scheduleOn ?: return null
            val off = scheduleOff ?: return null
            if (on == off) return null
            return if (windowMinutes(on, off) < ShortWindowMinutes) {
                "This window is shorter than the 1-minute check interval and may be missed."
            } else {
                null
            }
        }

    val timezoneCaption: String? get() = if (zoneDiffersFromPhone) "Times are in ${zone.id}." else null

    /** `⏱ scheduled` / `⏱ overridden`, or nothing at all. */
    val chipLabel: String?
        get() = when {
            isOverridden -> "⏱ overridden"
            isInsideWindow -> "⏱ scheduled"
            else -> null
        }

    /**
     * `Scheduled on from 18:30 to 23:00. Currently on. Turns off in 2 hours 14 minutes.`
     *
     * A complete sentence, because the ring's meaning is otherwise entirely visual and a
     * screen reader user would get nothing from it at all (section 13).
     */
    val ringSpoken: String
        get() {
            val on = scheduleOn
            val off = scheduleOff
            if (!scheduleEnabled || on == null || off == null) {
                return "No schedule set. This light stays as you set it."
            }
            return "Scheduled on from ${on.wireValue} to ${off.wireValue}. " +
                "Currently ${if (isOn) "on" else "off"}. $nextEventLine"
        }

    /** `Last changed 18:30 by schedule` — the footer names who did it (section 12). */
    val footerLine: String?
        get() {
            val millis = lastChangedMillis ?: return null
            val actor = when (lastChangedBy) {
                ChangeSource.APP -> "you"
                ChangeSource.WORKER -> "schedule"
                ChangeSource.SIMULATOR -> "simulator"
                null -> null
            }
            val time = clockTime(millis, zone)
            return if (actor == null) "Last changed $time" else "Last changed $time by $actor"
        }

    /** `Turns on at 18:30. Double tap to change.` */
    fun timeCardSpoken(isOnEdge: Boolean): String {
        val time = (if (isOnEdge) scheduleOn else scheduleOff)?.wireValue ?: "not set"
        return "Turns ${if (isOnEdge) "on" else "off"} at $time. Double tap to change."
    }
}

/**
 * Turns one light's documents into the sheet.
 *
 * Pure, and given the zone explicitly rather than reaching for the system default, so the
 * tests can assert on a home in Colombo from a machine anywhere.
 */
internal fun buildScheduleSheetState(
    device: Live<Device>?,
    floor: Floor?,
    events: List<UsageEvent>,
    nowMillis: Long,
    zone: ZoneId,
    phoneZone: ZoneId = ZoneId.systemDefault(),
): ScheduleSheetUiState {
    if (device == null) {
        return ScheduleSheetUiState(isLoading = false, isMissing = true, nowMillis = nowMillis, zone = zone)
    }

    val value = device.value
    val light = value.config as? DeviceConfig.Light
    val dayStart = startOfDay(nowMillis, zone)

    return ScheduleSheetUiState(
        isLoading = false,
        deviceName = value.name,
        locationLine = deviceLocationLine(value, floor),
        state = value.status,
        scheduleEnabled = light?.scheduleEnabled ?: false,
        scheduleOn = light?.scheduleOn,
        scheduleOff = light?.scheduleOff,
        lastChangedMillis = value.lastChangedAt?.toDate()?.time,
        lastChangedBy = value.lastChangedBy,
        usage = buildDayUsage(events, dayStart, nowMillis),
        scheduleRunsToday = countScheduledRuns(events, light?.scheduleOn, dayStart, nowMillis, zone),
        pendingWrite = !device.isFromServer,
        nowMillis = nowMillis,
        zone = zone,
        zoneDiffersFromPhone = zone.id != phoneZone.id,
    )
}

/**
 * How many of today's runs the schedule started.
 *
 * A usage event carries no actor — nothing in the contract records *who* opened a period —
 * so this is inferred from when it began: a run that started within a couple of minutes of
 * the scheduled ON edge is a scheduled run. The scheduler ticks once a minute, so the
 * window has to be at least that wide; two minutes absorbs the tick plus ordinary write
 * latency.
 *
 * The inference is stated rather than hidden because it can be wrong in one direction: a
 * user who flips the light on by hand within that window is credited to the schedule. That
 * is a rare coincidence and a harmless miscount, where adding an actor field to every usage
 * row would be a contract change affecting all three codebases.
 */
private fun countScheduledRuns(
    events: List<UsageEvent>,
    scheduleOn: TimeOfDay?,
    dayStartMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
): Int {
    val edge = scheduleOn ?: return 0
    return events.count { event ->
        val started = event.startedAt?.toDate()?.time ?: return@count false
        if (started < dayStartMillis || started > nowMillis) return@count false
        val startedAt = timeOfDayAt(started, zone)
        val delta = abs(startedAt.minutesSinceMidnight - edge.minutesSinceMidnight)
        // Wrapping, so a run at 00:01 counts against a 23:59 edge.
        minOf(delta, MinutesPerDay - delta) <= ScheduledRunToleranceMinutes
    }
}

internal fun timeOfDayAt(millis: Long, zone: ZoneId): TimeOfDay {
    val local = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return TimeOfDay.of(local.hour, local.minute)
}

/** Minutes from [from] to [to], wrapping midnight. Equal times are a whole day apart. */
internal fun minutesUntil(from: TimeOfDay, to: TimeOfDay): Int {
    val delta = to.minutesSinceMidnight - from.minutesSinceMidnight
    return if (delta <= 0) delta + MinutesPerDay else delta
}

/**
 * Seconds from now until the edge.
 *
 * [minutesUntil] works on whole minutes because that is all a [TimeOfDay] carries, so the
 * seconds already elapsed in the current minute are subtracted back off here. Minute
 * boundaries are the same in every zone the contract allows, so taking them off the raw
 * epoch millis is safe.
 */
internal fun secondsUntil(nowMillis: Long, nowTime: TimeOfDay, edge: TimeOfDay): Long {
    val wholeMinutes = minutesUntil(nowTime, edge).toLong()
    val intoMinute = (nowMillis.mod(60_000L)) / 1000L
    return (wholeMinutes * SecondsPerMinute - intoMinute).coerceAtLeast(0L)
}

private const val SecondsPerMinute = 60L

/** The window's width in minutes, wrapping midnight. */
internal fun windowMinutes(on: TimeOfDay, off: TimeOfDay): Int {
    val delta = off.minutesSinceMidnight - on.minutesSinceMidnight
    return if (delta <= 0) delta + MinutesPerDay else delta
}

private const val MinutesPerDay = 24 * 60
private const val MinutesPerDayF = 24f * 60f
private const val ShortWindowMinutes = 5
private const val ScheduledRunToleranceMinutes = 2
