package com.smarthome.control.ui.device

import com.google.firebase.Timestamp
import com.smarthome.control.data.Live
import com.smarthome.control.data.model.Channel
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
 * The multi-switch sheet's derivation.
 *
 * Most of these are about the unit-level rule, because it is the one piece of logic that has
 * to read identically here, on the marker, in the floor list's counts and in the simulator.
 * If those four disagree, the app is telling four stories about one wall plate.
 */
class MultiSwitchSheetStateTest {

    private val dayStart = 1_755_216_000_000L
    private val now = dayStart + 13 * Hour
    private val zone = ZoneOffset.UTC

    // ------------------------------------------------------- the unit's state

    @Test
    fun `the unit is on when any channel is on`() {
        val state = build(
            channel(0, DeviceState.OFF),
            channel(1, DeviceState.ON),
            channel(2, DeviceState.OFF),
        )

        assertEquals(DeviceState.ON, state.unitState)
        assertEquals("1 of 3 on", state.summaryLine)
    }

    @Test
    fun `a fault only wins when nothing is on`() {
        // The contract's clauses resolve in order: any ON beats any ERROR. A unit with one
        // lit channel and one faulted one is a unit that is on, with a fault to report
        // separately.
        val onAndFaulted = build(channel(0, DeviceState.ON), channel(1, DeviceState.ERROR))
        assertEquals(DeviceState.ON, onAndFaulted.unitState)

        val offAndFaulted = build(channel(0, DeviceState.OFF), channel(1, DeviceState.ERROR))
        assertEquals(DeviceState.ERROR, offAndFaulted.unitState)
    }

    @Test
    fun `disconnected comes from the unit, never from a channel`() {
        // No channel can report that the box it is wired into is unreachable, so the parent
        // document is the only source for this one.
        val state = build(
            channel(0, DeviceState.ON),
            deviceStatus = DeviceState.DISCONNECTED,
        )

        assertEquals(DeviceState.DISCONNECTED, state.unitState)
        assertFalse(state.isUnitReachable)
        assertFalse(state.canTurnAllOff)
    }

    @Test
    fun `the ends are phrased, not counted`() {
        val allOff = build(channel(0, DeviceState.OFF), channel(1, DeviceState.OFF))
        assertEquals("All off", allOff.summaryLine)
        assertNull(allOff.summaryHighlight)

        val allOn = build(channel(0, DeviceState.ON), channel(1, DeviceState.ON))
        assertEquals("All 2 on", allOn.summaryLine)
        assertNull(allOn.summaryHighlight)
    }

    @Test
    fun `only the numeral is highlighted, and only in the middle`() {
        val state = build(
            channel(0, DeviceState.ON),
            channel(1, DeviceState.ON),
            channel(2, DeviceState.OFF),
        )

        assertEquals("2 of 3 on", state.summaryLine)
        assertEquals("2", state.summaryHighlight)
    }

    @Test
    fun `bulk actions only offer what they would change`() {
        val allOn = build(channel(0, DeviceState.ON), channel(1, DeviceState.ON))
        assertFalse(allOn.canTurnAllOn)
        assertTrue(allOn.canTurnAllOff)

        // A faulted channel is not switchable, so a unit whose only off channel is faulted
        // offers no All on -- the button would write nothing.
        val faulted = build(channel(0, DeviceState.ON), channel(1, DeviceState.ERROR))
        assertFalse(faulted.canTurnAllOn)
        assertTrue(faulted.canTurnAllOff)
    }

    @Test
    fun `the fault line counts channels, not units`() {
        assertNull(build(channel(0, DeviceState.ON)).faultLine)

        assertEquals(
            "1 channel reported a fault.",
            build(channel(0, DeviceState.ON), channel(1, DeviceState.ERROR)).faultLine,
        )
        assertEquals(
            "2 channels reported a fault.",
            build(channel(0, DeviceState.ERROR), channel(1, DeviceState.ERROR)).faultLine,
        )
    }

    // ---------------------------------------------------------------- the rows

