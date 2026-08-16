package com.smarthome.control.ui.camera

import com.google.firebase.Timestamp
import com.smarthome.control.data.Live
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
 * The camera view's honesty rule.
 *
 * Almost every test here is the same assertion in a different costume: the badge describes
 * what is on screen, never what the device document offers. That is the one thing on this
 * screen an examiner can catch at a glance, and the one thing worth pinning down in tests.
 */
class CameraStateTest {

    private val now = 1_755_267_300_000L

    @Test
    fun `a playing stream is the only thing allowed to say LIVE`() {
        val presentation = cameraPresentation(
            status = DeviceState.OFF,
            phase = PlaybackPhase.Playing,
            hasStream = true,
            hasSnapshot = true,
        )

        assertEquals(CameraBadge.Live, presentation.badge)
        assertTrue(presentation.showsPlayer)
        assertFalse(presentation.showsSnapshot)
    }

    @Test
    fun `a still image says SNAPSHOT even when a stream URI exists`() {
        // The URI is a claim; the phase is the evidence. A stream that failed to start
        // leaves a snapshot on screen, and calling that LIVE is the lie this screen is
        // built to avoid.
        val presentation = cameraPresentation(
            status = DeviceState.OFF,
            phase = PlaybackPhase.Failed,
            hasStream = true,
            hasSnapshot = true,
        )

        assertEquals(CameraBadge.Snapshot, presentation.badge)
        assertTrue(presentation.showsSnapshot)
        assertFalse(presentation.showsPlayer)
    }

    @Test
    fun `connecting shows no badge at all`() {
        val presentation = cameraPresentation(
            status = DeviceState.OFF,
            phase = PlaybackPhase.Connecting,
            hasStream = true,
            hasSnapshot = false,
        )

        // There is nothing on screen yet, so there is nothing to describe. A badge here
        // would be describing an intention.
        assertNull(presentation.badge)
        assertEquals("Connecting…", presentation.statusLine)
    }

    @Test
    fun `a stalled stream keeps its last frame rather than blanking`() {
        val presentation = cameraPresentation(
            status = DeviceState.OFF,
            phase = PlaybackPhase.Stalled,
            hasStream = true,
            hasSnapshot = false,
        )

        assertTrue(presentation.holdsLastFrame)
        assertTrue(presentation.showsPlayer)
        assertEquals("Reconnecting…", presentation.statusLine)
    }

    @Test
    fun `the document's own DISCONNECTED outranks a player that thinks it is fine`() {
        val presentation = cameraPresentation(
            status = DeviceState.DISCONNECTED,
            phase = PlaybackPhase.Playing,
            hasStream = true,
            hasSnapshot = true,
        )

        assertEquals(CameraBadge.Offline, presentation.badge)
        assertFalse(presentation.showsPlayer)
        assertEquals("Can't reach this camera", presentation.statusLine)
    }

    @Test
    fun `a camera with no sources at all is offline, not empty`() {
        val presentation = cameraPresentation(
            status = DeviceState.OFF,
            phase = PlaybackPhase.Failed,
            hasStream = false,
            hasSnapshot = false,
        )

        assertEquals(CameraBadge.Offline, presentation.badge)
    }

    @Test
    fun `a snapshot-only camera never pretends to be connecting`() {
        val presentation = cameraPresentation(
            status = DeviceState.OFF,
            phase = PlaybackPhase.Connecting,
            hasStream = false,
            hasSnapshot = true,
        )

        assertEquals(CameraBadge.Snapshot, presentation.badge)
    }

    // ------------------------------------------------------------------ copy

    @Test
    fun `snapshot age is spelled out, not rounded to zero`() {
        assertEquals("Updated 8 seconds ago", snapshotAgeLine(now - 8_000L, now))
        assertEquals("Updated 12 minutes ago", snapshotAgeLine(now - 12 * 60_000L, now))
        assertEquals("Updated 1 minute ago", snapshotAgeLine(now - 60_000L, now))
        assertEquals("Updated a moment ago", snapshotAgeLine(now - 400L, now))
    }

    @Test
    fun `the spoken viewport names the source it is actually showing`() {
        assertEquals(
            "Front Door camera, Ground Floor, live",
            cameraSpoken("Front Door", "Ground Floor", CameraBadge.Live, null),
        )
        assertEquals(
            "Front Door camera, Ground Floor, snapshot updated 8 seconds ago",
            cameraSpoken("Front Door", "Ground Floor", CameraBadge.Snapshot, "Updated 8 seconds ago"),
        )
        assertEquals(
            "Front Door camera, Ground Floor, unreachable",
            cameraSpoken("Front Door", "Ground Floor", CameraBadge.Offline, null),
        )
    }

