package com.smarthome.control.ui.floor

import com.google.firebase.Timestamp
import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The floor dashboard's derivation.
 *
 * The cases here are the ones that decide what the examiner sees: which devices earn a
 * hazard chip, which marker is showing an unconfirmed write, and whether a change that
 * arrived from the simulator is told apart from one the user just made themselves.
 */
class FloorDashboardStateTest {

    private val now = 1_755_264_000_000L
    private val floorId = "ground"

    @Test
    fun `markers come out in reading order, not query order`() {
        val state = build(
            devices = listOf(
                live(device("c", gridX = 1, gridY = 4)),
                live(device("a", gridX = 5, gridY = 0)),
                live(device("b", gridX = 1, gridY = 0)),
            ),
        )

        // Top row first, left to right within it -- the order a screen reader walks.
        assertEquals(listOf("b", "a", "c"), state.markers.map { it.deviceId })
    }

    @Test
    fun `summary line names devices, active and offline`() {
        val state = build(
            devices = listOf(
                live(device("a", status = DeviceState.ON)),
                live(device("b", status = DeviceState.ON)),
                live(device("c", status = DeviceState.OFF)),
                live(device("d", status = DeviceState.DISCONNECTED)),
            ),
        )

        assertEquals("4 devices · 2 active · 1 offline", state.summaryLine)
    }

    @Test
    fun `a floor with nothing offline does not say so`() {
        val state = build(
            devices = listOf(
                live(device("a", status = DeviceState.ON)),
                live(device("b", status = DeviceState.OFF)),
            ),
        )

        assertEquals("2 devices · 1 active", state.summaryLine)
    }

    // --------------------------------------------------------------- hazards

    @Test
    fun `a running appliance earns a hazard chip from the moment it is switched on`() {
        val state = build(
            devices = listOf(
                live(
                    device(
                        id = "iron",
                        name = "Iron",
                        type = DeviceType.APPLIANCE,
                        status = DeviceState.ON,
                        turnedOnAt = Timestamp(Date(now - 5_000L)),
                        config = DeviceConfig.Appliance(maxOnDurationSeconds = 240),
                    ),
                ),
            ),
        )

        val chip = state.hazards.single()
        assertEquals("Iron", chip.name)
        assertEquals(240L, chip.maxOnSeconds)
        assertTrue(state.markers.single().hazardActive)
    }

    @Test
    fun `an appliance that is off has no chip and no glow`() {
        val state = build(
            devices = listOf(
                live(
                    device(
                        id = "iron",
                        type = DeviceType.APPLIANCE,
                        status = DeviceState.OFF,
                        config = DeviceConfig.Appliance(maxOnDurationSeconds = 240),
                    ),
                ),
            ),
        )

        assertTrue(state.hazards.isEmpty())
        assertFalse(state.markers.single().hazardActive)
    }

    @Test
    fun `an ordinary light running does not earn the glow, however long it has been on`() {
        val state = build(
            devices = listOf(
                live(
                    device(
                        id = "lamp",
                        type = DeviceType.LIGHT,
                        status = DeviceState.ON,
                        turnedOnAt = Timestamp(Date(now - 9_000_000L)),
                        config = DeviceConfig.Light.OFF,
                    ),
                ),
            ),
        )

        assertTrue(state.hazards.isEmpty())
        assertFalse(state.markers.single().hazardActive)
    }

    // ------------------------------------------------------- write states

    @Test
    fun `an unconfirmed write shows as pending`() {
        val state = build(
            devices = listOf(
                Live(device("a", status = DeviceState.ON), isFromServer = false),
                Live(device("b", status = DeviceState.OFF), isFromServer = true),
            ),
        )

        assertTrue(state.markers.first { it.deviceId == "a" }.pendingWrite)
        assertFalse(state.markers.first { it.deviceId == "b" }.pendingWrite)
    }

    @Test
    fun `a device changed by somebody else carries a flash token`() {
        val state = build(
            devices = listOf(live(device("a"))),
            changedExternally = mapOf("a" to 7L),
        )

        assertEquals(7L, state.markers.single().externalChangeToken)
    }

    // ------------------------------------------------------------- switching

    @Test
    fun `cameras and switch banks are not directly switchable`() {
        val state = build(
            devices = listOf(
                live(device("cam", type = DeviceType.CAMERA, config = DeviceConfig.Camera("", ""))),
                live(
                    device(
                        "bank",
                        type = DeviceType.MULTI_SWITCH,
                        status = DeviceState.ON,
                        config = DeviceConfig.MultiSwitch(channelCount = 3),
                    ),
                ),
                live(device("lamp", type = DeviceType.LIGHT, status = DeviceState.ON)),
            ),
        )

        assertFalse(state.markers.first { it.deviceId == "cam" }.canSwitch)
        assertFalse(state.markers.first { it.deviceId == "bank" }.canSwitch)
        assertTrue(state.markers.first { it.deviceId == "lamp" }.canSwitch)
        // Only the lamp would be touched by "Turn all off".
        assertEquals(1, state.switchableOnCount)
    }

    @Test
    fun `a switch bank shows how many of its channels are on`() {
        val state = build(
            devices = listOf(
                live(
                    device(
                        "bank",
                        type = DeviceType.MULTI_SWITCH,
                        status = DeviceState.ON,
                        config = DeviceConfig.MultiSwitch(channelCount = 3),
                    ),
                ),
            ),
            channels = mapOf(
                "bank" to listOf(
                    channel("c0", 0, DeviceState.ON),
                    channel("c1", 1, DeviceState.ON),
                    channel("c2", 2, DeviceState.OFF),
                ),
            ),
        )

        assertEquals("2/3", state.markers.single().channelBadge)
    }