    @Test
    fun `rows come out in plate order, not query order`() {
        val state = buildFrom(
            listOf(
                live(rawChannel(2, "Ceiling fan", DeviceState.ON)),
                live(rawChannel(0, "Ceiling light", DeviceState.ON)),
                live(rawChannel(1, "Wall light", DeviceState.OFF)),
            ),
        )

        assertEquals(
            listOf("Ceiling light", "Wall light", "Ceiling fan"),
            state.channels.map { it.name },
        )
    }

    @Test
    fun `an unnamed channel falls back to its plate position`() {
        val state = buildFrom(listOf(live(rawChannel(1, "", DeviceState.OFF))))
        assertEquals("Channel 2", state.channels.single().name)
    }

    @Test
    fun `the caption measures this run, not the day`() {
        // "On for" means since it was switched on. A channel switched off and on again is
        // on for the length of the second run, whatever today adds up to.
        val state = buildFrom(
            listOf(live(rawChannel(0, "Ceiling light", DeviceState.ON, onSince = now - 2 * Hour))),
        )

        assertEquals("2h 0m", state.channels.single().caption)
    }

    @Test
    fun `an off channel carries no caption`() {
        val state = buildFrom(listOf(live(rawChannel(0, "Wall light", DeviceState.OFF))))
        assertNull(state.channels.single().caption)
    }

    @Test
    fun `a row reads out as a sentence with an instruction`() {
        val state = buildFrom(
            listOf(
                live(
                    rawChannel(0, "Ceiling light", DeviceState.ON, onSince = now - (2 * Hour + 14 * Minute)),
                ),
            ),
        )

        assertEquals(
            "Ceiling light, on, on for 2 hours 14 minutes. Double tap to turn off.",
            state.channels.single().spoken,
        )
    }

    @Test
    fun `a faulted row does not invite a tap that would do nothing`() {
        val state = buildFrom(listOf(live(rawChannel(0, "Extractor", DeviceState.ERROR))))
        assertEquals("Extractor, in error.", state.channels.single().spoken)
    }

    @Test
    fun `a write in flight marks only its own row`() {
        val state = buildFrom(
            listOf(
                live(rawChannel(0, "Ceiling light", DeviceState.ON), fromServer = false),
                live(rawChannel(1, "Wall light", DeviceState.OFF)),
            ),
        )

        assertTrue(state.channels[0].pendingWrite)
        assertFalse(state.channels[1].pendingWrite)
    }

    @Test
    fun `the location line carries the gang count`() {
        val state = build(channel(0, DeviceState.ON), channel(1, DeviceState.OFF))
        assertEquals("Ground Floor · R4 C3 · 2 gang", state.locationLine)
    }

    // --------------------------------------------------------------- the usage

