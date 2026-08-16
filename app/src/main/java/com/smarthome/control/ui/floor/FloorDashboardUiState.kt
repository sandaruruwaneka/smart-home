package com.smarthome.control.ui.floor

import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import com.smarthome.control.ui.common.causeLine
import com.smarthome.control.ui.common.createdAtMillisOr
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import java.util.concurrent.TimeUnit

/**
 * Everything the floor dashboard draws.
 *
 * The canvas is the working surface of the whole application, so this state is written to
 * be renderable with no Firestore anywhere near it — the five artboards in section 12 of
 * the screen prompt are plain values, and so are the tests.
 */
data class FloorDashboardUiState(
    val isLoading: Boolean = true,
    val floorName: String = "",
    val planImageUrl: String? = null,
    /** Geometry from the floor document. The grid is the coordinate system, not the image. */
    val gridRows: Int = DefaultGrid,
    val gridCols: Int = DefaultGrid,
    val markers: List<MarkerUiState> = emptyList(),
    /** One per device running against a maximum on-duration right now. */
    val hazards: List<HazardChipUiState> = emptyList(),
    val banner: DashboardBanner? = null,
    /** For the floor switcher behind the title. */
    val floors: List<FloorSwitcherEntry> = emptyList(),
    val error: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val deviceCount: Int get() = markers.size
    val activeCount: Int get() = markers.count { it.state == DeviceState.ON }
    val offlineCount: Int get() = markers.count { it.state == DeviceState.DISCONNECTED }

    /**
     * `7 devices · 2 active · 1 offline`.
     *
     * The offline clause appears only when something is offline. A permanent `0 offline`
     * is the same mistake as a permanently red errors tile: a number that is almost always
     * zero teaches the eye to skip the whole line, including on the day it is not.
     */
    val summaryLine: String get() = buildString {
        append("$deviceCount ${if (deviceCount == 1) "device" else "devices"}")
        append(" · $activeCount active")
        if (offlineCount > 0) append(" · $offlineCount offline")
    }

    /** Devices the `Turn all off` action would actually switch. */
    val switchableOnCount: Int get() = markers.count { it.state == DeviceState.ON && it.canSwitch }

    companion object {
        /** Used until the floor document arrives, so the canvas has something to draw. */
        const val DefaultGrid = 8
    }
}

/**
 * One marker on the plan.
 *
 * @param hazardActive an appliance running against its limit — the one thing in the app
 *   allowed to glow.
 * @param channelBadge the `2/3` a switch bank carries, or null for every other type.
 * @param pendingWrite the app has written and Firestore has not confirmed. Comes straight
 *   from [Live.isFromServer], which is the only reliable answer: comparing
 *   `last_changed_by` races against the app's own write coming back.
 * @param externalChangeToken changes identity when somebody else changed this device, and
 *   only then. The marker flashes once when it does.
 * @param canSwitch false for cameras, whose `status` is stream reachability rather than
 *   power, and for switch banks, whose state lives on their channels.
 */
data class MarkerUiState(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val state: DeviceState,
    val gridX: Int,
    val gridY: Int,
    val hazardActive: Boolean = false,
    val channelBadge: String? = null,
    val pendingWrite: Boolean = false,
    val externalChangeToken: Long? = null,
    val canSwitch: Boolean = true,
) {
    /** `Bedroom lamp, light, on, row 2 column 5` — screen prompt 03 section 11. */
    val spokenDescription: String
        get() = "$name, ${type.label.lowercase()}, ${state.spoken}, " +
            "row ${gridY + 1} column ${gridX + 1}"
}

/** A chip in the hazard strip: a countdown and a name, nothing else. */
data class HazardChipUiState(
    val deviceId: String,
    val name: String,
    val turnedOnAtMillis: Long,
    val maxOnSeconds: Long,
)

/** The Critical banner, when an alert on this floor is outstanding. */
data class DashboardBanner(
    val alertId: String,
    val deviceId: String,
    val cause: String,
    val reason: String,
    val createdAtMillis: Long,
    /** More than one outstanding: the banner collapses and points at Alerts instead. */
    val collapsedCount: Int = 1,
)

/** A row of the floor switcher behind the title. */
data class FloorSwitcherEntry(
    val id: String,
    val name: String,
    val tier: PriorityTier = PriorityTier.NORMAL,
    val isCurrent: Boolean = false,
)

