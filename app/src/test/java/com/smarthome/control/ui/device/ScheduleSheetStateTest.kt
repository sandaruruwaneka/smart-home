package com.smarthome.control.ui.device

import com.google.firebase.Timestamp
import com.smarthome.control.data.Live
import com.smarthome.control.data.model.ChangeSource
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.TimeOfDay
import com.smarthome.control.data.model.UsageEvent
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
 * The schedule sheet's arithmetic.
 *
 * Most of these are about midnight, because that is where every schedule bug in every app
 * lives: a window that crosses it, a countdown that wraps around it, a day boundary drawn
 * in the wrong timezone.
 */
class ScheduleSheetStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Colombo")

    /** 2026-08-15, 19:45 in Colombo — which is 14:15 UTC, since the home is UTC+5:30. */
    private val now = 1_755_267_300_000L

    @Test
    fun `a window that does not cross midnight contains the evening`() {
        val state = state(on = TimeOfDay.of(18, 30), off = TimeOfDay.of(23, 0))

        assertTrue(state.isInsideWindow)
        assertEquals("Turns off in 3h 15m", state.nextEventLine)
    }

    @Test
    fun `outside the window it counts to the opening edge`() {
        // 14:00 Colombo, four and a half hours before the light comes on.
        val state = state(
            on = TimeOfDay.of(18, 30),
            off = TimeOfDay.of(23, 0),
            nowMillis = now - 5 * Hour - 45 * Minute,
            status = DeviceState.OFF,
        )

        assertFalse(state.isInsideWindow)
        assertEquals("Turns on in 4h 30m", state.nextEventLine)
    }

    @Test
    fun `a window through midnight is one window, not two`() {
        // 22:00 to 06:00, asked at 19:45 -- outside; and the same window at 23:30 -- inside.
        val outside = state(on = TimeOfDay.of(22, 0), off = TimeOfDay.of(6, 0))
        assertFalse(outside.isInsideWindow)

        val inside = state(
            on = TimeOfDay.of(22, 0),
            off = TimeOfDay.of(6, 0),
            nowMillis = now + 3 * Hour + 45 * Minute,
        )
        assertTrue(inside.isInsideWindow)
        assertEquals("Turns off in 6h 30m", inside.nextEventLine)
    }

    @Test
    fun `an overnight window says so in words`() {
        val state = state(on = TimeOfDay.of(22, 0), off = TimeOfDay.of(6, 0))
        assertEquals("Overnight — 22:00 today until 06:00 tomorrow.", state.overnightHelper)
    }

    @Test
    fun `a daytime window needs no overnight helper`() {
        assertNull(state(on = TimeOfDay.of(18, 30), off = TimeOfDay.of(23, 0)).overnightHelper)
    }

    @Test
    fun `the sweep of an overnight window wraps rather than going negative`() {
        // 22:00 to 06:00 is eight hours, which is a third of the ring.
        assertEquals(8 * 60, windowMinutes(TimeOfDay.of(22, 0), TimeOfDay.of(6, 0)))
        assertEquals(4 * 60 + 30, windowMinutes(TimeOfDay.of(18, 30), TimeOfDay.of(23, 0)))
    }

    @Test
    fun `identical edges are refused rather than drawn as an empty ring`() {
        val state = state(on = TimeOfDay.of(18, 0), off = TimeOfDay.of(18, 0))
        assertEquals("Start and end times must be different.", state.sameTimeError)
    }

    @Test
    fun `a window narrower than the tick interval warns`() {
        val state = state(on = TimeOfDay.of(18, 0), off = TimeOfDay.of(18, 3))
        assertEquals(
            "This window is shorter than the 1-minute check interval and may be missed.",
            state.shortWindowWarning,
        )
        assertNull(state(on = TimeOfDay.of(18, 0), off = TimeOfDay.of(18, 30)).shortWindowWarning)
    }

    @Test
    fun `under a minute reads shortly, never zero`() {
        // 19:45 with the window closing at 19:45 -- the edge is a whole day away by the
        // wrapping rule, so use one that lands 40 seconds out instead.
        val state = state(
            on = TimeOfDay.of(18, 30),
            off = TimeOfDay.of(19, 46),
            nowMillis = now + 20_000L,
        )
        assertEquals("Turns off shortly", state.nextEventLine)
    }

    // ------------------------------------------------------------- override

    @Test
    fun `a light switched off inside its own window is overridden`() {
        val state = state(
            on = TimeOfDay.of(18, 30),
            off = TimeOfDay.of(23, 0),
            status = DeviceState.OFF,
        )

        assertTrue(state.isOverridden)
        assertEquals("⏱ overridden", state.chipLabel)
        assertEquals("Off until you turn it on — schedule resumes at 23:00.", state.nextEventLine)
    }

    @Test
    fun `a light switched on outside its window is overridden the other way`() {
        val state = state(
            on = TimeOfDay.of(18, 30),
            off = TimeOfDay.of(23, 0),
            nowMillis = now - 6 * Hour,
            status = DeviceState.ON,
        )

        assertTrue(state.isOverridden)
        assertEquals("On until you turn it off — schedule resumes at 18:30.", state.nextEventLine)
    }

    @Test
    fun `a light doing what it is told is not overridden`() {
        val state = state(on = TimeOfDay.of(18, 30), off = TimeOfDay.of(23, 0))
        assertFalse(state.isOverridden)
        assertEquals("⏱ scheduled", state.chipLabel)
    }

    @Test
    fun `an unreachable light is never described as overridden`() {
        // Nobody chose that state, so blaming the user for it would be wrong.
        val state = state(
            on = TimeOfDay.of(18, 30),
            off = TimeOfDay.of(23, 0),
            status = DeviceState.DISCONNECTED,
        )
        assertFalse(state.isOverridden)
    }

    @Test
    fun `a disabled schedule has no opinion and no override`() {
        val state = state(on = TimeOfDay.of(18, 30), off = TimeOfDay.of(23, 0), enabled = false)

        assertNull(state.scheduledState)
        assertFalse(state.isOverridden)
        assertEquals("Schedule is off. This light stays as you set it.", state.nextEventLine)
    }

    // ----------------------------------------------------------------- ring

    @Test
    fun `the ring places the window where the clock face expects it`() {
        val state = state(on = TimeOfDay.of(6, 0), off = TimeOfDay.of(18, 0))

        // 06:00 is a quarter of the way round, and the window is half the day.
        assertEquals(0.25f, state.windowStartFraction!!, 0.001f)
        assertEquals(0.5f, state.windowSweepFraction!!, 0.001f)
    }

    @Test
    fun `the ring speaks a whole sentence`() {
        val state = state(on = TimeOfDay.of(18, 30), off = TimeOfDay.of(23, 0))
        assertEquals(
            "Scheduled on from 18:30 to 23:00. Currently on. Turns off in 3h 15m",
            state.ringSpoken,
        )
    }

    // ------------------------------------------------------------- metadata

    @Test
    fun `the footer names who changed it`() {
        assertEquals(
            "Last changed 18:30 by schedule",
            state(
                on = TimeOfDay.of(18, 30),
                off = TimeOfDay.of(23, 0),
                changedBy = ChangeSource.WORKER,
                changedAt = now - 75 * Minute,
            ).footerLine,
        )
        assertEquals(
            "Last changed 18:30 by you",
            state(
                on = TimeOfDay.of(18, 30),
                off = TimeOfDay.of(23, 0),
                changedBy = ChangeSource.APP,
                changedAt = now - 75 * Minute,
            ).footerLine,
        )
    }

    @Test
    fun `a home in another zone earns the caption`() {
        val state = buildScheduleSheetState(
            device = Live(device(), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
            phoneZone = ZoneId.of("Europe/London"),
        )

        assertEquals("Times are in Asia/Colombo.", state.timezoneCaption)
    }

    @Test
    fun `the caption stays away when the zones agree`() {
        val state = buildScheduleSheetState(
            device = Live(device(), isFromServer = true),
            floor = floor(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
            phoneZone = zone,
        )

        assertNull(state.timezoneCaption)
    }

    @Test
    fun `runs that began at the scheduled edge are credited to the schedule`() {
        // Colombo is UTC+5:30, so 18:30 local on this day is 13:00 UTC.
        val dayStart = now - 19 * Hour - 45 * Minute
        val scheduled = dayStart + 18 * Hour + 30 * Minute
        val byHand = dayStart + 9 * Hour

        val state = buildScheduleSheetState(
            device = Live(device(), isFromServer = true),
            floor = floor(),
            events = listOf(
                event(scheduled + 40_000L),
                event(byHand),
            ),
            nowMillis = now,
            zone = zone,
        )

        assertEquals(1, state.scheduleRunsToday)
    }

    // ------------------------------------------------------------ fixtures

    private fun state(
        on: TimeOfDay?,
        off: TimeOfDay?,
        enabled: Boolean = true,
        status: DeviceState = DeviceState.ON,
        nowMillis: Long = now,
        changedBy: ChangeSource? = null,
        changedAt: Long? = null,
    ) = buildScheduleSheetState(
        device = Live(
            device(on = on, off = off, enabled = enabled, status = status, changedBy = changedBy, changedAt = changedAt),
            isFromServer = true,
        ),
        floor = floor(),
        events = emptyList(),
        nowMillis = nowMillis,
        zone = zone,
        phoneZone = zone,
    )

    private fun device(
        on: TimeOfDay? = TimeOfDay.of(18, 30),
        off: TimeOfDay? = TimeOfDay.of(23, 0),
        enabled: Boolean = true,
        status: DeviceState = DeviceState.ON,
        changedBy: ChangeSource? = null,
        changedAt: Long? = null,
    ) = Device(
        id = "d1",
        ownerUid = "u1",
        floorId = "ground",
        type = DeviceType.LIGHT,
        name = "Porch Light",
        gridX = 1,
        gridY = 0,
        status = status,
        turnedOnAt = null,
        lastChangedAt = changedAt?.let { Timestamp(Date(it)) },
        lastChangedBy = changedBy,
        config = DeviceConfig.Light(
            scheduleEnabled = enabled && on != null && off != null,
            scheduleOn = on,
            scheduleOff = off,
        ),
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

    private fun event(startMillis: Long) = UsageEvent(
        id = "e$startMillis",
        ownerUid = "u1",
        deviceId = "d1",
        channelId = null,
        startedAt = Timestamp(Date(startMillis)),
        endedAt = Timestamp(Date(startMillis + Hour)),
        durationSeconds = 3600,
    )

    private companion object {
        const val Hour = 60L * 60L * 1000L
        const val Minute = 60L * 1000L
    }
}
