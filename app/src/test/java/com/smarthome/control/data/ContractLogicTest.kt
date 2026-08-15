package com.smarthome.control.data

import com.google.firebase.Timestamp
import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.TimeOfDay
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.data.model.deriveMultiSwitchStatus
import com.smarthome.control.data.model.windowContains
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Date

/**
 * Covers the parts of the data layer that carry contract meaning and no Firestore
 * dependency: the derived multi-switch status, the schedule window, and the values
 * SCHEMA.md section 14 says the client computes.
 *
 * These are the rules the app and the simulator have to agree on while running in
 * different languages against the same documents, so a disagreement here does not fail a
 * build anywhere -- it just makes the two clients render the same gang plate differently.
 */
class ContractLogicTest {

    private fun channel(index: Int, status: DeviceState) = Channel(
        id = "c$index",
        index = index,
        name = "",
        status = status,
        turnedOnAt = null,
        lastChangedAt = null,
    )

    private fun appliance(
        status: DeviceState,
        turnedOnAt: Timestamp?,
        maxOnDurationSeconds: Int = 1800,
    ) = Device(
        id = "d1",
        ownerUid = "u1",
        floorId = "f1",
        type = DeviceType.APPLIANCE,
        name = "Iron",
        gridX = 0,
        gridY = 0,
        status = status,
        turnedOnAt = turnedOnAt,
        lastChangedAt = null,
        lastChangedBy = null,
        config = DeviceConfig.Appliance(maxOnDurationSeconds),
    )

    // ------------------------------------------------- derived parent status

    @Test
    fun `any channel on makes the unit on`() {
        val channels = listOf(
            channel(0, DeviceState.ON),
            channel(1, DeviceState.OFF),
            channel(2, DeviceState.OFF),
        )
        assertEquals(DeviceState.ON, deriveMultiSwitchStatus(channels))
    }

    @Test
    fun `on wins over error, matching the contract's clause order`() {
        val channels = listOf(channel(0, DeviceState.ON), channel(1, DeviceState.ERROR))
        assertEquals(DeviceState.ON, deriveMultiSwitchStatus(channels))
    }

    @Test
    fun `error shows when nothing is on`() {
        val channels = listOf(channel(0, DeviceState.OFF), channel(1, DeviceState.ERROR))
        assertEquals(DeviceState.ERROR, deriveMultiSwitchStatus(channels))
    }

    @Test
    fun `all off makes the unit off`() {
        val channels = listOf(channel(0, DeviceState.OFF), channel(1, DeviceState.OFF))
        assertEquals(DeviceState.OFF, deriveMultiSwitchStatus(channels))
    }

    @Test
    fun `an unreachable unit is disconnected regardless of its channels`() {
        val channels = listOf(channel(0, DeviceState.ON))
        assertEquals(
            DeviceState.DISCONNECTED,
            deriveMultiSwitchStatus(channels, unitUnreachable = true),
        )
    }

    @Test
    fun `no channels yet is not derivable, and must not read as off`() {
        assertNull(deriveMultiSwitchStatus(emptyList()))
    }

    // ------------------------------------------------------ schedule windows

    @Test
    fun `time of day round-trips through the wire format`() {
        val parsed = TimeOfDay.parseOrNull("18:30")
        assertEquals("18:30", parsed?.wireValue)
        assertEquals(18, parsed?.hour)
        assertEquals(30, parsed?.minute)
    }

    @Test
    fun `malformed times are rejected rather than guessed`() {
        listOf("6:30", "24:00", "18:60", "1830", "", null).forEach {
            assertNull("expected null for $it", TimeOfDay.parseOrNull(it))
        }
    }

    @Test
    fun `a normal window contains only times inside it`() {
        val on = TimeOfDay.of(18, 30)
        val off = TimeOfDay.of(23, 0)
        assertTrue(on.windowContains(off, TimeOfDay.of(20, 0)))
        assertTrue(on.windowContains(off, TimeOfDay.of(18, 30)))
        assertFalse(on.windowContains(off, TimeOfDay.of(23, 0)))
        assertFalse(on.windowContains(off, TimeOfDay.of(9, 0)))
    }

    @Test
    fun `a window whose end precedes its start crosses midnight and stays valid`() {
        val on = TimeOfDay.of(22, 0)
        val off = TimeOfDay.of(6, 0)
        assertTrue(on.windowContains(off, TimeOfDay.of(23, 30)))
        assertTrue(on.windowContains(off, TimeOfDay.of(2, 0)))
        assertFalse(on.windowContains(off, TimeOfDay.of(12, 0)))
    }

