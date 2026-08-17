package com.smarthome.control.ui.floor.edit

import com.smarthome.control.ui.model.DeviceType

/**
 * Which of the screen's two jobs is on show.
 *
 * Create runs as a stepper *inside* this screen rather than as three routes, because the
 * three steps are one decision — a floor with a name but no grid is not a thing the app can
 * hold — and because step 3's preview has to show the plan chosen in step 2 without either
 * of them owning the other's state.
 */
sealed interface EditorMode {
    data class Create(val step: CreateStep) : EditorMode
    data object Place : EditorMode
}

enum class CreateStep(val label: String) {
    Name("Name"),
    Plan("Plan"),
    Grid("Grid");

    val index: Int get() = ordinal
}

/**
 * Everything the floor editor draws.
 *
 * @param original the floor as it was loaded, or null while create mode is still ahead of
 *   the floor existing. Dirtiness is the difference between this and the draft, which means
 *   an edit and its undo leave the screen clean — as they should, since there is then
 *   nothing to write.
 * @param isPickingDestination the non-gesture equivalent of a drag (section 10): a marker is
 *   selected, `Move` was chosen, and the next cell tap is the destination.
 */
data class EditFloorUiState(
    val isLoading: Boolean = false,
    val mode: EditorMode = EditorMode.Place,
    val draft: FloorDraft = FloorDraft(),
    val original: FloorDraft? = null,
    val armedType: DeviceType? = null,
    val selectedDeviceId: String? = null,
    val isPickingDestination: Boolean = false,
    val undoDepth: Int = 0,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
) {
    val isCreating: Boolean get() = mode is EditorMode.Create

    val createStep: CreateStep? get() = (mode as? EditorMode.Create)?.step

    /**
     * Whether there is anything to save.
     *
     * Asked of the save plan rather than tracked as a flag, so the amber dot and the `Save`
     * pill can never disagree with what a save would actually do. Placing a device and
     * undoing it leaves no dot, because it leaves no write.
     */
    val isDirty: Boolean
        get() = original?.let { !planWrites(it, draft).isEmpty } ?: false

    val canUndo: Boolean get() = undoDepth > 0

    val selectedDevice: DraftDevice? get() = selectedDeviceId?.let { draft.device(it) }

    /** `New floor` until it has a name; the floor's own name once it does (section 9). */
    val title: String
        get() = when {
            isCreating -> "New floor"
            draft.name.isNotBlank() -> draft.name
            else -> "Floor"
        }

    /** `Next` is dead until the field has something in it (section 3, step 1). */
    val canAdvanceFromName: Boolean get() = draft.name.isNotBlank()
}
