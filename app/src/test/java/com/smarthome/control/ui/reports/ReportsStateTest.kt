package com.smarthome.control.ui.reports

import com.google.firebase.Timestamp
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Date

/**
 * The reports aggregation.
 *
 * A chart that is subtly wrong looks exactly like a chart that is right, which is why this
 * screen's arithmetic gets more tests than its UI has states. Most of them are about the
 * range boundary — the place where an hour silently lands in the wrong bucket.
 */
class ReportsStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Colombo")

    /** 2026-08-15, 19:45 in Colombo. */
    private val now = 1_755_267_300_000L
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    /** Midnight Colombo on the day of [now]. */
    private val todayStart = now - 19 * hour - 45 * 60_000L

    @Test
    fun `total on adds up the runs inside the range`() {
        val state = build(
            events = listOf(
                event("iron", todayStart + 8 * hour, todayStart + 10 * hour),
                event("lamp", todayStart + 12 * hour, todayStart + 12 * hour + 30 * 60_000L),
            ),
        )

        assertEquals(2 * 3600L + 30 * 60L, state.totalOnSeconds)
        assertEquals("2h 30m", state.totalOnLabel)
        assertEquals(2, state.devicesUsed)
    }

    @Test
    fun `a run still going counts up to now, not past it`() {
        val state = build(events = listOf(open("iron", now - 45 * 60_000L)))
        assertEquals(45 * 60L, state.totalOnSeconds)
    }

    @Test
    fun `a run that started before the range only counts from the range start`() {
        // Yesterday evening into this morning, asked about Today. The hours before midnight
        // belong to yesterday and donating them here would inflate every figure on screen.
        val state = build(events = listOf(event("iron", todayStart - 3 * hour, todayStart + 1 * hour)))
        assertEquals(3600L, state.totalOnSeconds)
    }

    @Test
    fun `a run entirely before the range contributes nothing`() {
        val state = build(events = listOf(event("iron", todayStart - 5 * hour, todayStart - 4 * hour)))
        assertEquals(0L, state.totalOnSeconds)
        assertTrue(state.deviceBars.isEmpty())
    }

    @Test
    fun `seven days reaches back six midnights, not seven`() {
        // The range includes today, so a 7-day window starts six days ago at midnight.
        // Off-by-one here is a whole extra day of somebody's usage.
        val justInside = build(
            range = ReportRange.Week,
            events = listOf(event("iron", todayStart - 6 * day + hour, todayStart - 6 * day + 2 * hour)),
        )
        val justOutside = build(
            range = ReportRange.Week,
            events = listOf(event("iron", todayStart - 7 * day + hour, todayStart - 7 * day + 2 * hour)),
        )

        assertEquals(3600L, justInside.totalOnSeconds)
        assertEquals(0L, justOutside.totalOnSeconds)
    }

    // ------------------------------------------------------------------ bars

    @Test
    fun `bars are sorted by on-time and scaled to the largest`() {
        val state = build(
            events = listOf(
                event("lamp", todayStart + 1 * hour, todayStart + 2 * hour),
                event("iron", todayStart + 3 * hour, todayStart + 7 * hour),
                event("fan", todayStart + 8 * hour, todayStart + 10 * hour),
            ),
        )

        assertEquals(listOf("Iron", "Fan", "Lamp"), state.deviceBars.map { it.name })
        // The tallest bar is full width and the others are relative to it, so the shortest
        // is still visible rather than a hairline against a fixed ceiling.
        assertEquals(1f, state.deviceBars[0].fraction, 0.001f)
        assertEquals(0.5f, state.deviceBars[1].fraction, 0.001f)
        assertEquals(0.25f, state.deviceBars[2].fraction, 0.001f)
    }

    @Test
    fun `an appliance bar is marked as the hazard class`() {
        val state = build(events = listOf(event("iron", todayStart + 1 * hour, todayStart + 2 * hour)))
        assertTrue(state.deviceBars.single().isHazardClass)
    }

    @Test
    fun `a deleted device keeps its hours rather than losing them`() {
        val state = build(events = listOf(event("ghost", todayStart + 1 * hour, todayStart + 2 * hour)))
        assertEquals("Removed device", state.deviceBars.single().name)
        assertEquals(3600L, state.totalOnSeconds)
    }

    @Test
    fun `concurrent runs of one device count once, not twice`() {
        // A three-gang plate with every channel on has three overlapping usage events.
        // Summing them reports more hours than the day contains, which is how a credible
        // screen starts looking broken.
        val state = build(
            events = listOf(
                event("fan", todayStart + 8 * hour, todayStart + 12 * hour),
                event("fan", todayStart + 8 * hour, todayStart + 12 * hour),
                event("fan", todayStart + 8 * hour, todayStart + 12 * hour),
            ),
        )

        assertEquals(4 * 3600L, state.totalOnSeconds)
        assertEquals(1, state.devicesUsed)
    }

    @Test
    fun `partly overlapping runs merge into their union`() {
        val state = build(
            events = listOf(
                event("fan", todayStart + 8 * hour, todayStart + 10 * hour),
                event("fan", todayStart + 9 * hour, todayStart + 12 * hour),
            ),
        )

        assertEquals(4 * 3600L, state.totalOnSeconds)
    }

    @Test
    fun `runs that do not overlap still add up`() {
        val state = build(
            events = listOf(
                event("fan", todayStart + 1 * hour, todayStart + 2 * hour),
                event("fan", todayStart + 5 * hour, todayStart + 6 * hour),
            ),
        )

        assertEquals(2 * 3600L, state.totalOnSeconds)
    }

    @Test
    fun `several runs by one device become one bar`() {
        val state = build(
            events = listOf(
                event("iron", todayStart + 1 * hour, todayStart + 2 * hour),
                event("iron", todayStart + 5 * hour, todayStart + 6 * hour),
            ),
        )

        assertEquals(1, state.deviceBars.size)
        assertEquals(2 * 3600L, state.deviceBars.single().onSeconds)
        assertEquals(1, state.devicesUsed)
    }

    // ----------------------------------------------------------------- trend

    @Test
    fun `the trend has one bar per day of the range`() {
        assertEquals(7, build(range = ReportRange.Week).dailyTrend.size)
        assertEquals(30, build(range = ReportRange.Month).dailyTrend.size)
    }

    @Test
    fun `today renders no trend at all`() {
        val state = build(events = listOf(event("iron", todayStart + hour, todayStart + 2 * hour)))
        assertFalse(state.showsTrend)
    }

    @Test
    fun `a run spanning midnight is split across both days`() {
        // 23:00 to 01:00 is an hour on each side, not two hours on either.
        val state = build(
            range = ReportRange.Week,
            events = listOf(event("iron", todayStart - hour, todayStart + hour)),
        )

        val yesterday = state.dailyTrend[state.dailyTrend.size - 2]
        val today = state.dailyTrend.last()

        assertEquals(3600L, yesterday.totalSeconds)
        assertEquals(3600L, today.totalSeconds)
    }

    @Test
    fun `the trend splits a day by device type`() {
        val state = build(
            range = ReportRange.Week,
            events = listOf(
                event("iron", todayStart + 1 * hour, todayStart + 2 * hour),
                event("lamp", todayStart + 1 * hour, todayStart + 3 * hour),
            ),
        )

        val today = state.dailyTrend.last()
        assertEquals(3600L, today.secondsByType[DeviceType.APPLIANCE])
        assertEquals(2 * 3600L, today.secondsByType[DeviceType.LIGHT])
        assertEquals("Lamp", today.topDeviceName)
    }

    // --------------------------------------------------------------- cutoffs

    @Test
    fun `cutoffs are counted per device, most frequent first`() {
        val state = build(
            alerts = listOf(
                cutoff("iron", "Iron", todayStart + hour),
                cutoff("iron", "Iron", todayStart + 5 * hour),
                cutoff("heater", "Space Heater", todayStart + 6 * hour),
            ),
        )

        assertEquals(3, state.cutoffCount)
        assertEquals(listOf("Iron", "Space Heater"), state.cutoffRows.map { it.deviceName })
        assertEquals(2, state.cutoffRows.first().count)
    }

    @Test
    fun `a fault is not a cutoff`() {
        val state = build(
            alerts = listOf(
                Alert("a1", "u1", "iron", "Iron", "ground", AlertType.DEVICE_ERROR,
                    "Device reported a fault", Timestamp(Date(todayStart + hour)), false),
            ),
        )

        assertEquals(0, state.cutoffCount)
        assertTrue(state.cutoffRows.isEmpty())
    }

    @Test
    fun `cutoffs outside the range are not counted`() {
        val state = build(alerts = listOf(cutoff("iron", "Iron", todayStart - 2 * hour)))
        assertEquals(0, state.cutoffCount)
    }

    // ---------------------------------------------------------------- empty

    @Test
    fun `a run that started before the range still counts its overlap`() {
        // An appliance left on overnight. The aggregation clips it to today; the ViewModel's
        // query has to be wide enough to hand it over in the first place, which is the half
        // that was missing.
        val state = build(events = listOf(open("iron", todayStart - 4 * hour)))

        // Midnight to 19:45, the whole of today so far -- not the run's full length.
        assertEquals(19 * 3600L + 45 * 60L, state.totalOnSeconds)
        assertEquals(1, state.devicesUsed)
    }

    @Test
    fun `the today range does not say "in the last today"`() {
        val today = build(range = ReportRange.Today, events = emptyList(), hasAnyUsage = true)
        assertEquals("No activity today.", today.emptyMessage)
    }

    @Test
    fun `nothing in range is not the same as nothing ever`() {
        val inRange = build(range = ReportRange.Week, events = emptyList(), hasAnyUsage = true)
        assertEquals("No activity in the last 7 days.", inRange.emptyMessage)
        assertTrue(inRange.showsRangeReset)

        val never = build(events = emptyList(), hasAnyUsage = false)
        assertEquals(
            "No usage recorded yet. Turn a device on and its activity will appear here.",
            never.emptyMessage,
        )
        assertFalse(never.showsRangeReset)
    }

    @Test
    fun `the chart reads out before it is looked at`() {
        val state = build(
            events = listOf(
                event("fan", todayStart + 1 * hour, todayStart + 7 * hour + 12 * 60_000L),
                event("lamp", todayStart + 8 * hour, todayStart + 8 * hour + 30 * 60_000L),
            ),
        )

        assertEquals("Fan 6 hours 12 minutes, Lamp 30 minutes", state.barsSpoken)
    }

    // ------------------------------------------------------------ fixtures

    private fun build(
        range: ReportRange = ReportRange.Today,
        events: List<UsageEvent> = emptyList(),
        alerts: List<Alert> = emptyList(),
        hasAnyUsage: Boolean = events.isNotEmpty(),
    ) = buildReportsState(
        range = range,
        events = events,
        devices = listOf(
            device("iron", "Iron", DeviceType.APPLIANCE),
            device("lamp", "Lamp", DeviceType.LIGHT),
            device("fan", "Fan", DeviceType.OUTLET),
        ),
        alerts = alerts,
        nowMillis = now,
        zone = zone,
        hasAnyUsage = hasAnyUsage,
    )

    private fun device(id: String, name: String, type: DeviceType) = Device(
        id = id,
        ownerUid = "u1",
        floorId = "ground",
        type = type,
        name = name,
        gridX = 0,
        gridY = 0,
        status = DeviceState.OFF,
        turnedOnAt = null,
        lastChangedAt = null,
        lastChangedBy = null,
        config = when (type) {
            DeviceType.APPLIANCE -> DeviceConfig.Appliance(1800)
            DeviceType.LIGHT -> DeviceConfig.Light.OFF
            else -> DeviceConfig.Outlet
        },
    )

    private fun event(deviceId: String, from: Long, to: Long) = UsageEvent(
        id = "e-$deviceId-$from",
        ownerUid = "u1",
        deviceId = deviceId,
        channelId = null,
        startedAt = Timestamp(Date(from)),
        endedAt = Timestamp(Date(to)),
        durationSeconds = ((to - from) / 1000L).toInt(),
    )

    private fun open(deviceId: String, from: Long) = UsageEvent(
        id = "e-$deviceId-$from",
        ownerUid = "u1",
        deviceId = deviceId,
        channelId = null,
        startedAt = Timestamp(Date(from)),
        endedAt = null,
        durationSeconds = null,
    )

    private fun cutoff(deviceId: String, name: String, at: Long) = Alert(
        id = "a-$deviceId-$at",
        ownerUid = "u1",
        deviceId = deviceId,
        deviceName = name,
        floorId = "ground",
        type = AlertType.MAX_DURATION_EXCEEDED,
        message = "Maximum on time reached",
        createdAt = Timestamp(Date(at)),
        acknowledged = false,
    )
}
