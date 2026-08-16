package com.smarthome.control.ui.device

import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.data.model.deriveMultiSwitchStatus
import com.smarthome.control.ui.model.DeviceState
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Everything the multi-switch control sheet draws.
 *
 * The sheet's whole argument is that one physical gang box is several independently
 * addressable switches, so almost everything here is per-channel and the unit-level figures
 * are derived from the channels rather than stored beside them.
 */
data class MultiSwitchSheetUiState(
    val isLoading: Boolean = true,
    val deviceName: String = "",
    /** `Ground Floor · R4 C3 · 3 gang`. */
    val locationLine: String = "",
    /**
     * The unit's own status, from the parent document.
     *
     * Only [DeviceState.DISCONNECTED] is ever read off it directly — that one cannot be
     * derived, because no channel can report that the whole box is unreachable. Everything
     * else follows the contract's derivation, which the data layer already owns.
     */
    val unitState: DeviceState = DeviceState.OFF,
    val channels: List<ChannelUiState> = emptyList(),
    val lastChangedMillis: Long? = null,
    val usage: MultiSwitchUsage? = null,
    val actionError: String? = null,
    val loadError: String? = null,
    val isMissing: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val channelCount: Int get() = channels.size
    val onCount: Int get() = channels.count { it.state == DeviceState.ON }
    val faultCount: Int get() = channels.count { it.state == DeviceState.ERROR }

    val isUnitReachable: Boolean get() = unitState != DeviceState.DISCONNECTED

    /**
     * `2 of 3 on`, and the two ends said properly.
     *
     * A bare `ON` would be technically true under the unit-level rule and actively
     * misleading in practice — one lit channel and three lit channels would read
     * identically. At the ends a count is the clumsier phrasing, so `0 of 3 on` becomes
     * `All off` and `3 of 3 on` becomes `All 3 on`.
     */
    val summaryLine: String
        get() = when {
            channelCount == 0 -> "No channels"
            onCount == 0 -> "All off"
            onCount == channelCount -> "All $channelCount on"
            else -> "$onCount of $channelCount on"
        }

    /** The `2` in `2 of 3 on`, which is drawn in `stateOn` while the rest stays neutral. */
    val summaryHighlight: String?
        get() = if (onCount in 1 until channelCount) onCount.toString() else null

    /** `Living Room Switch, 2 of 3 channels on` — section 11. */
    val spokenPrimary: String
        get() = "$deviceName, $onCount of $channelCount channels on"

    /**
     * Bulk actions only offer what they would actually change.
     *
     * A channel in `ERROR` is not switchable, so a unit whose only `OFF` channel is faulted
     * offers no `All on` — the button would write nothing and leave the user wondering
     * which of the two of them was wrong.
     */
    val canTurnAllOn: Boolean
        get() = isUnitReachable && channels.any { it.state == DeviceState.OFF }

    val canTurnAllOff: Boolean
        get() = isUnitReachable && channels.any { it.state == DeviceState.ON }

    /** `1 channel reported a fault.` — sits under the primary card, never on it. */
    val faultLine: String?
        get() = when (faultCount) {
            0 -> null
            1 -> "1 channel reported a fault."
            else -> "$faultCount channels reported a fault."
        }
}

/**
 * One row of the plate.
 *
 * @param runningSeconds how long *this run* has lasted, for the `ON · 2h 14m` caption.
 *   Deliberately not today's total: the caption says "on for", and a channel switched off
 *   and on again is on for the length of the second run, whatever the day adds up to.
 * @param pendingWrite the toggle has moved and Firestore has not confirmed it.
 * @param externalChangeToken changes identity when somebody else moved this channel, which
 *   flashes the bar. Locally initiated changes leave it alone.
 */
data class ChannelUiState(
    val id: String,
    val index: Int,
    val name: String,
    val state: DeviceState,
    val runningSeconds: Long? = null,
    val pendingWrite: Boolean = false,
    val externalChangeToken: Long? = null,
) {
    /** `2h 14m` on a running channel, nothing on the others. */
    val caption: String? get() = runningSeconds?.takeIf { state == DeviceState.ON }?.let(::formatOnDuration)

    /**
     * `Ceiling light, on, on for 2 hours 14 minutes. Double tap to turn off.`
     *
     * The duration is spelled out rather than abbreviated: `2h 14m` is read aloud as
     * something between "two h fourteen m" and nothing at all, depending on the engine.
     */
    val spoken: String
        get() = buildString {
            append("$name, ${state.spoken}")
            if (state == DeviceState.ON && runningSeconds != null) {
                append(", on for ${spellDuration(runningSeconds)}")
            }
            append(".")
            if (state != DeviceState.ERROR) {
                append(" Double tap to turn ${if (state == DeviceState.ON) "off" else "on"}.")
            }
        }
}

/**
 * The unit's day.
 *
 * @param combinedOnSeconds added across channels, so a 3-gang unit with everything on for
 *   two hours reads six. The card is labelled `Combined on` for exactly that reason — the
 *   number is only honest if the label says what it did.
 * @param bands one per channel, sharing one axis. A single merged timeline would throw away
 *   the thing this sheet exists to show: that the fan runs afternoons and the ceiling light
 *   runs evenings.
 */