/**
 * Turns one floor's live documents into the screen.
 *
 * Pure, and separate from the ViewModel for the same reason as the floor list's: the
 * decisions worth arguing about are all here, and a function over four lists can be
 * reasoned about without a listener anywhere.
 *
 * @param previousStates the status each device was last seen in, and [changedExternally]
 *   the ids whose latest change arrived from the server rather than from this app. The
 *   ViewModel owns that bookkeeping because it spans emissions; this function only spends
 *   it.
 */
internal fun buildFloorDashboardState(
    floor: Floor?,
    devices: List<Live<Device>>,
    channelsByDevice: Map<String, List<Channel>>,
    alerts: List<Alert>,
    floors: List<Floor>,
    changedExternally: Map<String, Long>,
    nowMillis: Long,
): FloorDashboardUiState {
    val outstanding = alerts.filter { !it.acknowledged }

    // Sorted into reading order — top-left to bottom-right — rather than left in query
    // order. The canvas composes them in list order, which is the order a screen reader
    // walks them in, and section 11 requires that traversal to be a flat sensible list
    // rather than a spatial hunt.
    val markers = devices.sortedWith(
        compareBy({ it.value.gridY }, { it.value.gridX }),
    ).map { live ->
        val device = live.value
        val channels = channelsByDevice[device.id]

        MarkerUiState(
            deviceId = device.id,
            name = device.name,
            type = device.type,
            state = device.status,
            gridX = device.gridX,
            gridY = device.gridY,
            hazardActive = device.isRunningAgainstLimit(nowMillis),
            channelBadge = channels?.let { "${it.count { c -> c.status == DeviceState.ON }}/${it.size}" },
            // Straight from the snapshot's own metadata: false while this app's write is
            // still in flight, true for anything that actually came back from the server.
            pendingWrite = !live.isFromServer,
            externalChangeToken = changedExternally[device.id],
            canSwitch = device.type != DeviceType.CAMERA && device.type != DeviceType.MULTI_SWITCH,
        )
    }

    // Ordered by position rather than by name, so the strip reads left to right in the
    // same order the eye finds them on the plan.
    val hazards = devices
        .map { it.value }
        .filter { it.isRunningAgainstLimit(nowMillis) }
        .sortedWith(compareBy({ it.gridY }, { it.gridX }))
        .mapNotNull { device ->
            val limit = device.applianceConfig?.maxOnDurationSeconds?.toLong() ?: return@mapNotNull null
            val turnedOnAt = device.turnedOnAt?.toDate()?.time ?: return@mapNotNull null
            HazardChipUiState(
                deviceId = device.id,
                name = device.name,
                turnedOnAtMillis = turnedOnAt,
                maxOnSeconds = limit,
            )
        }

    return FloorDashboardUiState(
        isLoading = false,
        floorName = floor?.name.orEmpty(),
        planImageUrl = floor?.planImageUrl,
        gridRows = floor?.gridRows ?: FloorDashboardUiState.DefaultGrid,
        gridCols = floor?.gridCols ?: FloorDashboardUiState.DefaultGrid,
        markers = markers,
        hazards = hazards,
        banner = outstanding.toBanner(nowMillis),
        floors = floors.map { entry ->
            FloorSwitcherEntry(
                id = entry.id,
                name = entry.name,
                isCurrent = entry.id == floor?.id,
            )
        },
        nowMillis = nowMillis,
    )
}

/**
 * Whether this device is an appliance currently running against its maximum on-duration.
 *
 * True from the moment it is switched on, not only near the end. The hazard strip's job is
 * to say "something is running that will be cut off" — waiting until the last tenth would
 * make the strip appear at the worst possible moment rather than the useful one.
 */
private fun Device.isRunningAgainstLimit(nowMillis: Long): Boolean {
    val limit = applianceConfig?.maxOnDurationSeconds ?: return false
    if (status != DeviceState.ON) return false
    val turnedOnAt = turnedOnAt?.toDate()?.time ?: return false
    // A start time in the future is clock skew, not a countdown; treat it as running.
    return limit > 0 && TimeUnit.MILLISECONDS.toSeconds(nowMillis - turnedOnAt) >= 0
}

private fun List<Alert>.toBanner(nowMillis: Long): DashboardBanner? {
    val newest = firstOrNull() ?: return null
    return DashboardBanner(
        alertId = newest.id,
        deviceId = newest.deviceId,
        cause = if (size == 1) {
            newest.causeLine()
        } else {
            "$size devices need attention"
        },
        reason = if (size == 1) {
            newest.message
        } else {
            "Devices were switched off automatically or reported a fault"
        },
        createdAtMillis = newest.createdAtMillisOr(nowMillis),
        collapsedCount = size,
    )
}
