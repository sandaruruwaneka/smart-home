package com.smarthome.control.ui.device

import com.google.firebase.Timestamp
import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
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
import java.time.ZoneOffset
import java.util.Date

/**
 * The hazard sheet's derivation.
 *
 * This is the safety feature's arithmetic, so the cases here are the ones where being
 * wrong would either alarm somebody who is fine or reassure somebody who is not.
 */
class HazardSheetStateTest {

    private val dayStart = 1_755_216_000_000L
    private val now = dayStart + 13 * Hour
    private val zone = ZoneOffset.UTC

    @Test
    fun `remaining counts down from the limit`() {
        val state = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - 17 * Minute)

        assertTrue(state.isCounting)
        assertEquals(17 * 60L, state.elapsedSeconds)
        assertEquals(13 * 60L, state.remainingSeconds)
    }

    @Test
    fun `the final tenth begins at exactly one tenth left`() {
        // 30 minutes means the last three. A ring that turns red early cries wolf; one that
        // turns red late is useless.
        val justOutside = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - (26 * Minute + 59 * Second))
        assertFalse(justOutside.inFinalTenth)

        val justInside = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - 27 * Minute)
        assertTrue(justInside.inFinalTenth)
    }

    @Test
    fun `expired is still on, and says so`() {
        // The worker ticks every sixty seconds, so this gap is real. Showing 00:00 against a
        // running iron would read as a feature that failed.
        val state = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - 31 * Minute)

        assertTrue(state.isExpired)
        assertEquals(0L, state.remainingSeconds)
        assertFalse(state.inFinalTenth)
    }

    @Test
    fun `a device that is off is not counting anything`() {
        val state = build(status = DeviceState.OFF, limit = 30 * 60, onSince = null)

        assertFalse(state.isCounting)
        assertFalse(state.isExpired)
        assertNull(state.remainingSeconds)
    }

    @Test
    fun `on with no start time draws no ring rather than an invented one`() {
        val state = build(status = DeviceState.ON, limit = 30 * 60, onSince = null)
        assertFalse(state.isCounting)
    }

    @Test
    fun `no limit means the device cannot be switched on`() {
        val state = build(status = DeviceState.OFF, limit = null, onSince = null)

        assertFalse(state.canSwitchOn)
        assertEquals("Set a maximum on time before using this device.", state.helperLine)
    }

    @Test
    fun `a limit is spelled out, never left as a field value`() {
        assertEquals(
            "Switches off automatically after 30 minutes.",
            build(status = DeviceState.OFF, limit = 30 * 60, onSince = null).helperLine,
        )
        assertEquals("1 hour", spellLimit(3600))
        assertEquals("1 hour 30 minutes", spellLimit(5400))
        assertEquals("1 minute", spellLimit(60))
    }

    @Test
    fun `the footer names the start and the cutoff`() {
        val state = build(status = DeviceState.ON, limit = 30 * 60, onSince = dayStart + 17 * Hour + 12 * Minute)
        assertEquals("Started 17:12 · Cuts off 17:42", state.runFooter)
    }

    @Test
    fun `the caption says what the limit is`() {
        assertEquals("of 30 minutes", build(status = DeviceState.ON, limit = 30 * 60, onSince = now).limitCaption)
    }

    // ------------------------------------------------------ shortening a limit

    @Test
    fun `shortening below what has elapsed needs confirming`() {
        assertTrue(wouldCutOffImmediately(newLimitSeconds = 15 * 60, elapsedSeconds = 18 * 60))
        // Equal counts too: the cutoff would land on the worker's very next tick.
        assertTrue(wouldCutOffImmediately(newLimitSeconds = 18 * 60, elapsedSeconds = 18 * 60))
        assertFalse(wouldCutOffImmediately(newLimitSeconds = 30 * 60, elapsedSeconds = 18 * 60))
    }

    @Test
    fun `a device that is off never triggers the confirmation`() {
        assertFalse(wouldCutOffImmediately(newLimitSeconds = 60, elapsedSeconds = null))
    }

    @Test
    fun `the warning names both numbers`() {
        assertEquals(
            "The device has already been on for 18 minutes. " +
                "Setting a 15 minutes limit will switch it off within a minute.",
            cutOffWarning(newLimitSeconds = 15 * 60, elapsedSeconds = 18 * 60),
        )
    }

    // ---------------------------------------------------------- the cutoffs

    @Test
    fun `auto cutoffs count only the cutoff alerts`() {
        val state = buildWithAlerts(
            listOf(
                alert(AlertType.MAX_DURATION_EXCEEDED, dayStart + 10 * Hour),
                alert(AlertType.MAX_DURATION_EXCEEDED, dayStart + 12 * Hour),
                // A fault is a different story, and counting it here would make the card lie.
                alert(AlertType.DEVICE_ERROR, dayStart + 11 * Hour),
            ),
        )

        assertEquals(2, state.autoCutoffsToday)
        assertEquals(setOf(10, 12), state.cutoffHours)
    }

    @Test
    fun `yesterday's cutoffs do not count towards today`() {
        val state = buildWithAlerts(listOf(alert(AlertType.MAX_DURATION_EXCEEDED, dayStart - 2 * Hour)))
        assertEquals(0, state.autoCutoffsToday)
        assertTrue(state.cutoffHours.isEmpty())
    }

    @Test
    fun `the cutoff notice shows once the device is off`() {
        val state = buildWithAlerts(
            alerts = listOf(alert(AlertType.MAX_DURATION_EXCEEDED, now - 90_000L)),
            status = DeviceState.OFF,
        )

        assertEquals("Switched off automatically — maximum on time reached", state.cutoffNotice)
    }

    @Test
    fun `the notice clears once the device is running again`() {
        // The user has switched it back on; telling them it was cut off is now history, and
        // the ring is what they opened the sheet for.
        val state = buildWithAlerts(
            alerts = listOf(alert(AlertType.MAX_DURATION_EXCEEDED, now - 90_000L)),
            status = DeviceState.ON,
        )

        assertNull(state.cutoffNotice)
    }

    // ------------------------------------------------------------ narration

    @Test
    fun `the ring reads out with both figures`() {
        val state = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - (17 * Minute + 13 * Second))
        assertEquals("12 minutes 47 seconds remaining of 30 minutes", state.ringSpoken)
    }

    @Test
    fun `crossing into the final tenth is worth announcing`() {
        val state = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - (27 * Minute + 30 * Second))
        assertEquals("Bedroom Iron switches off in under 3 minutes", state.finalTenthAnnouncement)

        val calm = build(status = DeviceState.ON, limit = 30 * 60, onSince = now - 5 * Minute)
        assertNull(calm.finalTenthAnnouncement)
    }

    @Test
    fun `a deleted device closes the sheet`() {
        val state = buildHazardSheetState(
            device = null,
            floor = floor,
            alerts = emptyList(),
            events = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertTrue(state.isMissing)
        assertFalse(state.isLoading)
    }

    // ------------------------------------------------------------- fixtures

    private val floor = Floor(
        id = "first",
        ownerUid = "u1",
        name = "First Floor",
        planImageUrl = null,
        gridRows = 6,
        gridCols = 10,
        createdAt = null,
    )

    private fun device(status: DeviceState, limit: Int?, onSince: Long?) = Device(
        id = "d1",
        ownerUid = "u1",
        floorId = "first",
        type = DeviceType.APPLIANCE,
        name = "Bedroom Iron",
        gridX = 6,
        gridY = 2,
        status = status,
        turnedOnAt = onSince?.let { Timestamp(Date(it)) },
        lastChangedAt = Timestamp(Date(now - 2 * Hour)),
        lastChangedBy = null,
        // An appliance without a limit is not constructible, so "no limit set" is modelled
        // as the config being absent -- which is what a half-placed device looks like.
        config = limit?.let { DeviceConfig.Appliance(it) } ?: DeviceConfig.Outlet,
    )

    private fun build(status: DeviceState, limit: Int?, onSince: Long?) = buildHazardSheetState(
        device = Live(device(status, limit, onSince), isFromServer = true),
        floor = floor,
        alerts = emptyList(),
        events = emptyList(),
        nowMillis = now,
        zone = zone,
    )

    private fun buildWithAlerts(
        alerts: List<Alert>,
        status: DeviceState = DeviceState.OFF,
    ) = buildHazardSheetState(
        device = Live(device(status, 30 * 60, if (status == DeviceState.ON) now - Minute else null), true),
        floor = floor,
        alerts = alerts,
        events = emptyList(),
        nowMillis = now,
        zone = zone,
    )

    private fun alert(type: AlertType, atMillis: Long) = Alert(
        id = "a$atMillis",
        ownerUid = "u1",
        deviceId = "d1",
        floorId = "first",
        deviceName = "Bedroom Iron",
        type = type,
        message = "Maximum active time exceeded",
        createdAt = Timestamp(Date(atMillis)),
        acknowledged = false,
    )

    private companion object {
        const val Hour = 60L * 60L * 1000L
        const val Minute = 60L * 1000L
        const val Second = 1000L
    }
}
