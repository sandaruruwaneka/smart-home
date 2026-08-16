package com.smarthome.control.ui.floor.edit

import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.ui.model.DeviceType

/**
 * The floor as the user is currently arranging it, before any of it is written.
 *
 * This screen is the one place in the app that does not write live. Screen prompt 05
 * section 2 gives three reasons and all three are worth keeping written down: dragging a
 * marker across a grid would otherwise fire dozens of writes per gesture, undo stops being
 * possible the moment each drag is already committed, and grid geometry is *configuration*
 * rather than device state — the brief's realtime requirement is about a light turning on,
 * not about somebody being halfway through rearranging a room.
 *
 * The draft is the whole desired end state rather than a log of operations. Diffing two
 * lists at save time is easier to be sure about than replaying an operation log, and it
 * makes undo a matter of keeping the previous draft rather than inverting each edit — an
 * inverse that has to be right for six different operations is six chances to be wrong.
 *
 * @param planImageUrl what the canvas renders and, unless [pendingUpload] is set, what gets
 *   written to the floor document.
 * @param pendingUpload a `content://` URI the user picked from their photos, which has to
 *   reach Storage before the floor can point at it. Held separately so the preview can show
 *   the local file immediately while the upload is still only an intention.
 */
data class FloorDraft(
    val name: String = "",
    val planImageUrl: String? = null,
    val pendingUpload: String? = null,
    val gridRows: Int = DefaultRows,
    val gridCols: Int = DefaultCols,
    val devices: List<DraftDevice> = emptyList(),
) {
    fun deviceAt(gridX: Int, gridY: Int): DraftDevice? =
        devices.firstOrNull { it.gridX == gridX && it.gridY == gridY }

    fun device(id: String): DraftDevice? = devices.firstOrNull { it.id == id }

    fun contains(gridX: Int, gridY: Int): Boolean =
        gridX in 0 until gridCols && gridY in 0 until gridRows

    /**
     * The devices a resize to [rows] x [cols] would strand outside the grid.
     *
     * Section 8: these are shown in `stateError` and `Apply` stays disabled until the user
     * has moved them. Clamping their coordinates would move somebody's devices without
     * telling them, and deleting them would be worse.
     */
    fun orphanedBy(rows: Int, cols: Int): List<DraftDevice> =
        devices.filter { it.gridX >= cols || it.gridY >= rows }

    /**
     * `Outlet 3` — the next unused number for a type.
     *
     * Counts what the name would collide with rather than how many of the type exist, so
     * placing three outlets, deleting the second and placing another gives `Outlet 4`
     * rather than a second `Outlet 3`.
     */
    fun defaultNameFor(type: DeviceType): String {
        val prefix = type.trayLabel
        var index = 1
        while (devices.any { it.name.equals("$prefix $index", ignoreCase = true) }) index++
        return "$prefix $index"
    }

    companion object {
        const val DefaultRows = 8
        const val DefaultCols = 10
    }
}

/**
 * One device in the draft.
 *
 * @param id a Firestore document id for a device that already exists, or a `new:` token
 *   for one that has only been placed on screen. The token is never written anywhere —
 *   [SavePlan] turns those into `placeDevice` calls, which mint the real id.
 * @param channelNames the names of a switch bank's channels, which exist only at creation:
 *   `channel_count` is immutable by contract, so a bank's channels are written once with
 *   the device and never resized.
 */
data class DraftDevice(
    val id: String,
    val type: DeviceType,
    val name: String,
    val gridX: Int,
    val gridY: Int,
    val config: DeviceConfig,
    val channelNames: List<String> = emptyList(),
) {
    val isNew: Boolean get() = id.startsWith(NewIdPrefix)

    /** `Row 2, column 5, Kitchen Outlet` — section 10. Cells are counted from one. */
    val cellDescription: String get() = "Row ${gridY + 1}, column ${gridX + 1}, $name"

    companion object {
        const val NewIdPrefix = "new:"
    }
}

/** `Row 2, column 5, empty` — the description of a cell with nothing on it. */
fun emptyCellDescription(gridX: Int, gridY: Int): String =
    "Row ${gridY + 1}, column ${gridX + 1}, empty"

/**
 * Tray labels, which are not the same strings as [DeviceType.label].
 *
 * Section 9 is specific: `Appliance` rather than `Iron` or `Hazard`, because the type covers
 * anything carrying a maximum on-time, and `Switch unit` rather than `Switch bank` because
 * that is the phrase on the tray chip.
 */
val DeviceType.trayLabel: String
    get() = when (this) {
        DeviceType.OUTLET -> "Outlet"
        DeviceType.MULTI_SWITCH -> "Switch unit"
        DeviceType.LIGHT -> "Light"
        DeviceType.APPLIANCE -> "Appliance"
        DeviceType.CAMERA -> "Camera"
    }

/**
 * Whether this type has anything to set up.
 *
 * An outlet configures nothing, so section 5 requires the `Configure` action not to render
 * at all rather than to render disabled. A disabled control is a promise that something
 * could be configured here under other circumstances, and for an outlet there are none.
 */
val DeviceType.isConfigurable: Boolean get() = this != DeviceType.OUTLET

/** The default config a freshly placed device of each type starts with. */
fun defaultConfigFor(type: DeviceType): DeviceConfig = when (type) {
    DeviceType.OUTLET -> DeviceConfig.Outlet
    DeviceType.MULTI_SWITCH -> DeviceConfig.MultiSwitch(channelCount = DefaultChannelCount)
    DeviceType.LIGHT -> DeviceConfig.Light.OFF
    DeviceType.APPLIANCE -> DeviceConfig.Appliance(maxOnDurationSeconds = DefaultMaxOnSeconds)
    DeviceType.CAMERA -> DeviceConfig.Camera(streamUri = "", snapshotUri = "")
}

