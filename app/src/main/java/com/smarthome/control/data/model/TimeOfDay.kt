package com.smarthome.control.data.model

/**
 * A wall-clock time of day, stored as `"HH:mm"` in 24-hour form (SCHEMA.md section 1).
 *
 * Held as minutes since midnight rather than as the string, so that comparisons are
 * arithmetic instead of lexicographic and so an invalid value cannot be constructed at
 * all. The string form is produced only when writing to Firestore.
 *
 * There is no date and no timezone here on purpose: `schedule_on` and `schedule_off` are
 * local to the home, and the timezone that interprets them lives on the user document
 * (section 9).
 */
@JvmInline
value class TimeOfDay private constructor(val minutesSinceMidnight: Int) : Comparable<TimeOfDay> {

    val hour: Int get() = minutesSinceMidnight / 60
    val minute: Int get() = minutesSinceMidnight % 60

    /** The `"HH:mm"` form written to Firestore. Always zero-padded to five characters. */
    val wireValue: String get() = "%02d:%02d".format(hour, minute)

    override fun compareTo(other: TimeOfDay): Int =
        minutesSinceMidnight.compareTo(other.minutesSinceMidnight)

    override fun toString(): String = wireValue

    companion object {
        private val PATTERN = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

        fun of(hour: Int, minute: Int): TimeOfDay {
            require(hour in 0..23) { "hour must be 0..23, was $hour" }
            require(minute in 0..59) { "minute must be 0..59, was $minute" }
            return TimeOfDay(hour * 60 + minute)
        }

        /**
         * Parses the wire form, returning null for anything that is not exactly `"HH:mm"`.
         *
         * Null rather than an exception because this parses data written by the simulator
         * and the worker, and one malformed schedule should degrade to "no schedule"
         * rather than crash the device sheet.
         */
        fun parseOrNull(value: String?): TimeOfDay? {
            val match = PATTERN.matchEntire(value ?: return null) ?: return null
            return TimeOfDay(match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt())
        }
    }
}

/**
 * Whether [now] falls inside the window that starts at this time and ends at [end].
 *
 * A window whose end is earlier than its start crosses midnight and is valid (section 5),
 * so `22:00 -> 06:00` is a single window covering the small hours, not an empty one. The
 * start is inclusive and the end exclusive, which makes a window that starts and ends at
 * the same minute empty rather than eternal.
 *
 * The app uses this only to describe a schedule to the user; the worker owns actually
 * flipping the light (section 10.2).
 */
fun TimeOfDay.windowContains(end: TimeOfDay, now: TimeOfDay): Boolean = when {
    this == end -> false
    this < end -> now >= this && now < end
    else -> now >= this || now < end
}
