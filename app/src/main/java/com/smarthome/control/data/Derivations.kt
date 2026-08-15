package com.smarthome.control.data

import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.ui.model.DeviceState
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The values SCHEMA.md section 14 lists as computed, not stored.
 *
 * They live together in one file so the list stays checkable against the contract. Nothing
 * here writes: if any of these ever became a Firestore field, the simulator and the worker
 * would have to start maintaining it too, and the contract says they do not.
 *
 * Denormalised per-floor counters were considered and rejected -- they need a Cloud
 * Function trigger to stay accurate, and a single collection listener is both simpler and
 * correct at this scale.
 */

/** Per-floor device counts for the floor list's summary tiles. */
data class FloorSummary(
    val total: Int,
    val on: Int,
    val faulted: Int,
) {
    /** True when anything on this floor needs attention -- drives the card's alert tier. */
    val hasFault: Boolean get() = faulted > 0
}

/**
 * Groups one account-wide device list into per-floor counts.
 *
 * Takes the whole list rather than querying per floor because that is the point: the floor
 * list runs one listener over `devices` scoped to `owner_uid` and groups in memory, so
 * adding a floor costs no extra reads.
 *
 * `ERROR` and `DISCONNECTED` both count as faulted. They are different causes but the same
 * consequence for a summary tile: the app cannot vouch for that device's state.
 */
fun floorSummaries(devices: List<Device>): Map<String, FloorSummary> =
    devices.groupBy { it.floorId }.mapValues { (_, onFloor) ->
        FloorSummary(
            total = onFloor.size,
            on = onFloor.count { it.status == DeviceState.ON },
            faulted = onFloor.count {
                it.status == DeviceState.ERROR || it.status == DeviceState.DISCONNECTED
            },
        )
    }

/** The `2 of 3 on` line on a switch bank. */
fun channelsOnLabel(channels: List<Channel>): String =
    "${channels.count { it.status == DeviceState.ON }} of ${channels.size} on"

/**
 * How long a device has been on today, in seconds, in the home's timezone.
 *
 * Closed periods contribute their stored `duration_seconds`; a period that is still open
 * contributes `now - started_at`, which is what makes the figure tick upward live rather
 * than jumping only when the device is switched off.
 *
 * [zoneId] comes from the user document, not the phone. "Today" has to mean the same day
 * the user's schedules mean, or a light that came on at 23:50 lands in the wrong day's
 * total for anyone whose phone is roaming.
 *
 * A period that started before midnight and is still running is clipped at midnight, so
 * the figure answers "on today" rather than "on since it was switched on".
 */
fun timeOnTodaySeconds(
    events: List<UsageEvent>,
    zoneId: ZoneId,
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    val startOfDayMillis = Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    return events.sumOf { event ->
        val startedAt = event.startedAt?.toDate()?.time ?: return@sumOf 0L
        val endedAt = event.endedAt?.toDate()?.time ?: nowMillis
        val overlapStart = maxOf(startedAt, startOfDayMillis)
        val overlapEnd = minOf(endedAt, nowMillis)
        ((overlapEnd - overlapStart) / 1000L).coerceAtLeast(0L)
    }
}

/**
 * Seconds left before the safety worker cuts this appliance off, or null when no countdown
 * applies.
 *
 * Null for anything that is not a running appliance -- there is no countdown to show, and
 * a zero would read as "about to be cut off".
 *
 * Reaching zero does not mean the device is off. The worker ticks every 60 seconds, so the
 * actual cutoff lands up to a minute past the limit; the UI should show the countdown as
 * expired and wait for the status change rather than assuming it.
 */
fun countdownRemainingSeconds(
    device: Device,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val limit = device.applianceConfig?.maxOnDurationSeconds ?: return null
    if (device.status != DeviceState.ON) return null
    val turnedOnAt = device.turnedOnAt?.toDate()?.time ?: return null
    val elapsed = TimeUnit.MILLISECONDS.toSeconds(nowMillis - turnedOnAt)
    return (limit - elapsed).coerceAtLeast(0L)
}

/**
 * How far through its allowance a running appliance is, from 0f to 1f, for the countdown
 * ring. Null on the same terms as [countdownRemainingSeconds].
 */
fun countdownProgress(device: Device, nowMillis: Long = System.currentTimeMillis()): Float? {
    val limit = device.applianceConfig?.maxOnDurationSeconds ?: return null
    val remaining = countdownRemainingSeconds(device, nowMillis) ?: return null
    return (remaining.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}
