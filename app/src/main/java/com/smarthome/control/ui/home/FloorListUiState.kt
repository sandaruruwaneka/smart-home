package com.smarthome.control.ui.home

import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import com.smarthome.control.ui.components.AlertType as AlertIconType
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.PriorityTier
import com.smarthome.control.ui.model.priorityTierOf
import com.smarthome.control.data.model.AlertType
import java.util.concurrent.TimeUnit

/**
 * Everything the floor list draws, derived from three live collections.
 *
 * The screen holds no data of its own and re-derives nothing: whatever is here is what is
 * on screen. That is what makes the four artboards in section 10 of the screen prompt
 * previewable as plain values rather than as a Firestore emulator with the right documents
 * in it.
 */
data class FloorListUiState(
    /** True until the first snapshot of every collection has arrived. Drives the skeleton. */
    val isLoading: Boolean = true,
    val summary: HouseSummary = HouseSummary(),
    val floors: List<FloorRow> = emptyList(),
    /** Null when nothing is outstanding, and then the banner occupies no space at all. */
    val banner: HomeBanner? = null,
    /** At most two, newest first. History, not alarm. */
    val recentEvents: List<EventRow> = emptyList(),
    /** Drives the badge on the `Alerts` navigation item. */
    val unacknowledgedCount: Int = 0,
    /**
     * A listener failure, in the user's words.
     *
     * Firestore's flows fail rather than going quiet, so a missing composite index or a
     * rules rejection arrives here instead of looking like an empty house.
     */
    val error: String? = null,
    /**
     * The clock the screen renders relative timestamps against.
     *
     * Carried in the state rather than read at the point of use so that an artboard can
     * fix it, and so that every `min ago` on the screen agrees with every other.
     */
    val nowMillis: Long = System.currentTimeMillis(),
)

/** The four numbers in the status summary. */
data class HouseSummary(
    val totalDevices: Int = 0,
    val activeNow: Int = 0,
    val errors: Int = 0,
    val warnings: Int = 0,
)

/**
 * One floor, with the counts and the tier its card renders at.
 *
 * @param flaggedDevices how many devices on this floor are the reason for the dot, for the
 *   spoken description. Zero when [tier] is Normal.
 */
data class FloorRow(
    val id: String,
    val name: String,
    val deviceCount: Int,
    val activeCount: Int,
    val tier: PriorityTier = PriorityTier.NORMAL,
    val flaggedDevices: Int = 0,
    val planImageUrl: String? = null,
)

/** The Critical-tier banner, in its two forms. */
sealed interface HomeBanner {

    /** When the banner appeared, for the relative timestamp. */
    val createdAtMillis: Long

    /**
     * One outstanding alert. The action goes to the device that caused it, not to the
     * alert list — in that moment the user wants the appliance, not the log.
     */
    data class Single(
        val alertId: String,
        val deviceId: String,
        val floorId: String,
        val cause: String,
        val reason: String,
        override val createdAtMillis: Long,
    ) : HomeBanner

    /**
     * Two or more, collapsed into one banner with a count (master prompt section 5). Its
     * action goes to Alerts, because there is no single device to go to.
     */
    data class Multiple(
        val count: Int,
        override val createdAtMillis: Long,
    ) : HomeBanner
}

/** One row of the recent-events list. */
data class EventRow(
    val id: String,
    val deviceName: String,
    val reason: String,
    val type: AlertIconType,
    val createdAtMillis: Long,
    val acknowledged: Boolean,
)

/**
 * Turns the three collections into the screen.
 *
 * Pure, and deliberately not a method on the ViewModel: the interesting decisions in this
 * file are all in here, and a function taking three lists can be reasoned about — and
 * previewed — without a Firestore instance anywhere near it.
 *
 * @param nowMillis passed in rather than read, so that a device sitting one second past
 *   its limit renders identically in a test, an artboard, and the app.
 */
