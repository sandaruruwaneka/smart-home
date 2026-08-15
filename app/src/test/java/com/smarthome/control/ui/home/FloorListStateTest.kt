package com.smarthome.control.ui.home

import com.google.firebase.Timestamp
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.model.PriorityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The floor list's derivation, which is where the screen's real decisions live.
 *
 * These are the cases the prompt's four artboards correspond to, plus the two that are easy
 * to get wrong and impossible to see in a screenshot: a device that has outrun its limit
 * before the safety worker has noticed, and an appliance that has already been cut off.
 */
class FloorListStateTest {

    private val now = 1_755_264_000_000L

    // ------------------------------------------------------------ calm house

    @Test
    fun `a healthy house counts devices and stays normal`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor"), floor("first", "First Floor")),
            devices = listOf(
                device("d1", "ground", DeviceType.LIGHT, DeviceState.ON),
                device("d2", "ground", DeviceType.OUTLET, DeviceState.OFF),
                device("d3", "first", DeviceType.LIGHT, DeviceState.ON),
            ),
            alerts = emptyList(),
            nowMillis = now,
        )

        assertEquals(HouseSummary(totalDevices = 3, activeNow = 2, errors = 0, warnings = 0), state.summary)
        assertNull(state.banner)
        assertTrue(state.floors.all { it.tier == PriorityTier.NORMAL })
        assertEquals(listOf(2, 1), state.floors.map { it.deviceCount })
        assertEquals(listOf(1, 1), state.floors.map { it.activeCount })
    }

    @Test
    fun `an ordinary light being on is not a warning`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = List(6) { device("d$it", "ground", DeviceType.LIGHT, DeviceState.ON) },
            alerts = emptyList(),
            nowMillis = now,
        )

        assertEquals(6, state.summary.activeNow)
        assertEquals(0, state.summary.warnings)
        assertEquals(PriorityTier.NORMAL, state.floors.single().tier)
    }

    // --------------------------------------------------------------- warnings

    @Test
    fun `a running appliance is a warning, and its floor gets the amber dot`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = listOf(
                device(
                    id = "iron",
                    floorId = "ground",
                    type = DeviceType.APPLIANCE,
                    status = DeviceState.ON,
                    turnedOnAt = Timestamp(Date(now - 60_000L)),
                    config = DeviceConfig.Appliance(maxOnDurationSeconds = 600),
                ),
            ),
            alerts = emptyList(),
            nowMillis = now,
        )

        assertEquals(1, state.summary.warnings)
        assertEquals(0, state.summary.errors)
        assertEquals(PriorityTier.ATTENTION, state.floors.single().tier)
        assertEquals(1, state.floors.single().flaggedDevices)
    }

    @Test
    fun `a device the app cannot vouch for is a warning`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = listOf(
                device("camera", "ground", DeviceType.CAMERA, DeviceState.DISCONNECTED),
            ),
            alerts = emptyList(),
            nowMillis = now,
        )

        assertEquals(1, state.summary.warnings)
        assertEquals(0, state.summary.errors)
    }

    // ---------------------------------------------------------------- errors

    @Test
    fun `an appliance past its limit is an error before the worker has caught it`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = listOf(
                device(
                    id = "iron",
                    floorId = "ground",
                    type = DeviceType.APPLIANCE,
                    status = DeviceState.ON,
                    // Switched on eleven minutes ago against a ten-minute limit: the worker
                    // ticks every 60 s, so for up to a minute the app knows and it does not.
                    turnedOnAt = Timestamp(Date(now - 11 * 60_000L)),
                    config = DeviceConfig.Appliance(maxOnDurationSeconds = 600),
                ),
            ),
            alerts = emptyList(),
            nowMillis = now,
        )

        assertEquals(1, state.summary.errors)
        assertEquals(0, state.summary.warnings)
        assertEquals(PriorityTier.CRITICAL, state.floors.single().tier)
    }

    @Test
    fun `a cut-off appliance still reads as an error while its alert is unacknowledged`() {
        // The worker has already switched the iron off, so the device document looks
        // perfectly ordinary. Without the alert the errors tile would read zero underneath
        // a red banner -- the three levels of the hierarchy have to agree.
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor"), floor("first", "First Floor")),
            devices = listOf(
                device("iron", "ground", DeviceType.APPLIANCE, DeviceState.OFF),
                device("lamp", "first", DeviceType.LIGHT, DeviceState.OFF),
            ),
            alerts = listOf(
                alert(
                    id = "a1",
                    deviceId = "iron",
                    deviceName = "Iron",
                    floorId = "ground",
                    createdAtMillis = now - 4 * 60_000L,
                ),
            ),
            nowMillis = now,
        )

        assertEquals(1, state.summary.errors)
        assertEquals(1, state.unacknowledgedCount)
        assertEquals(PriorityTier.CRITICAL, state.floors.first().tier)
        assertEquals(PriorityTier.NORMAL, state.floors.last().tier)

        val banner = state.banner as HomeBanner.Single
        assertEquals("Iron switched off automatically", banner.cause)
        assertEquals("Maximum active time exceeded", banner.reason)
        assertEquals("iron", banner.deviceId)
        assertEquals("ground", banner.floorId)
    }

    @Test
    fun `one device that has faulted and raised an alert counts once`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = listOf(device("outlet", "ground", DeviceType.OUTLET, DeviceState.ERROR)),
            alerts = listOf(
                alert(
                    id = "a1",
                    deviceId = "outlet",
                    deviceName = "Kitchen outlet",
                    floorId = "ground",
                    type = AlertType.DEVICE_ERROR,
                    message = "Device reported a fault",
                    createdAtMillis = now - 60_000L,
                ),
            ),
            nowMillis = now,
        )

        assertEquals(1, state.summary.errors)
    }

    // --------------------------------------------------------------- banners

    @Test
    fun `two outstanding alerts collapse into one banner`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = emptyList(),
            alerts = listOf(
                alert("a1", "iron", "Iron", "ground", createdAtMillis = now - 60_000L),
                alert("a2", "heater", "Water heater", "ground", createdAtMillis = now - 300_000L),
            ),
            nowMillis = now,
        )

        val banner = state.banner as HomeBanner.Multiple
        assertEquals(2, banner.count)
        assertEquals(2, state.unacknowledgedCount)
    }

    @Test
    fun `acknowledged alerts stay in the history and out of the banner`() {
        val state = buildFloorListState(
            floors = listOf(floor("ground", "Ground Floor")),
            devices = emptyList(),
            alerts = listOf(
                alert("a1", "iron", "Iron", "ground", createdAtMillis = now - 60_000L, acknowledged = true),
            ),
            nowMillis = now,
        )

        assertNull(state.banner)
        assertEquals(0, state.unacknowledgedCount)
        assertEquals(1, state.recentEvents.size)
        assertEquals(PriorityTier.NORMAL, state.floors.single().tier)
    }

    @Test
    fun `recent events show at most two`() {
        val state = buildFloorListState(
            floors = emptyList(),
            devices = emptyList(),
            alerts = (1..5).map {
                alert("a$it", "iron", "Iron", "ground", createdAtMillis = now - it * 60_000L, acknowledged = true)
            },
            nowMillis = now,
        )

        assertEquals(listOf("a1", "a2"), state.recentEvents.map { it.id })
    }

    // ----------------------------------------------------------------- empty

    @Test
    fun `an empty house is not a loading house`() {
        val state = buildFloorListState(
            floors = emptyList(),
            devices = emptyList(),
            alerts = emptyList(),
            nowMillis = now,
        )

        assertEquals(false, state.isLoading)
        assertEquals(HouseSummary(), state.summary)
        assertTrue(state.floors.isEmpty())
    }

    // ---------------------------------------------------------------- fixtures

    private fun floor(id: String, name: String) = Floor(
        id = id,
        ownerUid = "uid",
        name = name,
        planImageUrl = null,
        gridRows = 8,
        gridCols = 8,
        createdAt = Timestamp(Date(now)),
    )

    private fun device(
        id: String,
        floorId: String,
        type: DeviceType,
        status: DeviceState,
        turnedOnAt: Timestamp? = null,
        config: DeviceConfig = defaultConfig(type),
    ) = Device(
        id = id,
        ownerUid = "uid",
        floorId = floorId,
        type = type,
        name = id,
        gridX = 0,
        gridY = 0,
        status = status,
        turnedOnAt = turnedOnAt,
        lastChangedAt = Timestamp(Date(now)),
        lastChangedBy = null,
        config = config,
    )

    private fun defaultConfig(type: DeviceType): DeviceConfig = when (type) {
        DeviceType.OUTLET -> DeviceConfig.Outlet
        DeviceType.MULTI_SWITCH -> DeviceConfig.MultiSwitch(channelCount = 2)
        DeviceType.LIGHT -> DeviceConfig.Light.OFF
        DeviceType.APPLIANCE -> DeviceConfig.Appliance(maxOnDurationSeconds = 600)
        DeviceType.CAMERA -> DeviceConfig.Camera(streamUri = "", snapshotUri = "")
    }

    private fun alert(
        id: String,
        deviceId: String,
        deviceName: String,
        floorId: String,
        type: AlertType = AlertType.MAX_DURATION_EXCEEDED,
        message: String = "Maximum active time exceeded",
        createdAtMillis: Long,
        acknowledged: Boolean = false,
    ) = Alert(
        id = id,
        ownerUid = "uid",
        deviceId = deviceId,
        deviceName = deviceName,
        floorId = floorId,
        type = type,
        message = message,
        createdAt = Timestamp(Date(createdAtMillis)),
        acknowledged = acknowledged,
    )
}