    @Test
    fun `a light with only one edge configured is not treated as scheduled`() {
        val config = DeviceConfig.fromMap(
            DeviceType.LIGHT,
            mapOf("schedule_enabled" to true, "schedule_on" to "18:30"),
        ) as DeviceConfig.Light
        assertFalse(config.scheduleEnabled)
    }

    @Test
    fun `an appliance with no usable limit is unreadable rather than unlimited`() {
        assertNull(DeviceConfig.fromMap(DeviceType.APPLIANCE, emptyMap()))
        assertNull(DeviceConfig.fromMap(DeviceType.APPLIANCE, mapOf("max_on_duration" to 0)))
    }

    // ----------------------------------------------------------- derivations

    @Test
    fun `countdown counts down and floors at zero`() {
        val now = 1_000_000_000_000L
        val startedOneMinuteAgo = Timestamp(Date(now - 60_000L))
        val device = appliance(DeviceState.ON, startedOneMinuteAgo, maxOnDurationSeconds = 120)
        assertEquals(60L, countdownRemainingSeconds(device, now))

        val startedLongAgo = Timestamp(Date(now - 600_000L))
        val overdue = appliance(DeviceState.ON, startedLongAgo, maxOnDurationSeconds = 120)
        assertEquals(0L, countdownRemainingSeconds(overdue, now))
    }

    @Test
    fun `a device that is off has no countdown at all`() {
        val now = 1_000_000_000_000L
        assertNull(countdownRemainingSeconds(appliance(DeviceState.OFF, null), now))
    }

    @Test
    fun `time on today adds the running period to the closed ones`() {
        val zone = ZoneId.of("Asia/Colombo")
        // Midday, so nothing in this test brushes against a day boundary.
        val now = java.time.ZonedDateTime.of(2026, 8, 15, 12, 0, 0, 0, zone)
            .toInstant().toEpochMilli()

        val closed = UsageEvent(
            id = "e1",
            ownerUid = "u1",
            deviceId = "d1",
            channelId = null,
            startedAt = Timestamp(Date(now - 7_200_000L)),
            endedAt = Timestamp(Date(now - 5_400_000L)),
            durationSeconds = 1800,
        )
        val open = closed.copy(
            id = "e2",
            startedAt = Timestamp(Date(now - 600_000L)),
            endedAt = null,
            durationSeconds = null,
        )

        assertEquals(1800L + 600L, timeOnTodaySeconds(listOf(closed, open), zone, now))
    }

    @Test
    fun `a period running since before midnight is clipped to today`() {
        val zone = ZoneId.of("Asia/Colombo")
        val now = java.time.ZonedDateTime.of(2026, 8, 15, 1, 0, 0, 0, zone)
            .toInstant().toEpochMilli()

        // Switched on at 23:00 the previous day and still running: two hours elapsed, but
        // only the hour since midnight belongs to today.
        val open = UsageEvent(
            id = "e1",
            ownerUid = "u1",
            deviceId = "d1",
            channelId = null,
            startedAt = Timestamp(Date(now - 7_200_000L)),
            endedAt = null,
            durationSeconds = null,
        )

        assertEquals(3600L, timeOnTodaySeconds(listOf(open), zone, now))
    }

    @Test
    fun `floor summaries group one account-wide list and count faults together`() {
        val devices = listOf(
            appliance(DeviceState.ON, Timestamp(Date())).copy(id = "a", floorId = "f1"),
            appliance(DeviceState.OFF, null).copy(id = "b", floorId = "f1"),
            appliance(DeviceState.ERROR, null).copy(id = "c", floorId = "f1"),
            appliance(DeviceState.DISCONNECTED, null).copy(id = "d", floorId = "f2"),
        )

        val summaries = floorSummaries(devices)
        assertEquals(FloorSummary(total = 3, on = 1, faulted = 1), summaries["f1"])
        assertEquals(FloorSummary(total = 1, on = 0, faulted = 1), summaries["f2"])
        assertTrue(summaries.getValue("f2").hasFault)
    }

    @Test
    fun `channel label reads as the sheet shows it`() {
        val channels = listOf(
            channel(0, DeviceState.ON),
            channel(1, DeviceState.ON),
            channel(2, DeviceState.OFF),
        )
        assertEquals("2 of 3 on", channelsOnLabel(channels))
    }

    @Test
    fun `an unnamed channel falls back to its plate position`() {
        assertEquals("Channel 1", channel(0, DeviceState.OFF).displayName)
        assertEquals("Channel 3", channel(2, DeviceState.OFF).displayName)
    }
}