// ---------------------------------------------------------------------------
// Building a draft, and turning one back into writes
// ---------------------------------------------------------------------------

fun draftOf(floor: Floor, devices: List<Device>): FloorDraft = FloorDraft(
    name = floor.name,
    planImageUrl = floor.planImageUrl,
    pendingUpload = null,
    gridRows = floor.gridRows,
    gridCols = floor.gridCols,
    devices = devices.map { device ->
        DraftDevice(
            id = device.id,
            type = device.type,
            name = device.name,
            gridX = device.gridX,
            gridY = device.gridY,
            config = device.config,
        )
    }.sortedWith(compareBy({ it.gridY }, { it.gridX })),
)

/**
 * Everything `Save` has to do, worked out before any of it is attempted.
 *
 * Deriving the writes as a value rather than performing them inline is what makes this
 * screen's most consequential moment testable: a diff that decides to delete somebody's
 * devices is worth being able to assert on without a Firestore in the room.
 *
 * The order the ViewModel runs these in is not arbitrary — see [SavePlan.isEmpty] and the
 * commit itself.
 */
data class SavePlan(
    val floorName: String? = null,
    val deletes: List<String> = emptyList(),
    val moves: List<DraftDevice> = emptyList(),
    val renames: List<DraftDevice> = emptyList(),
    val configs: List<DraftDevice> = emptyList(),
    val places: List<DraftDevice> = emptyList(),
    val grid: GridSize? = null,
    val uploadImage: String? = null,
    val clearImage: Boolean = false,
) {
    val isEmpty: Boolean
        get() = floorName == null && deletes.isEmpty() && moves.isEmpty() && renames.isEmpty() &&
            configs.isEmpty() && places.isEmpty() && grid == null && uploadImage == null &&
            !clearImage
}

data class GridSize(val rows: Int, val cols: Int)

/**
 * What changed between the floor as loaded and the floor as arranged.
 *
 * A device that was both moved and renamed appears in both lists, because they are two
 * different Firestore writes against two different sets of fields — the repository has no
 * method that does both, and inventing one to save a round trip on a screen the user
 * leaves once would be optimising the wrong thing.
 */
fun planWrites(original: FloorDraft, draft: FloorDraft): SavePlan {
    val originalById = original.devices.associateBy { it.id }
    val draftIds = draft.devices.map { it.id }.toSet()

    return SavePlan(
        floorName = draft.name.trim().takeIf { it.isNotEmpty() && it != original.name },
        deletes = original.devices.map { it.id }.filterNot { it in draftIds },
        moves = draft.devices.filter { device ->
            val before = originalById[device.id] ?: return@filter false
            before.gridX != device.gridX || before.gridY != device.gridY
        },
        renames = draft.devices.filter { device ->
            val before = originalById[device.id] ?: return@filter false
            before.name != device.name
        },
        configs = draft.devices.filter { device ->
            val before = originalById[device.id] ?: return@filter false
            before.config != device.config
        },
        places = draft.devices.filter { it.isNew },
        grid = GridSize(draft.gridRows, draft.gridCols)
            .takeIf { draft.gridRows != original.gridRows || draft.gridCols != original.gridCols },
        uploadImage = draft.pendingUpload,
        // Only a deliberate removal counts. A draft that never had an image and still has
        // none is not asking for the field to be cleared.
        clearImage = draft.pendingUpload == null &&
            draft.planImageUrl == null &&
            original.planImageUrl != null,
    )
}

// ---------------------------------------------------------------------------
// Copy that depends on state
// ---------------------------------------------------------------------------

/**
 * The one line between the canvas and the tray (section 6).
 *
 * It replaces every tooltip and coach mark this screen would otherwise need, which only
 * works if it is always saying the most useful thing available — so the cases are ordered
 * by how immediate they are. What the user is doing right now outranks what they could do
 * next.
 */
fun placementHint(
    armedType: DeviceType?,
    selectedDeviceId: String?,
    isDragging: Boolean,
    isPickingDestination: Boolean,
    deviceCount: Int,
): String = when {
    isDragging -> "Drop on an empty cell"
    isPickingDestination -> "Tap the cell to move it to"
    armedType != null -> "Tap a cell to place the ${armedType.trayLabel.lowercase()}"
    selectedDeviceId != null -> "Drag to move, or use the actions above"
    deviceCount == 0 -> "Pick a device below to start placing"
    else -> "Tap a device to edit · $deviceCount placed"
}

/**
 * `Cells will be 38 dp`, and the warning that follows it.
 *
 * Section 7 is explicit that a dense grid warns rather than blocks. The user may know
 * exactly what they are doing with a 20-column plan, and a screen that refuses to let them
 * have it has substituted its own judgement for theirs on their own floor.
 */
fun cellSizeCaption(cellSizeDp: Int): String = "Cells will be $cellSizeDp dp"

fun isCellTooSmall(cellSizeDp: Int): Boolean = cellSizeDp < MinComfortableCellDp

const val MinComfortableCellDp = 32
const val CellTooSmallWarning = "Cells this small are hard to tap. Try fewer columns."

private const val DefaultChannelCount = 3
private const val DefaultMaxOnSeconds = 1800
