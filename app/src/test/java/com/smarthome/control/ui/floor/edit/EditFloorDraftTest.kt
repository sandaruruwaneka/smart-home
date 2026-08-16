package com.smarthome.control.ui.floor.edit

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

/**
 * The staged draft and the writes it turns into.
 *
 * The save plan gets the most attention here because it is the one piece of this screen
 * that can destroy something: a diff that decides wrongly deletes devices and their whole
 * usage history. Being able to assert on it without a Firestore in the room is the reason
 * it is a value rather than a sequence of calls.
 */
class EditFloorDraftTest {

    private val floor = Floor(
        id = "ground",
        ownerUid = "u1",
        name = "Ground Floor",
        planImageUrl = null,
        gridRows = 6,
        gridCols = 10,
        createdAt = null,
    )

    @Test
    fun `a freshly loaded draft has nothing to write`() {
        val draft = draftOf(floor, listOf(device("d1", 1, 1)))
        assertTrue(planWrites(draft, draft).isEmpty)
    }

    @Test
    fun `moving a device plans a move and nothing else`() {
        val original = draftOf(floor, listOf(device("d1", 1, 1)))
        val moved = original.copy(
            devices = original.devices.map { it.copy(gridX = 4, gridY = 2) },
        )

        val plan = planWrites(original, moved)

        assertEquals(listOf("d1"), plan.moves.map { it.id })
        assertTrue(plan.renames.isEmpty())
        assertTrue(plan.places.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `a device both moved and renamed lands in both lists`() {
        // They are two writes against two different sets of fields, and no repository
        // method does both.
        val original = draftOf(floor, listOf(device("d1", 1, 1)))
        val edited = original.copy(
            devices = original.devices.map { it.copy(gridX = 3, name = "Hall lamp") },
        )

        val plan = planWrites(original, edited)

        assertEquals(listOf("d1"), plan.moves.map { it.id })
        assertEquals(listOf("d1"), plan.renames.map { it.id })
    }

    @Test
    fun `a device that left the draft is planned for deletion`() {
        val original = draftOf(floor, listOf(device("d1", 1, 1), device("d2", 2, 2)))
        val edited = original.copy(devices = original.devices.filter { it.id == "d1" })

        assertEquals(listOf("d2"), planWrites(original, edited).deletes)
    }

    @Test
    fun `a newly placed device is planned as a placement, never as an update`() {
        val original = draftOf(floor, emptyList())
        val edited = original.copy(
            devices = listOf(
                DraftDevice("${DraftDevice.NewIdPrefix}1", DeviceType.OUTLET, "Outlet 1", 0, 0, DeviceConfig.Outlet),
            ),
        )

        val plan = planWrites(original, edited)

        assertEquals(1, plan.places.size)
        assertTrue(plan.moves.isEmpty())
        assertTrue(plan.renames.isEmpty())
    }

    @Test
    fun `an unchanged name is not a rename`() {
        val original = draftOf(floor, listOf(device("d1", 1, 1)))
        assertNull(planWrites(original, original.copy(name = floor.name)).floorName)
    }

    @Test
    fun `clearing an image the floor never had is not a change`() {
        val original = draftOf(floor, emptyList())
        assertFalse(planWrites(original, original).clearImage)
    }

    @Test
    fun `removing an existing image plans the clear`() {
        val withImage = draftOf(floor.copy(planImageUrl = "https://example/plan.jpg"), emptyList())
        assertTrue(planWrites(withImage, withImage.copy(planImageUrl = null)).clearImage)
    }

    @Test
    fun `an edit and its undo leave nothing to save`() {
        // Dirtiness is the difference between the two drafts, so it has to survive a round
        // trip: an undone edit must leave the amber dot off, because there is no write.
        val original = draftOf(floor, listOf(device("d1", 1, 1)))
        val moved = original.copy(devices = original.devices.map { it.copy(gridX = 5) })

        assertFalse(planWrites(original, moved).isEmpty)
        assertTrue(planWrites(original, original).isEmpty)
    }

    // -------------------------------------------------------------- the grid

    @Test
    fun `shrinking the grid names the devices it would strand`() {
        val draft = draftOf(floor, listOf(device("d1", 1, 1), device("d2", 8, 2), device("d3", 3, 5)))

        val orphaned = draft.orphanedBy(rows = 4, cols = 6)

        assertEquals(setOf("d2", "d3"), orphaned.map { it.id }.toSet())
    }

    @Test
    fun `growing the grid strands nobody`() {
        val draft = draftOf(floor, listOf(device("d1", 9, 5)))
        assertTrue(draft.orphanedBy(rows = 20, cols = 20).isEmpty())
    }

    @Test
    fun `a device on the last row and column is inside the grid`() {
        // Bounds are exclusive on both axes, so a 6 x 10 grid holds row 5 and column 9.
        val draft = draftOf(floor, listOf(device("d1", 9, 5)))
        assertTrue(draft.orphanedBy(rows = 6, cols = 10).isEmpty())
        assertTrue(draft.contains(9, 5))
        assertFalse(draft.contains(10, 5))
    }

    // ------------------------------------------------------------- placement

    @Test
    fun `the default name skips numbers already in use`() {
        val draft = draftOf(floor, emptyList()).copy(
            devices = listOf(
                DraftDevice("d1", DeviceType.OUTLET, "Outlet 1", 0, 0, DeviceConfig.Outlet),
                DraftDevice("d2", DeviceType.OUTLET, "Outlet 2", 1, 0, DeviceConfig.Outlet),
            ),
        )

        assertEquals("Outlet 3", draft.defaultNameFor(DeviceType.OUTLET))
        // Types are numbered independently -- the first light is Light 1 even beside two
        // outlets.
        assertEquals("Light 1", draft.defaultNameFor(DeviceType.LIGHT))
    }

    @Test
    fun `a gap left by a deletion is not reused`() {
        // Placing three, deleting the second and placing another gives Outlet 4. Reusing
        // the gap would put a second Outlet 3 in the history of a floor that already had
        // one.
        val draft = draftOf(floor, emptyList()).copy(
            devices = listOf(
                DraftDevice("d1", DeviceType.OUTLET, "Outlet 1", 0, 0, DeviceConfig.Outlet),
                DraftDevice("d3", DeviceType.OUTLET, "Outlet 3", 2, 0, DeviceConfig.Outlet),
            ),
        )

        assertEquals("Outlet 2", draft.defaultNameFor(DeviceType.OUTLET))
    }

    @Test
    fun `an occupied cell reports its occupant`() {
        val draft = draftOf(floor, listOf(device("d1", 4, 2)))

        assertEquals("d1", draft.deviceAt(4, 2)?.id)
        assertNull(draft.deviceAt(4, 3))
    }

    // ----------------------------------------------------------------- copy

    @Test
    fun `the hint says what the current gesture will do`() {
        assertEquals(
            "Drop on an empty cell",
            placementHint(DeviceType.OUTLET, "d1", isDragging = true, isPickingDestination = false, deviceCount = 4),
        )
        assertEquals(
            "Tap a cell to place the outlet",
            placementHint(DeviceType.OUTLET, null, isDragging = false, isPickingDestination = false, deviceCount = 4),
        )
        assertEquals(
            "Drag to move, or use the actions above",
            placementHint(null, "d1", isDragging = false, isPickingDestination = false, deviceCount = 4),
        )
        assertEquals(
            "Pick a device below to start placing",
            placementHint(null, null, isDragging = false, isPickingDestination = false, deviceCount = 0),
        )
        assertEquals(
            "Tap a device to edit · 4 placed",
            placementHint(null, null, isDragging = false, isPickingDestination = false, deviceCount = 4),
        )
    }

    @Test
    fun `the tray says appliance, not iron`() {
        // Section 9: the type covers anything with a maximum on-time, so naming it after
        // one appliance would be naming it after the demo.
        assertEquals("Appliance", DeviceType.APPLIANCE.trayLabel)
        assertEquals("Switch unit", DeviceType.MULTI_SWITCH.trayLabel)
    }

    @Test
    fun `an outlet has nothing to configure`() {
        assertFalse(DeviceType.OUTLET.isConfigurable)
        assertTrue(DeviceType.APPLIANCE.isConfigurable)
    }

    @Test
    fun `a cramped grid warns without being wrong about the number`() {
        assertEquals("Cells will be 38 dp", cellSizeCaption(38))
        assertFalse(isCellTooSmall(38))
        assertTrue(isCellTooSmall(31))
    }

    @Test
    fun `cells are described from one, for both states`() {
        val draft = draftOf(floor, listOf(device("d1", 4, 1)))

        assertEquals("Row 2, column 5, Kitchen Outlet", draft.deviceAt(4, 1)?.cellDescription)
        assertEquals("Row 3, column 1, empty", emptyCellDescription(0, 2))
    }

    private fun device(id: String, gridX: Int, gridY: Int) = Device(
        id = id,
        ownerUid = "u1",
        floorId = "ground",
        type = DeviceType.OUTLET,
        name = "Kitchen Outlet",
        gridX = gridX,
        gridY = gridY,
        status = DeviceState.OFF,
        turnedOnAt = null,
        lastChangedAt = null,
        lastChangedBy = null,
        config = DeviceConfig.Outlet,
    )
}