data class MultiSwitchUsage(
    val combinedOnSeconds: Long,
    val mostUsedChannel: String?,
    val bands: List<TimelineBand>,
) {
    val combinedLabel: String get() = formatOnDuration(combinedOnSeconds)

    /** Section 10: an em dash when nothing ran, never a blank card. */
    val mostUsedLabel: String get() = mostUsedChannel ?: "—"
}

data class TimelineBand(
    val label: String,
    val hourFractions: List<Float>,
    val onSeconds: Long,
) {
    /** Section 11 wants a text alternative per band, not one for the whole stack. */
    val spoken: String get() = "$label, on for ${spellDuration(onSeconds)} today."
}

/**
 * `2 hours 14 minutes`, for screen readers.
 *
 * Zero minutes are dropped rather than spoken, because "two hours zero minutes" is a phrase
 * no person says out loud.
 */
internal fun spellDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val hourWord = if (hours == 1L) "hour" else "hours"
    val minuteWord = if (minutes == 1L) "minute" else "minutes"
    return when {
        hours > 0 && minutes > 0 -> "$hours $hourWord $minutes $minuteWord"
        hours > 0 -> "$hours $hourWord"
        else -> "$minutes $minuteWord"
    }
}

/**
 * Turns one gang unit's live documents into the sheet.
 *
 * @param changedExternally channel ids whose latest change came from the server rather than
 *   from this app, with the token that makes the bar flash. The ViewModel owns that
 *   bookkeeping because it spans emissions; this function only spends it.
 */
internal fun buildMultiSwitchSheetState(
    device: Live<Device>?,
    floor: Floor?,
    channels: List<Live<Channel>>,
    events: List<UsageEvent>,
    changedExternally: Map<String, Long>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): MultiSwitchSheetUiState {
    if (device == null) {
        return MultiSwitchSheetUiState(isLoading = false, isMissing = true, nowMillis = nowMillis)
    }

    val value = device.value
    // Ordered by plate position, never by name: "the second one" has to mean the same thing
    // to the app and to the person standing at the wall.
    val ordered = channels.sortedBy { it.value.index }

    val rows = ordered.map { live ->
        val channel = live.value
        ChannelUiState(
            id = channel.id,
            index = channel.index,
            name = channel.displayName,
            state = channel.status,
            runningSeconds = channel.turnedOnAt?.toDate()?.time
                ?.let { TimeUnit.MILLISECONDS.toSeconds((nowMillis - it).coerceAtLeast(0L)) },
            pendingWrite = !live.isFromServer,
            externalChangeToken = changedExternally[channel.id],
        )
    }

    // The unit is unreachable or it is not; everything else the contract derives from the
    // channels, using the same function the repository writes with.
    val unitState = if (value.status == DeviceState.DISCONNECTED) {
        DeviceState.DISCONNECTED
    } else {
        deriveMultiSwitchStatus(ordered.map { it.value }) ?: value.status
    }

    return MultiSwitchSheetUiState(
        isLoading = false,
        deviceName = value.name,
        locationLine = deviceLocationLine(value, floor, suffix = "${rows.size} gang".takeIf { rows.size > 0 }),
        unitState = unitState,
        channels = rows,
        lastChangedMillis = value.lastChangedAt?.toDate()?.time,
        usage = buildMultiSwitchUsage(
            channels = rows,
            events = events,
            dayStartMillis = startOfDay(nowMillis, zone),
            nowMillis = nowMillis,
        ),
        nowMillis = nowMillis,
    )
}

/**
 * The day, per channel and then combined.
 *
 * Each band is [buildDayUsage] over that channel's own events — the same clipping to
 * midnight, the same treatment of a period that is still open — so the stack cannot drift
 * from the outlet sheet's single bar.
 *
 * Returns null only when no channel ran at all. One channel with history is enough to draw
 * the stack, and the quiet bands are part of what it says.
 */
internal fun buildMultiSwitchUsage(
    channels: List<ChannelUiState>,
    events: List<UsageEvent>,
    dayStartMillis: Long,
    nowMillis: Long,
): MultiSwitchUsage? {
    if (channels.isEmpty()) return null

    val byChannel = events.groupBy { it.channelId }
    val bands = channels.map { channel ->
        val usage = buildDayUsage(byChannel[channel.id].orEmpty(), dayStartMillis, nowMillis)
        TimelineBand(
            label = channel.name,
            hourFractions = usage?.hourFractions ?: List(HoursInDay) { 0f },
            onSeconds = usage?.onSeconds ?: 0L,
        )
    }

    if (bands.all { it.onSeconds == 0L }) return null

    return MultiSwitchUsage(
        combinedOnSeconds = bands.sumOf { it.onSeconds },
        mostUsedChannel = bands.filter { it.onSeconds > 0 }.maxByOrNull { it.onSeconds }?.label,
        bands = bands,
    )
}

