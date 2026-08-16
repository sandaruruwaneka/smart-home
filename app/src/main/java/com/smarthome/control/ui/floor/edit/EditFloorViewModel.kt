package com.smarthome.control.ui.floor.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UserRepository
import com.smarthome.control.ui.model.DeviceType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the floor editor, in both of its modes.
 *
 * ### The floor is read once, not observed
 *
 * Every other screen in the app holds its listeners open. This one takes a single snapshot
 * and works on a copy, because a live listener would fight the user: a device status
 * arriving from the simulator would rebuild the state under a half-finished drag, and a
 * grid change from another phone would move markers the user is in the middle of placing.
 * The staged draft *is* the screen's state, and Firestore does not get a vote on it until
 * `Save`.
 *
 * The cost is a lost-update window — two people editing one floor plan at once, last save
 * wins. For a single-home app whose floor plan changes a handful of times ever, that is the
 * right trade against a canvas that rearranges itself while you use it.
 *
 * ### Undo keeps drafts, not inverses
 *
 * Ten previous drafts, pushed before each edit. An undo stack of inverse operations needs a
 * correct inverse for placing, moving, renaming, deleting, configuring and resizing — six
 * chances to get it subtly wrong — while a stack of whole drafts is right by construction
 * and costs a list of five or six small objects per step.
 */