    // ------------------------------------------------------------- derivation

    @Test
    fun `the state carries both URIs and the location`() {
        val state = buildCameraState(
            device = Live(camera("c1", "Front Door", stream = "https://x/live.m3u8"), isFromServer = true),
            floors = floors(),
            cameras = listOf(camera("c1", "Front Door")),
            nowMillis = now,
        )

        assertEquals("Ground Floor · R1 C4", state.locationLine)
        assertTrue(state.hasStream)
        assertTrue(state.hasSnapshot)
    }

    @Test
    fun `a blank URI is no URI`() {
        // The contract lets a camera carry empty strings, and "" is not a source.
        val state = buildCameraState(
            device = Live(camera("c1", "Front Door", stream = "", snapshot = ""), isFromServer = true),
            floors = floors(),
            cameras = emptyList(),
            nowMillis = now,
        )

        assertFalse(state.hasStream)
        assertFalse(state.hasSnapshot)
    }

    @Test
    fun `the strip excludes the camera you are already looking at`() {
        val state = buildCameraState(
            device = Live(camera("c1", "Front Door"), isFromServer = true),
            floors = floors(),
            cameras = listOf(camera("c1", "Front Door"), camera("c2", "Garage"), camera("c3", "Hall")),
            nowMillis = now,
        )

        assertEquals(listOf("Garage", "Hall"), state.otherCameras.map { it.name })
    }

    @Test
    fun `an unreachable camera is marked rather than hidden`() {
        val state = buildCameraState(
            device = Live(camera("c1", "Front Door"), isFromServer = true),
            floors = floors(),
            cameras = listOf(
                camera("c1", "Front Door"),
                camera("c2", "Garage", status = DeviceState.DISCONNECTED),
            ),
            nowMillis = now,
        )

        assertFalse(state.otherCameras.single().isReachable)
    }

    @Test
    fun `last seen reads in the largest unit that fits`() {
        val state = buildCameraState(
            device = Live(camera("c1", "Front Door", changedAt = now - 12 * 60_000L), isFromServer = true),
            floors = floors(),
            cameras = emptyList(),
            nowMillis = now,
        )

        assertEquals("Last seen 12 minutes ago", state.lastSeenLine)
    }

    @Test
    fun `a deleted camera closes the screen`() {
        val state = buildCameraState(device = null, floors = floors(), cameras = emptyList(), nowMillis = now)
        assertTrue(state.isMissing)
    }

    // ------------------------------------------------------------------ wall

    @Test
    fun `the wall groups by floor and skips floors with no cameras`() {
        val sections = buildCameraWall(
            cameras = listOf(
                camera("c1", "Front Door", floorId = "ground"),
                camera("c2", "Landing", floorId = "first"),
                camera("c3", "Garage", floorId = "ground"),
            ),
            floors = floors() + Floor("attic", "u1", "Attic", null, 6, 10, null),
        )

        assertEquals(listOf("Ground Floor", "First Floor"), sections.map { it.floorName })
        assertEquals(listOf("Front Door", "Garage"), sections.first().cameras.map { it.name })
    }

    @Test
    fun `a home with no cameras has no sections`() {
        assertTrue(buildCameraWall(cameras = emptyList(), floors = floors()).isEmpty())
    }

    private fun floors() = listOf(
        Floor("ground", "u1", "Ground Floor", null, 6, 10, null),
        Floor("first", "u1", "First Floor", null, 6, 10, null),
    )

    private fun camera(
        id: String,
        name: String,
        floorId: String = "ground",
        status: DeviceState = DeviceState.OFF,
        stream: String = "https://example/live.m3u8",
        snapshot: String = "https://example/still.jpg",
        changedAt: Long? = null,
    ) = Device(
        id = id,
        ownerUid = "u1",
        floorId = floorId,
        type = DeviceType.CAMERA,
        name = name,
        gridX = 3,
        gridY = 0,
        status = status,
        turnedOnAt = null,
        lastChangedAt = changedAt?.let { Timestamp(Date(it)) },
        lastChangedBy = null,
        config = DeviceConfig.Camera(streamUri = stream, snapshotUri = snapshot),
    )
}
