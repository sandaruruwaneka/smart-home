package com.smarthome.control.ui.alerts

import com.google.firebase.Timestamp
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.data.model.Floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Date

/**
 * The alerts screen's derivation.
 *
 * The grouping and the two empty states get the most attention. Grouping because a day
 * boundary drawn in the wrong zone puts last night's cutoff under the wrong header, and the
 * empty states because telling "nothing matched" apart from "nothing ever happened" is the
 * difference between a working filter and a screen that looks broken.
 */
class AlertsStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Colombo")

    /** 2026-08-15, 19:45 in Colombo. */
    private val now = 1_755_267_300_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun `alerts are grouped under the day they landed on`() {
        val state = build(
            listOf(
                alert("a1", at = now - hour),
                alert("a2", at = now - 26 * hour),
                alert("a3", at = now - 72 * hour),
            ),
        )

        assertEquals(listOf("TODAY", "YESTERDAY", "12 AUGUST"), state.sections.map { it.header })
    }

    @Test
    fun `a day boundary is drawn in the home timezone, not UTC`() {
        // 00:30 Colombo is still 19:00 the previous day in UTC. Grouping on the raw epoch
        // would file this morning's cutoff under yesterday.
        val justAfterMidnight = now - 19 * hour - 15 * 60_000L + 30 * 60_000L
        val state = build(listOf(alert("a1", at = justAfterMidnight)))

        assertEquals(listOf("TODAY"), state.sections.map { it.header })
    }

    @Test
    fun `the newest day comes first and the order inside it is preserved`() {
        val state = build(
            listOf(
                alert("newest", at = now - hour),
                alert("older", at = now - 3 * hour),
                alert("yesterday", at = now - 26 * hour),
            ),
        )

        assertEquals("TODAY", state.sections.first().header)
        assertEquals(listOf("newest", "older"), state.sections.first().rows.map { it.alertId })
    }

    // ------------------------------------------------------------- filtering

    @Test
    fun `the cutoffs filter keeps only cutoffs`() {
        val alerts = listOf(
            alert("cut", type = AlertType.MAX_DURATION_EXCEEDED),
            alert("fault", type = AlertType.DEVICE_ERROR),
        )

        assertEquals(listOf("cut"), build(alerts, AlertFilter.Cutoffs).sections.flatMap { it.rows.map { r -> r.alertId } })
        assertEquals(listOf("fault"), build(alerts, AlertFilter.Faults).sections.flatMap { it.rows.map { r -> r.alertId } })
        assertEquals(2, build(alerts, AlertFilter.All).sections.sumOf { it.rows.size })
    }

    @Test
    fun `filtering everything out is not the same as having nothing`() {
        val state = build(listOf(alert("cut", type = AlertType.MAX_DURATION_EXCEEDED)), AlertFilter.Faults)

        assertTrue(state.isEmpty)
        assertTrue(state.hasAnyAlerts)
        assertEquals("No faults recorded.", state.emptyMessage)
        assertTrue(state.showsShowAllAction)
    }

    @Test
    fun `an account with no alerts says so and offers nothing to do`() {
        val state = build(emptyList())

        assertTrue(state.isEmpty)
        assertFalse(state.hasAnyAlerts)
        assertEquals(
            "No alerts yet. Devices that switch off automatically will appear here.",
            state.emptyMessage,
        )
        assertFalse(state.showsShowAllAction)
    }

    // --------------------------------------------------------------- banner

    @Test
    fun `one outstanding alert is named rather than counted`() {
        val state = build(listOf(alert("a1", acknowledged = false, device = "Bedroom Iron")))

        assertEquals("Bedroom Iron switched off automatically", state.banner?.cause)
        assertEquals(1, state.unacknowledgedCount)
    }

    @Test
    fun `a single outstanding fault says fault, not cutoff`() {
        val state = build(
            listOf(alert("a1", acknowledged = false, device = "Porch Light", type = AlertType.DEVICE_ERROR)),
        )

        assertEquals("Porch Light reported a fault", state.banner?.cause)
    }

    @Test
    fun `several outstanding alerts collapse to a count`() {
        val state = build(
            listOf(
                alert("a1", acknowledged = false),
                alert("a2", acknowledged = false),
                alert("a3", acknowledged = true),
            ),
        )

        assertEquals("2 alerts need your attention", state.banner?.cause)
        assertEquals(2, state.unacknowledgedCount)
    }

    @Test
    fun `no banner once everything is acknowledged`() {
        val state = build(listOf(alert("a1", acknowledged = true)))

        assertNull(state.banner)
        assertEquals(0, state.unacknowledgedCount)
    }

    @Test
    fun `the banner counts alerts the filter is hiding`() {
        // The banner speaks for the whole account, not for the current view -- hiding an
        // outstanding cutoff because the user tapped `Faults` would hide the thing the
        // banner exists to raise.
        val state = build(
            listOf(alert("cut", acknowledged = false, type = AlertType.MAX_DURATION_EXCEEDED)),
            AlertFilter.Faults,
        )

        assertEquals(1, state.unacknowledgedCount)
        assertTrue(state.isEmpty)
    }

    // ----------------------------------------------------------------- rows

    @Test
    fun `a row names its floor and the time it happened`() {
        val state = build(listOf(alert("a1", at = now - 3 * hour)))
        assertEquals("First Floor · 16:45", state.sections.first().rows.first().locationLine)
    }

    @Test
    fun `inside the hour a row counts minutes instead`() {
        val state = build(listOf(alert("a1", at = now - 14 * 60_000L)))
        assertEquals("First Floor · 14 min ago", state.sections.first().rows.first().locationLine)
    }

    @Test
    fun `a floor the app cannot name leaves the time standing alone`() {
        val state = build(listOf(alert("a1", floorId = "gone", at = now - 3 * hour)))
        assertEquals("16:45", state.sections.first().rows.first().locationLine)
    }

    @Test
    fun `the spoken row leads with the word, never just the dot`() {
        val state = build(listOf(alert("a1", acknowledged = false, at = now - 3 * hour)))

        assertEquals(
            "Unacknowledged. Bedroom Iron, maximum on time reached, First Floor, 16:45. " +
                "Double tap to open device.",
            state.sections.first().rows.first().spoken,
        )
    }

    @Test
    fun `an acknowledged row does not claim to be outstanding`() {
        val state = build(listOf(alert("a1", acknowledged = true, at = now - 3 * hour)))
        assertFalse(state.sections.first().rows.first().spoken.startsWith("Unacknowledged"))
    }

    @Test
    fun `an alert with no server timestamp yet is treated as just now`() {
        // The instant between the worker's write and the server materialising its clock.
        val state = build(listOf(alert("a1", at = null)))
        assertEquals("TODAY", state.sections.first().header)
    }

    // ------------------------------------------------------------ fixtures

    private fun build(
        alerts: List<Alert>,
        filter: AlertFilter = AlertFilter.All,
        arrivals: Map<String, Long> = emptyMap(),
    ) = buildAlertsState(
        alerts = alerts,
        floors = listOf(
            Floor("first", "u1", "First Floor", null, 6, 10, null),
            Floor("ground", "u1", "Ground Floor", null, 6, 10, null),
        ),
        filter = filter,
        arrivals = arrivals,
        nowMillis = now,
        zone = zone,
    )

    private fun alert(
        id: String,
        device: String = "Bedroom Iron",
        floorId: String = "first",
        type: AlertType = AlertType.MAX_DURATION_EXCEEDED,
        acknowledged: Boolean = true,
        at: Long? = now,
    ) = Alert(
        id = id,
        ownerUid = "u1",
        deviceId = "d-$id",
        deviceName = device,
        floorId = floorId,
        type = type,
        message = if (type == AlertType.MAX_DURATION_EXCEEDED) {
            "Maximum on time reached"
        } else {
            "Device reported a fault"
        },
        createdAt = at?.let { Timestamp(Date(it)) },
        acknowledged = acknowledged,
    )
}
