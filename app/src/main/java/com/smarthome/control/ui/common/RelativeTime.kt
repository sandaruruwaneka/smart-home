package com.smarthome.control.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How the app says "when".
 *
 * Alerts and safety events are read in two different frames of mind. Something that
 * happened four minutes ago is being read as *is this still happening*, and the answer has
 * to be a duration. Something that happened at half six is being read as a record, and the
 * answer has to be a clock time — "eleven hours ago" makes the reader do arithmetic to
 * recover the fact they were actually after.
 *
 * So the scale switches at the hour rather than counting hours, days and weeks upward:
 *
 * | Age | Rendering |
 * |---|---|
 * | under a minute | `Just now` |
 * | under an hour | `4 min ago` |
 * | earlier today | `6:30 PM` |
 * | yesterday | `Yesterday` |
 * | older | `12 Aug` |
 *
 * @param zone the zone the day boundary is drawn in. The home's timezone lives on the
 *   user document, so screens that have it should pass it — "yesterday" has to mean the
 *   same yesterday the user's schedules mean.
 */
fun relativeTime(
    epochMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    // A timestamp in the future is a clock skew between the phone and the server, not a
    // scheduled event -- there is nothing useful to say about it beyond "now".
    val ageMillis = (nowMillis - epochMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)

    if (minutes < 1) return "Just now"
    if (minutes < 60) return "$minutes min ago"

    val then = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val thenDate = then.toLocalDate()

    return when {
        thenDate == today -> then.format(TimeFormat)
        thenDate == today.minusDays(1) -> "Yesterday"
        else -> then.format(DateFormat)
    }
}

private val TimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private val DateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * A clock that advances while the screen is on it.
 *
 * Relative timestamps are the one piece of a live screen that goes stale without anything
 * changing: no Firestore snapshot arrives to tell the app that `4 min ago` is now
 * `5 min ago`. This is the tick that keeps them honest.
 *
 * [intervalMillis] defaults to half a minute rather than a second because the shortest
 * unit this scale renders is the minute — a faster tick would recompose the screen
 * repeatedly to produce identical text.
 */
@Composable
fun rememberNowMillis(intervalMillis: Long = 30_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis(), intervalMillis) {
        while (true) {
            delay(intervalMillis)
            value = System.currentTimeMillis()
        }
    }