    // --------------------------------------------------------------- banner

    @Test
    fun `an outstanding alert on this floor raises the banner`() {
        val state = build(
            devices = listOf(live(device("iron", name = "Iron"))),
            alerts = listOf(alert("a1", "iron", "Iron")),
        )

        val banner = requireNotNull(state.banner)
        assertEquals("Iron switched off automatically", banner.cause)
        assertEquals("Maximum active time exceeded", banner.reason)
        assertEquals(1, banner.collapsedCount)
    }

    @Test
    fun `several outstanding alerts collapse into one banner with a count`() {
        val state = build(
            devices = emptyList(),
            alerts = listOf(
                alert("a1", "iron", "Iron"),
                alert("a2", "heater", "Water heater"),
            ),
        )

        val banner = requireNotNull(state.banner)
        assertEquals("2 devices need attention", banner.cause)
        assertEquals(2, banner.collapsedCount)
    }

    @Test
    fun `an acknowledged alert raises nothing`() {
        val state = build(
            devices = emptyList(),
            alerts = listOf(alert("a1", "iron", "Iron", acknowledged = true)),
        )

        assertNull(state.banner)
    }

    // ------------------------------------------------------- accessibility

    @Test
    fun `a marker says what it is and where it is`() {
        val state = build(
            devices = listOf(
                live(device("lamp", name = "Bedroom lamp", type = DeviceType.LIGHT, status = DeviceState.ON, gridX = 4, gridY = 1)),
            ),
        )

        assertEquals(
            "Bedroom lamp, light, on, row 2 column 5",
            state.markers.single().spokenDescription,
        )
    }

    // -------------------------------------------------------------- geometry

    @Test
    fun `geometry and the floor switcher come from the floor documents`() {
        val here = floor(floorId, "Ground Floor", rows = 6, cols = 10)
        val upstairs = floor("first", "First Floor")

        val state = buildFloorDashboardState(
            floor = here,
            devices = emptyList(),
            channelsByDevice = emptyMap(),
            alerts = emptyList(),
            floors = listOf(here, upstairs),
            changedExternally = emptyMap(),
            nowMillis = now,
        )

        assertEquals("Ground Floor", state.floorName)
        assertEquals(6, state.gridRows)
        assertEquals(10, state.gridCols)
        assertEquals(listOf(true, false), state.floors.map { it.isCurrent })
    }

    @Test
    fun `a floor that has not loaded still gives the canvas a grid to draw`() {
        val state = buildFloorDashboardState(
            floor = null,
            devices = emptyList(),
            channelsByDevice = emptyMap(),
            alerts = emptyList(),
            floors = emptyList(),
            changedExternally = emptyMap(),
            nowMillis = now,
        )

        assertEquals(FloorDashboardUiState.DefaultGrid, state.gridRows)
        assertEquals(FloorDashboardUiState.DefaultGrid, state.gridCols)
    }

    // ---------------------------------------------------------------- fixtures

    private fun build(
        devices: List<Live<Device>>,
        channels: Map<String, List<Channel>> = emptyMap(),
        alerts: List<Alert> = emptyList(),
        changedExternally: Map<String, Long> = emptyMap(),
    ) = buildFloorDashboardState(
        floor = floor(floorId, "Ground Floor"),
        devices = devices,
        channelsByDevice = channels,
        alerts = alerts,
        floors = listOf(floor(floorId, "Ground Floor")),
        changedExternally = changedExternally,
        nowMillis = now,
    )

    private fun live(device: Device) = Live(device, isFromServer = true)

    private fun floor(id: String, name: String, rows: Int = 8, cols: Int = 8) = Floor(
        id = id,
        ownerUid = "uid",
        name = name,
        planImageUrl = null,
        gridRows = rows,
        gridCols = cols,
        createdAt = Timestamp(Date(now)),
    )

    private fun device(
        id: String,
        name: String = id,
        type: DeviceType = DeviceType.OUTLET,
        status: DeviceState = DeviceState.OFF,
        gridX: Int = 0,
        gridY: Int = 0,
        turnedOnAt: Timestamp? = null,
        config: DeviceConfig = DeviceConfig.Outlet,
    ) = Device(
        id = id,
        ownerUid = "uid",
        floorId = floorId,
        type = type,
        name = name,
        gridX = gridX,
        gridY = gridY,
        status = status,
        turnedOnAt = turnedOnAt,
        lastChangedAt = Timestamp(Date(now)),
        lastChangedBy = null,
        config = config,
    )

    private fun channel(id: String, index: Int, status: DeviceState) = Channel(
        id = id,
        index = index,
        name = "Channel $index",
        status = status,
        turnedOnAt = null,
        lastChangedAt = Timestamp(Date(now)),
    )

    private fun alert(
        id: String,
        deviceId: String,
        deviceName: String,
        acknowledged: Boolean = false,
    ) = Alert(
        id = id,
        ownerUid = "uid",
        deviceId = deviceId,
        deviceName = deviceName,
        floorId = floorId,
        type = AlertType.MAX_DURATION_EXCEEDED,
        message = "Maximum active time exceeded",
        createdAt = Timestamp(Date(now - 60_000L)),
        acknowledged = acknowledged,
    )
}