internal fun buildFloorListState(
    floors: List<Floor>,
    devices: List<Device>,
    alerts: List<Alert>,
    nowMillis: Long,
): FloorListUiState {
    val outstanding = alerts.filter { !it.acknowledged }

    // A device is "in error" if it is faulted right now, or if it has an alert nobody has
    // acknowledged. The second half is what keeps the three levels of the hierarchy
    // agreeing after a cutoff: the safety worker switches the iron OFF, so by the time the
    // banner arrives the device itself looks perfectly ordinary, and an errors tile reading
    // zero underneath a red banner would make the banner look like a bug.
    //
    // Counted as device ids rather than as events, so one appliance that has faulted and
    // raised an alert is one error, not two.
    val criticalDevices = devices
        .filter { it.homeTier(nowMillis) == PriorityTier.CRITICAL }
        .map { it.id }
        .toMutableSet()
    outstanding.forEach { criticalDevices += it.deviceId }

    val warningDevices = devices
        .filter { it.homeTier(nowMillis) == PriorityTier.ATTENTION }
        .map { it.id }
        .toSet() - criticalDevices

    val byFloor = devices.groupBy { it.floorId }
    val alertedFloors = outstanding.map { it.floorId }.toSet()

    val rows = floors.map { floor ->
        val onFloor = byFloor[floor.id].orEmpty()
        val flagged = onFloor.count { it.id in criticalDevices || it.id in warningDevices }
        FloorRow(
            id = floor.id,
            name = floor.name,
            deviceCount = onFloor.size,
            activeCount = onFloor.count { it.status == DeviceState.ON },
            tier = when {
                floor.id in alertedFloors -> PriorityTier.CRITICAL
                onFloor.any { it.id in criticalDevices } -> PriorityTier.CRITICAL
                onFloor.any { it.id in warningDevices } -> PriorityTier.ATTENTION
                else -> PriorityTier.NORMAL
            },
            // At least one: a floor carrying an alert for a device that has since been
            // deleted still has something wrong with it, and "0 devices need attention"
            // would be a strange thing to say next to a red dot.
            flaggedDevices = if (floor.id in alertedFloors) maxOf(flagged, 1) else flagged,
            planImageUrl = floor.planImageUrl,
        )
    }

    return FloorListUiState(
        isLoading = false,
        summary = HouseSummary(
            totalDevices = devices.size,
            activeNow = devices.count { it.status == DeviceState.ON },
            errors = criticalDevices.size,
            warnings = warningDevices.size,
        ),
        floors = rows,
        banner = outstanding.toBanner(nowMillis),
        recentEvents = alerts.take(MAX_RECENT_EVENTS).map { it.toEventRow(nowMillis) },
        unacknowledgedCount = outstanding.size,
        nowMillis = nowMillis,
    )
}

/** At most two rows. Section 2: this block is history, and history does not get the screen. */
private const val MAX_RECENT_EVENTS = 2

/**
 * The tier this device is counted at on the house summary.
 *
 * Two departures from [priorityTierOf], both about what a *count* should mean:
 *
 * `DISCONNECTED` is counted as Attention. The shared derivation leaves it Normal, which is
 * right for a device row — the marker already says "offline" in words and a dashed border.
 * A number cannot say that, and a device the app has lost contact with is exactly the sort
 * of thing the warnings tile exists to surface.
 *
 * A light running inside its own schedule is *not* counted, so `runningOnSchedule` is left
 * false. It is Attention tier on a device row, where it means "this is why the hall light
 * is on". Counting it as a warning would put the tile in amber every evening for a light
 * doing precisely what it was told — the over-escalation section 4 of the screen prompt
 * spends a paragraph warning against.
 */
private fun Device.homeTier(nowMillis: Long): PriorityTier {
    if (status == DeviceState.DISCONNECTED) return PriorityTier.ATTENTION

    val elapsedSeconds = turnedOnAt?.toDate()?.time
        ?.let { TimeUnit.MILLISECONDS.toSeconds(nowMillis - it) }
        ?.coerceAtLeast(0L)

    return priorityTierOf(
        state = status,
        type = type,
        elapsedSeconds = elapsedSeconds,
        maxOnSeconds = applianceConfig?.maxOnDurationSeconds?.toLong(),
    )
}

private fun List<Alert>.toBanner(nowMillis: Long): HomeBanner? {
    val newest = firstOrNull() ?: return null
    val createdAt = newest.createdAtMillis(nowMillis)

    return if (size == 1) {
        HomeBanner.Single(
            alertId = newest.id,
            deviceId = newest.deviceId,
            floorId = newest.floorId,
            cause = newest.cause(),
            reason = newest.message,
            createdAtMillis = createdAt,
        )
    } else {
        HomeBanner.Multiple(count = size, createdAtMillis = createdAt)
    }
}

/**
 * What happened, in the user's terms.
 *
 * The worker writes `message` — `Maximum active time exceeded` — which answers *why*. It
 * deliberately does not say which device or what became of it, because that is the app's
 * half of the sentence and the app is the one that knows how it is about to be laid out.
 */
private fun Alert.cause(): String = when (type) {
    AlertType.MAX_DURATION_EXCEEDED -> "$deviceName switched off automatically"
    AlertType.DEVICE_ERROR -> "$deviceName reported a fault"
}

private fun Alert.toEventRow(nowMillis: Long) = EventRow(
    id = id,
    deviceName = deviceName,
    reason = message,
    type = when (type) {
        AlertType.MAX_DURATION_EXCEEDED -> AlertIconType.MAX_DURATION_EXCEEDED
        AlertType.DEVICE_ERROR -> AlertIconType.DEVICE_ERROR
    },
    createdAtMillis = createdAtMillis(nowMillis),
    acknowledged = acknowledged,
)

/**
 * [Alert.createdAt] is null for the instant between a local write and the server
 * materialising its timestamp. Treating that as "now" is right: an alert with no server
 * time yet is one that has only just been written.
 */
private fun Alert.createdAtMillis(nowMillis: Long): Long =
    createdAt?.toDate()?.time ?: nowMillis