    @Test
    fun `combined on adds the channels up`() {
        val usage = buildMultiSwitchUsage(
            channels = listOf(row("c0", "Ceiling light"), row("c1", "Ceiling fan")),
            events = listOf(
                event("c0", dayStart + 7 * Hour, dayStart + 9 * Hour),
                event("c1", dayStart + 12 * Hour, dayStart + 13 * Hour),
            ),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        // Deliberately additive: two channels on for two hours and one hour reads three.
        assertEquals(3 * 3600L, usage?.combinedOnSeconds)
        assertEquals("3h 0m", usage?.combinedLabel)
    }

    @Test
    fun `most used names the busiest channel`() {
        val usage = buildMultiSwitchUsage(
            channels = listOf(row("c0", "Ceiling light"), row("c1", "Ceiling fan")),
            events = listOf(
                event("c0", dayStart + 7 * Hour, dayStart + 8 * Hour),
                event("c1", dayStart + 9 * Hour, dayStart + 12 * Hour),
            ),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertEquals("Ceiling fan", usage?.mostUsedChannel)
    }

    @Test
    fun `a quiet channel still gets a band`() {
        // The empty band is part of what the stack says -- "the fan ran, the light did not"
        // is only legible if the light has a row to be empty in.
        val usage = buildMultiSwitchUsage(
            channels = listOf(row("c0", "Ceiling light"), row("c1", "Ceiling fan")),
            events = listOf(event("c1", dayStart + 9 * Hour, dayStart + 10 * Hour)),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertEquals(2, usage?.bands?.size)
        assertEquals(0L, usage?.bands?.first()?.onSeconds)
        assertEquals(HoursInDay, usage?.bands?.first()?.hourFractions?.size)
    }

    @Test
    fun `a unit where nothing ran has no usage section at all`() {
        val usage = buildMultiSwitchUsage(
            channels = listOf(row("c0", "Ceiling light")),
            events = emptyList(),
            dayStartMillis = dayStart,
            nowMillis = now,
        )

        assertNull(usage)
    }

    @Test
    fun `each band reads out on its own`() {
        val band = TimelineBand("Ceiling fan", List(HoursInDay) { 0f }, 3 * 3600L + 6 * 60L)
        assertEquals("Ceiling fan, on for 3 hours 6 minutes today.", band.spoken)
    }

    @Test
    fun `a deleted unit closes the sheet`() {
        val state = buildMultiSwitchSheetState(
            device = null,
            floor = floor,
            channels = emptyList(),
            events = emptyList(),
            changedExternally = emptyMap(),
            nowMillis = now,
            zone = zone,
        )

        assertTrue(state.isMissing)
        assertFalse(state.isLoading)
    }

    // ------------------------------------------------------------- fixtures

    private val floor = Floor(
        id = "ground",
        ownerUid = "u1",
        name = "Ground Floor",
        planImageUrl = null,
        gridRows = 6,
        gridCols = 10,
        createdAt = null,
    )

    private fun device(status: DeviceState) = Device(
        id = "d1",
        ownerUid = "u1",
        floorId = "ground",
        type = DeviceType.MULTI_SWITCH,
        name = "Living Room Switch",
        gridX = 2,
        gridY = 3,
        status = status,
        turnedOnAt = null,
        lastChangedAt = Timestamp(Date(now - 3 * Minute)),
        lastChangedBy = null,
        config = DeviceConfig.MultiSwitch(channelCount = 3),
    )

    private fun rawChannel(
        index: Int,
        name: String,
        status: DeviceState,
        onSince: Long? = null,
    ) = Channel(
        id = "c$index",
        index = index,
        name = name,
        status = status,
        turnedOnAt = onSince?.let { Timestamp(Date(it)) },
        lastChangedAt = null,
    )

    private fun channel(index: Int, status: DeviceState) =
        live(rawChannel(index, "Channel ${index + 1}", status))

    private fun live(channel: Channel, fromServer: Boolean = true) = Live(channel, fromServer)

    private fun build(
        vararg channels: Live<Channel>,
        deviceStatus: DeviceState = DeviceState.ON,
    ) = buildMultiSwitchSheetState(
        device = Live(device(deviceStatus), isFromServer = true),
        floor = floor,
        channels = channels.toList(),
        events = emptyList(),
        changedExternally = emptyMap(),
        nowMillis = now,
        zone = zone,
    )

    private fun buildFrom(channels: List<Live<Channel>>) = buildMultiSwitchSheetState(
        device = Live(device(DeviceState.ON), isFromServer = true),
        floor = floor,
        channels = channels,
        events = emptyList(),
        changedExternally = emptyMap(),
        nowMillis = now,
        zone = zone,
    )

    private fun row(id: String, name: String) = ChannelUiState(
        id = id,
        index = 0,
        name = name,
        state = DeviceState.OFF,
    )

    private fun event(channelId: String, from: Long, to: Long) = UsageEvent(
        id = "e$channelId$from",
        ownerUid = "u1",
        deviceId = "d1",
        channelId = channelId,
        startedAt = Timestamp(Date(from)),
        endedAt = Timestamp(Date(to)),
        durationSeconds = ((to - from) / 1000L).toInt(),
    )

    private companion object {
        const val Hour = 60L * 60L * 1000L
        const val Minute = 60L * 1000L
    }
}
