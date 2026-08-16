package com.smarthome.control.ui.device

import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The derivations every device sheet needs.
 *
 * These arrived one sheet at a time as private copies — by the fourth sheet there were
 * three identical `startOfDay` functions and two `locationLine`s differing only in what
 * they appended. Four copies of a day boundary is four chances for one screen to disagree
 * with another about when today started, which is exactly the class of bug nobody finds by
 * looking at a screenshot.
 */

/**
 * `Ground Floor · R2 C5`, plus whatever the sheet appends.
 *
 * Rows and columns are shown 1-based although they are stored 0-based. The grid is a
 * physical thing the user counts across a floor plan, and nobody counts from zero out loud.
 *
 * @param suffix the multi-switch sheet's `3 gang`, and nothing for anyone else.
 */
internal fun deviceLocationLine(device: Device, floor: Floor?, suffix: String? = null): String {
    val cell = "R${device.gridY + 1} C${device.gridX + 1}"
    val place = floor?.name?.takeIf { it.isNotBlank() }?.let { "$it · $cell" } ?: cell
    return if (suffix.isNullOrBlank()) place else "$place · $suffix"
}

/**
 * Midnight, in the zone that matters.
 *
 * Always the home's timezone where the caller has it, never the phone's: "today" has to
 * mean the same day the schedules and the safety worker mean.
 */
internal fun startOfDay(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone)
        .toInstant().toEpochMilli()

/** `17:12` — 24-hour, matching the `"HH:mm"` the contract stores and the simulator shows. */
internal fun clockTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(ClockFormat)

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
