package com.smarthome.control.ui.device

import com.google.firebase.Timestamp
import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Date

/**
 * The outlet sheet's derivation.
 *
 * Most of these are about the day boundary and the open period, because those are the two
 * places where the easy implementation gives a number that is confidently wrong: a total
 * that includes last night, or one that stops moving while the device is still running.
 */
class OutletSheetStateTest {

    /** Midnight UTC. The tests fix the zone so the day boundary is not the build machine's. */
    private val dayStart = 1_755_216_000_000L
    private val now = dayStart + 13 * Hour
    private val zone = ZoneOffset.UTC

    @Test
    fun `time on adds up the closed periods`() {
        val usage = buildDayUsage(
            events = listOf(
                event(from = dayStart + 7 * Hour, to = dayStart + 9 * Hour),
                event(from = dayStart + 12 * Hour, to = dayStart + 12 * Hour + 30 * Minute),
            ),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertEquals(2 * 3600L + 30 * 60L, usage?.onSeconds)
        assertEquals("2h 30m", usage?.timeOnLabel)
        assertEquals(2, usage?.periodCount)
    }

    @Test
    fun `a period still running counts up to now, not to its missing end`() {
        val usage = buildDayUsage(
            events = listOf(open(from = now - 45 * Minute)),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertEquals(45 * 60L, usage?.onSeconds)
        assertEquals("45m", usage?.timeOnLabel)
    }

    @Test
    fun `a period that started before midnight only counts from midnight`() {
        // The iron went on at eleven last night and is still on. Nine hours of it belong to
        // yesterday, and a sheet that says thirteen hours has invented a day.
        val usage = buildDayUsage(
            events = listOf(open(from = dayStart - 1 * Hour)),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertEquals(13 * 3600L, usage?.onSeconds)
    }

    @Test
    fun `a period that ended before midnight does not count at all`() {
        val usage = buildDayUsage(
            events = listOf(event(from = dayStart - 3 * Hour, to = dayStart - 1 * Hour)),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertNull(usage)
    }

    @Test
    fun `nothing today is nothing to draw`() {
        assertNull(buildDayUsage(events = emptyList(), dayStartMillis = dayStart, nowMillis = now))
    }

    @Test
    fun `the timeline fills the hours a run actually covered`() {
        val usage = buildDayUsage(
            events = listOf(event(from = dayStart + 7 * Hour + 30 * Minute, to = dayStart + 9 * Hour)),
            dayStartMillis = dayStart,
            nowMillis = now,
        )
        val hours = usage?.hourFractions.orEmpty()

        assertEquals(HoursInDay, hours.size)
        assertEquals(0f, hours[6], 0.001f)
        // Half of the eighth hour, all of the ninth, none of the tenth.
        assertEquals(0.5f, hours[7], 0.001f)
        assertEquals(1f, hours[8], 0.001f)
        assertEquals(0f, hours[9], 0.001f)
    }

    @Test
    fun `hours still to come are empty rather than absent`() {
        val usage = buildDayUsage(
            events = listOf(open(from = dayStart + 12 * Hour)),
            dayStartMillis = dayStart,
            nowMillis = now,
        )
        val hours = usage?.hourFractions.orEmpty()

        assertEquals(HoursInDay, hours.size)
        assertEquals(0f, hours[23], 0.001f)
    }

    @Test
    fun `switches counts the same runs the timeline draws`() {
        // One run crossed midnight and one started this morning. The bar shows two blocks,
        // so the number under it says two.
        val usage = buildDayUsage(
            events = listOf(
                event(from = dayStart - 1 * Hour, to = dayStart + 1 * Hour),
                event(from = dayStart + 8 * Hour, to = dayStart + 9 * Hour),
            ),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertEquals(2, usage?.periodCount)
    }

    @Test
    fun `the location line counts rows and columns from one`() {
        val state = buildOutletSheetState(
            device = Live(device(gridX = 4, gridY = 1), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertEquals("Ground Floor · R2 C5", state.locationLine)
    }

    @Test
    fun `the coordinates stand alone until the floor document arrives`() {
        val state = buildOutletSheetState(
            device = Live(device(gridX = 4, gridY = 1), isFromServer = true),
            floor = null,
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertEquals("R2 C5", state.locationLine)
    }

    @Test
    fun `a write that has not reached the server yet is pending`() {
        val state = buildOutletSheetState(
            device = Live(device(status = DeviceState.ON), isFromServer = false),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertTrue(state.pendingWrite)
        // The card already shows the new state -- Firestore's cache saw to that. Only the
        // confirmation is outstanding.
        assertEquals(DeviceState.ON, state.state)
    }

    @Test
    fun `a change from the server is not pending`() {
        val state = buildOutletSheetState(
            device = Live(device(status = DeviceState.ON), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertFalse(state.pendingWrite)
    }

    @Test
    fun `a deleted device closes the sheet`() {
        val state = buildOutletSheetState(
            device = null,
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertTrue(state.isMissing)
        assertFalse(state.isLoading)
    }

    @Test
    fun `prose says offline where the contract says disconnected`() {
        val state = buildOutletSheetState(
            device = Live(device(status = DeviceState.DISCONNECTED), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertEquals("OFFLINE", state.stateLabel)
        assertFalse(state.canSwitch)
    }

    @Test
    fun `an unreachable device offers no toggle`() {
        val error = buildOutletSheetState(
            device = Live(device(status = DeviceState.ERROR), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertFalse(error.canSwitch)
        // And the spoken description does not invite a double tap that would do nothing.
        assertEquals("Kitchen Outlet, in error.", error.spokenControl)
    }

    @Test
    fun `the control card says what the double tap will do`() {
        val state = buildOutletSheetState(
            device = Live(device(status = DeviceState.ON), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertEquals("Kitchen Outlet, on. Double tap to turn off.", state.spokenControl)
    }

    @Test
    fun `the timeline reads out as a sentence`() {
        val usage = DayUsage(
            onSeconds = 4 * 3600L + 12 * 60L,
            periodCount = 6,
            hourFractions = List(HoursInDay) { 0f },
        )

        assertEquals(
            "On for 4 hours 12 minutes today, across 6 periods.",
            usage.spokenSummary,
        )
    }

    @Test
    fun `a single period is not pluralised`() {
        val usage = DayUsage(
            onSeconds = 3600L,
            periodCount = 1,
            hourFractions = List(HoursInDay) { 0f },
        )

        assertEquals("On for 1 hour today, across 1 period.", usage.spokenSummary)
    }

    // ------------------------------------------------------------------ fixtures

    private fun device(
        status: DeviceState = DeviceState.OFF,
        gridX: Int = 4,
        gridY: Int = 1,
    ) = Device(
        id = "d1",
        ownerUid = "u1",
        floorId = "ground",
        type = DeviceType.OUTLET,
        name = "Kitchen Outlet",
        gridX = gridX,
        gridY = gridY,
        status = status,
        turnedOnAt = if (status == DeviceState.ON) Timestamp(Date(now - Hour)) else null,
        lastChangedAt = Timestamp(Date(now - 14 * Minute)),
        lastChangedBy = null,
        config = DeviceConfig.Outlet,
    )

    private fun floor() = Floor(
        id = "ground",
        ownerUid = "u1",
        name = "Ground Floor",
        planImageUrl = null,
        gridRows = 6,
        gridCols = 10,
        createdAt = null,
    )

    private fun event(from: Long, to: Long) = UsageEvent(
        id = "e${from}",
        ownerUid = "u1",
        deviceId = "d1",
        channelId = null,
        startedAt = Timestamp(Date(from)),
        endedAt = Timestamp(Date(to)),
        durationSeconds = ((to - from) / 1000L).toInt(),
    )

    private fun open(from: Long) = UsageEvent(
        id = "e${from}",
        ownerUid = "u1",
        deviceId = "d1",
        channelId = null,
        startedAt = Timestamp(Date(from)),
        endedAt = null,
        durationSeconds = null,
    )

    private companion object {
        const val Hour = 60L * 60L * 1000L
        const val Minute = 60L * 1000L
    }
}