class EditFloorViewModel(
    /** Null in create mode, until [finishCreate] mints one. */
    private var floorId: String?,
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditFloorUiState(
            mode = if (floorId == null) EditorMode.Create(CreateStep.Name) else EditorMode.Place,
            isLoading = floorId != null,
        ),
    )
    val state: StateFlow<EditFloorUiState> = _state.asStateFlow()

    /** Undo announcements (section 10) and nothing else. Events, not state. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val undoStack = ArrayDeque<Pair<String, FloorDraft>>()

    /** The floor document as loaded, so writes can be bounds-checked against real geometry. */
    private var loadedFloor: Floor? = null
    private var ownerUid: String? = null

    init {
        if (floorId != null) load()
    }

    fun load() {
        val floorId = floorId ?: return
        val users = users
        val floors = floors
        val devices = devices

        if (users == null || floors == null || devices == null) {
            _state.update { it.copy(isLoading = false, loadError = FirebaseMissing) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }

            runCatching {
                val uid = users.observeAuthState().first() ?: error("signed out")
                ownerUid = uid
                val floor = floors.getFloor(floorId) ?: error("no such floor")
                loadedFloor = floor
                // One emission of the device listener, then nothing more. See the class
                // comment: this screen owns its state until Save.
                val onFloor = devices.observeDevicesOnFloor(uid, floorId).first().map { it.value }
                draftOf(floor, onFloor)
            }.onSuccess { draft ->
                undoStack.clear()
                _state.update {
                    it.copy(
                        isLoading = false,
                        mode = EditorMode.Place,
                        draft = draft,
                        original = draft,
                        undoDepth = 0,
                    )
                }
            }.onFailure { failure ->
                _state.update { it.copy(isLoading = false, loadError = failure.userMessage()) }
            }
        }
    }

    // ------------------------------------------------------------ create mode

    fun setFloorName(name: String) = _state.update { it.copy(draft = it.draft.copy(name = name)) }

    /** A bundled sample plan, or a photo the user picked. Both render; only one uploads. */
    fun chooseSamplePlan(resourceUri: String) = _state.update {
        it.copy(draft = it.draft.copy(planImageUrl = resourceUri, pendingUpload = null))
    }

    fun choosePickedImage(uri: String) = _state.update {
        it.copy(draft = it.draft.copy(planImageUrl = uri, pendingUpload = uri))
    }

    fun clearPlanImage() = _state.update {
        it.copy(draft = it.draft.copy(planImageUrl = null, pendingUpload = null))
    }

    fun setGrid(rows: Int, cols: Int) {
        val clampedRows = rows.coerceIn(Floor.GRID_RANGE)
        val clampedCols = cols.coerceIn(Floor.GRID_RANGE)
        val current = _state.value

        if (current.mode is EditorMode.Create) {
            // Nothing is placed yet, so there is nothing to orphan and nothing to undo.
            _state.update {
                it.copy(draft = it.draft.copy(gridRows = clampedRows, gridCols = clampedCols))
            }
            return
        }

        // Section 8: a resize that would strand a device is refused here as well as in the
        // dialog, so the guard does not depend on the dialog's button being right.
        if (current.draft.orphanedBy(clampedRows, clampedCols).isNotEmpty()) return
        edit("changed the grid") { it.copy(gridRows = clampedRows, gridCols = clampedCols) }
    }

    fun goToStep(step: CreateStep) = _state.update {
        if (it.mode is EditorMode.Create) it.copy(mode = EditorMode.Create(step)) else it
    }

    /**
     * Creates the floor and drops the user into placement mode.
     *
     * This is the one write create mode makes before `Save` exists, and it has to be: a
     * device cannot be placed on a floor that has no id. Everything after it is staged like
     * any other edit.
     */
    fun finishCreate() {
        val users = users
        val floors = floors
        if (users == null || floors == null) {
            _state.update { it.copy(saveError = FirebaseMissing) }
            return
        }

        val draft = _state.value.draft
        if (draft.name.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }

            runCatching {
                val uid = users.observeAuthState().first() ?: error("signed out")
                ownerUid = uid
                val id = floors.createFloor(uid, draft.name, draft.gridRows, draft.gridCols)
                floorId = id
                applyPlanImage(floors, uid, id, draft)
                floors.getFloor(id) ?: error("no such floor")
            }.onSuccess { floor ->
                loadedFloor = floor
                val committed = draftOf(floor, emptyList())
                undoStack.clear()
                _state.update {
                    it.copy(
                        isSaving = false,
                        mode = EditorMode.Place,
                        draft = committed,
                        original = committed,
                        undoDepth = 0,
                    )
                }
            }.onFailure {
                _state.update { it.copy(isSaving = false, saveError = SaveFailed) }
            }
        }
    }

    // --------------------------------------------------------- placement mode

    fun arm(type: DeviceType) = _state.update {
        // Arming and selecting are mutually exclusive: the action bar and the tray both
        // claim the space above the tray, and both showing at once is two answers to "what
        // does a tap on a cell do now".
        it.copy(
            armedType = if (it.armedType == type) null else type,
            selectedDeviceId = null,
            isPickingDestination = false,
        )
    }

    fun select(deviceId: String?) = _state.update {
        it.copy(selectedDeviceId = deviceId, armedType = null, isPickingDestination = false)
    }

    fun startPickingDestination() = _state.update {
        if (it.selectedDeviceId == null) it else it.copy(isPickingDestination = true)
    }

    fun cancelPickingDestination() = _state.update { it.copy(isPickingDestination = false) }

    /**
     * What a tap on a cell means, which depends entirely on what the user is doing.
     *
     * Every branch is one of the five rows in section 5's gesture table, kept in one place
     * so the hint strip and the canvas cannot disagree about what a tap will do.
     */
    fun tapCell(gridX: Int, gridY: Int) {
        val current = _state.value
        if (!current.draft.contains(gridX, gridY)) return
        val occupant = current.draft.deviceAt(gridX, gridY)

        when {
            current.isPickingDestination -> {
                val selected = current.selectedDeviceId ?: return
                if (occupant != null) return
                moveDevice(selected, gridX, gridY)
                _state.update { it.copy(isPickingDestination = false) }
            }

            occupant != null -> select(occupant.id)

            current.armedType != null -> place(current.armedType, gridX, gridY)

            else -> select(null)
        }
    }

    fun place(type: DeviceType, gridX: Int, gridY: Int) {
        val current = _state.value.draft
        if (!current.contains(gridX, gridY) || current.deviceAt(gridX, gridY) != null) return

        val name = current.defaultNameFor(type)
        val device = DraftDevice(
            id = "${DraftDevice.NewIdPrefix}${nextNewId++}",
            type = type,
            name = name,
            gridX = gridX,
            gridY = gridY,
            config = defaultConfigFor(type),
        )

        edit("placed $name") { it.copy(devices = it.devices + device) }
        // Stays armed -- placing five outlets in a row should cost five taps, not ten --
        // and selects, so the action bar's Rename is one tap from the default name.
        //
        // Section 5 asks for an inline name field on placement instead. It is not here on
        // purpose: a field that writes as you type would push an undo step per keystroke,
        // and the same section asks for rapid repeated placement in the very next clause.
        // Renaming stays one deliberate edit, which is one undo step.
        _state.update { it.copy(selectedDeviceId = device.id) }
    }

    /** Returns false when the destination is occupied or off the grid, so the canvas can flash it. */
    fun moveDevice(deviceId: String, gridX: Int, gridY: Int): Boolean {
        val draft = _state.value.draft
        val device = draft.device(deviceId) ?: return false
        if (!draft.contains(gridX, gridY)) return false
        // Section 5: a drop on an occupied cell is rejected outright. Swapping the two
        // devices would be a second, invisible edit the user never asked for, and stacking
        // them would put two markers in a cell the grid says holds one.
        val occupant = draft.deviceAt(gridX, gridY)
        if (occupant != null && occupant.id != deviceId) return false
        if (device.gridX == gridX && device.gridY == gridY) return true

        edit("moved ${device.name}") { current ->
            current.copy(
                devices = current.devices.map {
                    if (it.id == deviceId) it.copy(gridX = gridX, gridY = gridY) else it
                },
            )
        }
        return true
    }

    fun renameDevice(deviceId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val device = _state.value.draft.device(deviceId) ?: return
        if (device.name == trimmed) return

        edit("renamed ${device.name}") { current ->
            current.copy(
                devices = current.devices.map { if (it.id == deviceId) it.copy(name = trimmed) else it },
            )
        }
    }

    fun configureDevice(deviceId: String, config: DeviceConfig, channelNames: List<String> = emptyList()) {
        val device = _state.value.draft.device(deviceId) ?: return
        require(config.deviceType == device.type) { "config is for ${config.deviceType}" }

        edit("configured ${device.name}") { current ->
            current.copy(
                devices = current.devices.map {
                    if (it.id == deviceId) {
                        it.copy(
                            config = config,
                            channelNames = if (it.isNew) channelNames else it.channelNames,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun deleteDevice(deviceId: String) {
        val device = _state.value.draft.device(deviceId) ?: return
        edit("deleted ${device.name}") { current ->
            current.copy(devices = current.devices.filterNot { it.id == deviceId })
        }
        _state.update { it.copy(selectedDeviceId = null) }
    }

    fun undo() {
        val (label, previous) = undoStack.removeLastOrNull() ?: return
        _state.update {
            it.copy(
                draft = previous,
                undoDepth = undoStack.size,
                selectedDeviceId = null,
                isPickingDestination = false,
            )
        }
        _messages.tryEmit("Undone: $label")
    }

    // ------------------------------------------------------------------ save

    fun save() {
        val current = _state.value
        val original = current.original ?: return
        val floorId = floorId ?: return
        val floors = floors
        val devices = devices
        val uid = ownerUid

        if (floors == null || devices == null || uid == null) {
            _state.update { it.copy(saveError = FirebaseMissing) }
            return
        }

        val plan = planWrites(original, current.draft)
        if (plan.isEmpty) {
            _state.update { it.copy(isSaved = true) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            runCatching { commit(uid, floorId, plan, current.draft) }
                .onSuccess { _state.update { it.copy(isSaving = false, isSaved = true) } }
                .onFailure { _state.update { it.copy(isSaving = false, saveError = SaveFailed) } }
        }
    }

    /**
     * Runs the plan, in the one order that is safe whichever way the grid moved.
     *
     * A grid change is applied in two passes. The first grows both axes to the larger of
     * the old and new values, which can never strand a device and so is always allowed; the
     * device writes then happen with every cell they need in bounds; the second pass applies
     * the real geometry, by which time anything that would have been orphaned has already
     * moved or gone. A single resize up front would be rejected when shrinking, and a single
     * resize at the end would reject moves onto cells the floor does not have yet.
     *
     * Not one Firestore batch, despite section 2's wording. `placeDevice` already commits
     * its own batch of device-plus-channels, `deleteDevice` chunks itself around the 500
     * write limit, and a Storage upload is not a Firestore write at all. Reaching under the
     * repositories to assemble one batch would duplicate the invariants they exist to hold.
     */
    private suspend fun commit(uid: String, floorId: String, plan: SavePlan, draft: FloorDraft) {
        val floors = floors ?: return
        val devices = devices ?: return
        val original = loadedFloor ?: error("nothing loaded")

        val widest = Floor(
            id = original.id,
            ownerUid = original.ownerUid,
            name = original.name,
            planImageUrl = original.planImageUrl,
            gridRows = maxOf(original.gridRows, draft.gridRows),
            gridCols = maxOf(original.gridCols, draft.gridCols),
            createdAt = original.createdAt,
        )

        if (widest.gridRows != original.gridRows || widest.gridCols != original.gridCols) {
            floors.resizeGrid(floorId, widest.gridRows, widest.gridCols, devicesOnFloor = emptyList())
        }

        plan.deletes.forEach { devices.deleteDevice(it) }
        plan.moves.forEach { devices.move(it.id, widest, it.gridX, it.gridY) }
        plan.places.forEach { device ->
            devices.placeDevice(
                ownerUid = uid,
                floor = widest,
                type = device.type,
                name = device.name,
                gridX = device.gridX,
                gridY = device.gridY,
                config = device.config,
                channelNames = device.channelNames,
            )
        }
        plan.renames.forEach { devices.rename(it.id, it.name) }
        plan.configs.forEach { device ->
            when (val config = device.config) {
                is DeviceConfig.Appliance ->
                    devices.updateApplianceLimit(device.id, config.maxOnDurationSeconds)
                is DeviceConfig.Light -> devices.updateLightSchedule(device.id, config)
                is DeviceConfig.Camera -> devices.updateCameraUris(device.id, config)
                // A switch bank's channel count is immutable by contract and its channel
                // names live in a subcollection this screen never loads, so there is
                // nothing here to write. See the Configure sheet.
                else -> Unit
            }
        }

        if (widest.gridRows != draft.gridRows || widest.gridCols != draft.gridCols) {
            floors.resizeGrid(floorId, draft.gridRows, draft.gridCols, devicesOnFloor = emptyList())
        }

        plan.floorName?.let { floors.rename(floorId, it) }
        applyPlanImage(floors, uid, floorId, draft)
        if (plan.clearImage) floors.removePlanImage(uid, floorId)

        // The floor is now what the draft said it was, so reloading is pointless -- the
        // screen is closing.
        loadedFloor = widest.copy(gridRows = draft.gridRows, gridCols = draft.gridCols)
    }

    private suspend fun applyPlanImage(
        floors: FloorRepository,
        uid: String,
        floorId: String,
        draft: FloorDraft,
    ) {
        val upload = draft.pendingUpload
        val url = draft.planImageUrl
        when {
            upload != null -> floors.uploadPlanImage(uid, floorId, android.net.Uri.parse(upload))
            url != null -> floors.setPlanImageUrl(floorId, url)
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    private fun edit(label: String, transform: (FloorDraft) -> FloorDraft) {
        val current = _state.value
        undoStack.addLast(label to current.draft)
        while (undoStack.size > MaxUndoSteps) undoStack.removeFirst()

        _state.update {
            it.copy(
                draft = transform(it.draft),
                undoDepth = undoStack.size,
                // A new edit is a new attempt; leaving the previous failure up would attach
                // it to work the user has since redone.
                saveError = null,
            )
        }
    }

    private var nextNewId = 1

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to this floor."
        else -> "Couldn't load this floor. Check your connection and try again."
    }

    companion object {
        /** Section 2 fixes this string. */
        const val SaveFailed = "Couldn't save. Check your connection and try again."

        const val FirebaseMissing =
            "Firebase isn't set up yet. Add app/google-services.json and rebuild."

        /** Section 5: undo goes back ten steps. */
        const val MaxUndoSteps = 10

        /** Null [floorId] is create mode. */
        fun factory(floorId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditFloorViewModel(floorId) }
        }
    }
}
