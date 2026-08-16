package com.smarthome.control.ui.floor.edit

import com.smarthome.control.ui.floor.SamplePlan
import com.smarthome.control.ui.floor.samplePlanUri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.TimeOfDay
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.components.EmptyState
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Screen prompt 05 — the floor editor, in create and edit modes.
 *
 * Two jobs share one screen because they share one canvas: naming a floor and arranging
 * devices on it are the same activity separated by ninety seconds. What they do not share
 * with the *dashboard* is any way to switch a device on. Mixing control gestures and
 * arrange gestures on one surface is how somebody turns an iron on while trying to drag it,
 * and keeping them apart is the reason this screen exists.
 *
 * The other thing to know before reading further is in [FloorDraft]: nothing here writes
 * until `Save`.
 */
@Composable
fun EditFloorScreen(
    floorId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditFloorViewModel = viewModel(
        factory = EditFloorViewModel.factory(floorId),
        key = floorId ?: "create",
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmingDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }

    // Leaving is the screen's business rather than the content's, because both ways out --
    // the system back gesture and the top bar's close button -- have to pass the same
    // question first.
    val leave = { if (state.isDirty) confirmingDiscard = true else onClose() }
    BackHandler { leave() }

    if (confirmingDiscard) {
        ConfirmDialog(
            title = "Discard changes to this floor?",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            destructive = true,
            onDismiss = { confirmingDiscard = false },
            onConfirm = { confirmingDiscard = false; onClose() },
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.choosePickedImage(it.toString()) } }

    EditFloorContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onCancel = leave,
        onSave = viewModel::save,
        onUndo = viewModel::undo,
        onNameChange = viewModel::setFloorName,
        onStep = viewModel::goToStep,
        onFinishCreate = viewModel::finishCreate,
        onPickPhoto = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onChooseSample = { viewModel.chooseSamplePlan(samplePlanUri(context.packageName, it)) },
        onSkipPlan = viewModel::clearPlanImage,
        onGridChange = viewModel::setGrid,
        onArm = viewModel::arm,
        onTapCell = viewModel::tapCell,
        onSelectDevice = viewModel::select,
        onMoveDevice = viewModel::moveDevice,
        onStartMove = viewModel::startPickingDestination,
        onRenameDevice = viewModel::renameDevice,
        onConfigureDevice = viewModel::configureDevice,
        onDeleteDevice = viewModel::deleteDevice,
        onRetryLoad = viewModel::load,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditFloorContent(
    state: EditFloorUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onNameChange: (String) -> Unit,
    onStep: (CreateStep) -> Unit,
    onFinishCreate: () -> Unit,
    onPickPhoto: () -> Unit,
    onChooseSample: (SamplePlan) -> Unit,
    onSkipPlan: () -> Unit,
    onGridChange: (Int, Int) -> Unit,
    onArm: (DeviceType) -> Unit,
    onTapCell: (Int, Int) -> Unit,
    onSelectDevice: (String?) -> Unit,
    onMoveDevice: (String, Int, Int) -> Boolean,
    onStartMove: () -> Unit,
    onRenameDevice: (String, String) -> Unit,
    onConfigureDevice: (String, DeviceConfig, List<String>) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = SmartHomeTheme.colors

    var renamingDevice by remember { mutableStateOf<DraftDevice?>(null) }
    var configuringDevice by remember { mutableStateOf<DraftDevice?>(null) }
    var deletingDevice by remember { mutableStateOf<DraftDevice?>(null) }
    var changingGrid by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Cancel",
                                tint = colors.textPrimary,
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.title,
                                style = AppType.display,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.isDirty) {
                                // Section 2's amber dot. It is the whole of the unsaved
                                // indicator: a screen whose Save button already changes
                                // shape does not need a second badge saying the same thing
                                // in words.
                                Box(
                                    modifier = Modifier
                                        .padding(start = Spacing.sm)
                                        .size(DirtyDotSize)
                                        .background(colors.stateOn, RoundedCornerShape(percent = 50))
                                        .semantics { contentDescription = "Unsaved changes" },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                    ),
                    actions = {
                        if (!state.isCreating) {
                            IconButton(onClick = onUndo, enabled = state.canUndo) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Undo",
                                    tint = if (state.canUndo) colors.textPrimary else colors.outline,
                                )
                            }
                            SaveAction(state = state, onSave = onSave)
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        contentDescription = "More actions",
                                        tint = colors.textSecondary,
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    containerColor = colors.surface,
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Change grid",
                                                style = AppType.body,
                                                color = colors.textPrimary,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Grid4x4,
                                                contentDescription = null,
                                                tint = colors.textSecondary,
                                            )
                                        },
                                        onClick = { menuOpen = false; changingGrid = true },
                                    )
                                }
                            }
                        }
                    },
                )

                state.createStep?.let { CreateStepper(current = it) }

                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant,
                    )
                }

                state.saveError?.let { message ->
                    Text(
                        text = message,
                        style = AppType.body,
                        color = colors.stateError,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenHorizontal,
                            vertical = Spacing.sm,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loadError != null -> LoadFailure(
                    message = state.loadError,
                    onRetry = onRetryLoad,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.isCreating -> CreatePane(
                    state = state,
                    onNameChange = onNameChange,
                    onStep = onStep,
                    onFinishCreate = onFinishCreate,
                    onPickPhoto = onPickPhoto,
                    onChooseSample = onChooseSample,
                    onSkipPlan = onSkipPlan,
                    onGridChange = onGridChange,
                )

                else -> PlacePane(
                    state = state,
                    isDragging = isDragging,
                    onTapCell = onTapCell,
                    onSelectDevice = onSelectDevice,
                    onMoveDevice = onMoveDevice,
                    onDraggingChange = { isDragging = it },
                    onArm = onArm,
                    onRename = { renamingDevice = it },
                    onConfigure = { configuringDevice = it },
                    onStartMove = onStartMove,
                    onDelete = { deletingDevice = it },
                )
            }
        }
    }

    renamingDevice?.let { device ->
        RenameDialog(
            currentName = device.name,
            onDismiss = { renamingDevice = null },
            onConfirm = { renamingDevice = null; onRenameDevice(device.id, it) },
        )
    }

    deletingDevice?.let { device ->
        ConfirmDialog(
            title = "Delete ${device.name}?",
            body = "Its usage history will be removed too.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            onDismiss = { deletingDevice = null },
            onConfirm = { deletingDevice = null; onDeleteDevice(device.id) },
        )
    }

    configuringDevice?.let { device ->
        ConfigureDialog(
            device = device,
            onDismiss = { configuringDevice = null },
            onConfirm = { config, channelNames ->
                configuringDevice = null
                onConfigureDevice(device.id, config, channelNames)
            },
        )
    }

    if (changingGrid) {
        ChangeGridDialog(
            draft = state.draft,
            onDismiss = { changingGrid = false },
            onApply = { rows, cols -> changingGrid = false; onGridChange(rows, cols) },
        )
    }
}

// ---------------------------------------------------------------------------
// Create mode
// ---------------------------------------------------------------------------

@Composable
private fun CreatePane(
    state: EditFloorUiState,
    onNameChange: (String) -> Unit,
    onStep: (CreateStep) -> Unit,
    onFinishCreate: () -> Unit,
    onPickPhoto: () -> Unit,
    onChooseSample: (SamplePlan) -> Unit,
    onSkipPlan: () -> Unit,
    onGridChange: (Int, Int) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val step = state.createStep ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).padding(vertical = Spacing.md)) {
            when (step) {
                CreateStep.Name -> StepName(name = state.draft.name, onNameChange = onNameChange)
                CreateStep.Plan -> StepPlan(
                    draft = state.draft,
                    onPickPhoto = onPickPhoto,
                    onChooseSample = onChooseSample,
                    onSkip = {
                        onSkipPlan()
                        onStep(CreateStep.Grid)
                    },
                )
                CreateStep.Grid -> StepGrid(
                    draft = state.draft,
                    rows = state.draft.gridRows,
                    cols = state.draft.gridCols,
                    onGridChange = onGridChange,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != CreateStep.Name) {
                TextButton(onClick = { onStep(CreateStep.entries[step.index - 1]) }) {
                    Text("Back", style = AppType.label, color = colors.textSecondary)
                }
            }
            Box(modifier = Modifier.weight(1f))

            val isLast = step == CreateStep.Grid
            Button(
                onClick = {
                    if (isLast) onFinishCreate() else onStep(CreateStep.entries[step.index + 1])
                },
                enabled = state.canAdvanceFromName && !state.isSaving,
                shape = AppShapes.buttonPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SaveIndicatorSize),
                        color = colors.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (isLast) "Done" else "Next", style = AppType.label)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Placement mode
// ---------------------------------------------------------------------------

@Composable
private fun PlacePane(
    state: EditFloorUiState,
    isDragging: Boolean,
    onTapCell: (Int, Int) -> Unit,
    onSelectDevice: (String?) -> Unit,
    onMoveDevice: (String, Int, Int) -> Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onArm: (DeviceType) -> Unit,
    onRename: (DraftDevice) -> Unit,
    onConfigure: (DraftDevice) -> Unit,
    onStartMove: () -> Unit,
    onDelete: (DraftDevice) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    val selected = state.selectedDevice

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)
                // Section 7: while a save is in flight the canvas stops taking input. An
                // edit made mid-write would either be lost or land in a second write the
                // user did not ask for.
                .alpha(if (state.isSaving) SavingAlpha else 1f),
        ) {
            PlacementCanvas(
                draft = state.draft,
                selectedDeviceId = state.selectedDeviceId,
                enabled = !state.isSaving,
                onTapCell = onTapCell,
                onSelectDevice = { onSelectDevice(it) },
                onMoveDevice = onMoveDevice,
                onDraggingChange = onDraggingChange,
                modifier = Modifier.fillMaxSize(),
            )

            if (state.draft.devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Rounded.Tune,
                        message = "No devices placed yet.",
                    )
                }
            }
        }

        Text(
            text = placementHint(
                armedType = state.armedType,
                selectedDeviceId = state.selectedDeviceId,
                isDragging = isDragging,
                isPickingDestination = state.isPickingDestination,
                deviceCount = state.draft.devices.size,
            ),
            style = AppType.label,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
        )

        if (selected != null) {
            MarkerActionBar(
                device = selected,
                onRename = { onRename(selected) },
                onConfigure = { onConfigure(selected) },
                onMove = onStartMove,
                onDelete = { onDelete(selected) },
            )
        }

        DeviceTray(armed = state.armedType, onArm = onArm)
    }
}

/**
 * The tray, which stands in for the bottom navigation bar while this screen is up.
 *
 * Replacing the nav bar rather than sitting above it is the third mode signal, after the
 * grid lines and the dashed border: the app's usual way out is not where it usually is,
 * because leaving this screen goes through Save or Cancel.
 */
@Composable
private fun DeviceTray(armed: DeviceType?, onArm: (DeviceType) -> Unit) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(Spacing.sm)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DeviceType.entries.forEach { type ->
            val isArmed = type == armed
            AppCard(
                modifier = Modifier
                    .width(TrayItemWidth)
                    .clickable { onArm(type) }
                    .semantics {
                        contentDescription =
                            "${type.trayLabel}${if (isArmed) ", armed" else ""}"
                    },
                color = if (isArmed) colors.primary else colors.surfaceVariant,
                border = null,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = type.outlinedIcon,
                        contentDescription = null,
                        tint = if (isArmed) colors.onPrimary else colors.textSecondary,
                    )
                    Text(
                        text = type.trayLabel,
                        style = AppType.label,
                        color = if (isArmed) colors.onPrimary else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Rises above the tray when a marker is selected (section 5). */
@Composable
private fun MarkerActionBar(
    device: DraftDevice,
    onRename: () -> Unit,
    onConfigure: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal)
            .background(colors.surfaceVariant, AppShapes.card)
            .border(AppBorders.hairline, colors.outline, AppShapes.card)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = device.name,
            style = AppType.label,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = colors.textPrimary)
        }
        // The non-gesture equivalent of a drag (section 10). It is not an accessibility
        // afterthought bolted beside the gesture -- it drives the same move the drag does.
        IconButton(onClick = onMove) {
            Icon(Icons.Rounded.OpenWith, contentDescription = "Move", tint = colors.textPrimary)
        }
        // Absent, not disabled, for an outlet: there is nothing an outlet configures, and a
        // greyed control promises otherwise.
        if (device.type.isConfigurable) {
            IconButton(onClick = onConfigure) {
                Icon(Icons.Rounded.Tune, contentDescription = "Configure", tint = colors.textPrimary)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = colors.stateError)
        }
    }
}

/**
 * `Save`, in its three states.
 *
 * Clean it is inert text, dirty it is a filled pill, saving it is an indicator at the *same
 * width* as the pill. Letting the button resize while it works would shift everything
 * beside it at the exact moment the user is watching to see whether their edit took.
 */
@Composable
private fun SaveAction(state: EditFloorUiState, onSave: () -> Unit) {
    val colors = SmartHomeTheme.colors

    Box(
        modifier = Modifier.width(SaveWidth),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isSaving -> CircularProgressIndicator(
                modifier = Modifier.size(SaveIndicatorSize),
                color = colors.primary,
                strokeWidth = 2.dp,
            )

            state.isDirty -> Button(
                onClick = onSave,
                shape = AppShapes.buttonPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.xs,
                ),
            ) {
                Text("Save", style = AppType.label)
            }

            else -> Text(
                text = "Save",
                style = AppType.label,
                color = colors.textSecondary,
                modifier = Modifier.semantics { contentDescription = "Save. Nothing to save yet" },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun ChangeGridDialog(
    draft: FloorDraft,
    onDismiss: () -> Unit,
    onApply: (rows: Int, cols: Int) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var rows by remember { mutableStateOf(draft.gridRows) }
    var cols by remember { mutableStateOf(draft.gridCols) }
    val orphaned = draft.orphanedBy(rows, cols)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Change grid", style = AppType.sectionHeader) },
        text = {
            ChangeGridBody(
                draft = draft,
                rows = rows,
                cols = cols,
                onRowsChange = { rows = it },
                onColsChange = { cols = it },
            )
        },
        confirmButton = {
            // Disabled, not warned about. Applying would either strand the devices or move
            // them without being asked, and both are worse than making the user move them.
            TextButton(onClick = { onApply(rows, cols) }, enabled = orphaned.isEmpty()) {
                Text(
                    "Apply",
                    style = AppType.label,
                    color = if (orphaned.isEmpty()) colors.primary else colors.outline,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

/**
 * The body of the change-grid dialog, kept out of it so section 11's artboard can show the
 * orphan warning without a dialog window in the preview pane.
 */
@Composable
private fun ChangeGridBody(
    draft: FloorDraft,
    rows: Int,
    cols: Int,
    onRowsChange: (Int) -> Unit,
    onColsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    val orphaned = draft.orphanedBy(rows, cols)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GridStepper(
                label = "Rows",
                value = rows,
                onChange = onRowsChange,
                modifier = Modifier.weight(1f),
            )
            GridStepper(
                label = "Columns",
                value = cols,
                onChange = onColsChange,
                modifier = Modifier.weight(1f),
            )
        }

        GridPreview(
            draft = draft,
            rows = rows,
            cols = cols,
            modifier = Modifier
                .fillMaxWidth()
                .height(GridDialogPreviewHeight),
        )

        if (orphaned.isNotEmpty()) {
            Text(
                text = "${orphaned.size} ${if (orphaned.size == 1) "device" else "devices"} " +
                    "would be outside the new grid.",
                style = AppType.body,
                color = colors.stateError,
            )
        }
    }
}

/**
 * The type-specific setup (section 5).
 *
 * A switch bank's channel count is settable only while the device is new, because
 * `channel_count` is immutable by contract — changing it on a placed unit would orphan the
 * usage history of any channel that disappeared. Channel *names* on an existing unit are
 * edited from its control sheet, which is the screen that already has them loaded.
 */
@Composable
private fun ConfigureDialog(
    device: DraftDevice,
    onDismiss: () -> Unit,
    onConfirm: (DeviceConfig, List<String>) -> Unit,
) {
    val colors = SmartHomeTheme.colors

    var minutes by remember {
        mutableStateOf(
            ((device.config as? DeviceConfig.Appliance)?.maxOnDurationSeconds ?: 0).let { it / 60 }
                .coerceAtLeast(1)
                .toString(),
        )
    }
    val light = device.config as? DeviceConfig.Light
    var scheduleEnabled by remember { mutableStateOf(light?.scheduleEnabled ?: false) }
    var onTime by remember { mutableStateOf(light?.scheduleOn?.wireValue.orEmpty()) }
    var offTime by remember { mutableStateOf(light?.scheduleOff?.wireValue.orEmpty()) }
    val camera = device.config as? DeviceConfig.Camera
    var streamUri by remember { mutableStateOf(camera?.streamUri.orEmpty()) }
    var snapshotUri by remember { mutableStateOf(camera?.snapshotUri.orEmpty()) }
    var channelCount by remember {
        mutableStateOf((device.config as? DeviceConfig.MultiSwitch)?.channelCount ?: 3)
    }
    var channelNames by remember {
        mutableStateOf(device.channelNames.ifEmpty { List(channelCount) { "" } })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Configure ${device.name}", style = AppType.sectionHeader) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (device.type) {
                    DeviceType.APPLIANCE -> {
                        LabeledTextField(
                            label = "Maximum on time (minutes)",
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                            helperText = "The safety cutoff switches it off after this.",
                            helperVisibleWhenUnfocused = true,
                        )
                    }

                    DeviceType.LIGHT -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Daily schedule",
                                style = AppType.body,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                        }
                        LabeledTextField(
                            label = "On at (HH:MM)",
                            value = onTime,
                            onValueChange = { onTime = it.take(5) },
                            enabled = scheduleEnabled,
                        )
                        LabeledTextField(
                            label = "Off at (HH:MM)",
                            value = offTime,
                            onValueChange = { offTime = it.take(5) },
                            enabled = scheduleEnabled,
                        )
                    }

                    DeviceType.CAMERA -> {
                        LabeledTextField(
                            label = "Stream URI",
                            value = streamUri,
                            onValueChange = { streamUri = it },
                        )
                        LabeledTextField(
                            label = "Snapshot URI",
                            value = snapshotUri,
                            onValueChange = { snapshotUri = it },
                        )
                    }

                    DeviceType.MULTI_SWITCH -> {
                        if (device.isNew) {
                            GridStepper(
                                label = "Channels",
                                value = channelCount,
                                onChange = { count ->
                                    val clamped = count.coerceIn(1, MaxChannels)
                                    channelCount = clamped
                                    channelNames = List(clamped) { channelNames.getOrElse(it) { "" } }
                                },
                            )
                            channelNames.forEachIndexed { index, name ->
                                LabeledTextField(
                                    label = "Channel ${index + 1}",
                                    value = name,
                                    onValueChange = { value ->
                                        channelNames = channelNames.toMutableList()
                                            .also { it[index] = value }
                                    },
                                )
                            }
                        } else {
                            Text(
                                "A switch unit's channels are fixed when it is placed. " +
                                    "Rename them from its control sheet.",
                                style = AppType.body,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    DeviceType.OUTLET -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config = when (device.type) {
                        DeviceType.APPLIANCE -> DeviceConfig.Appliance(
                            maxOnDurationSeconds = (minutes.toIntOrNull() ?: 1).coerceAtLeast(1) * 60,
                        )
                        DeviceType.LIGHT -> {
                            val on = TimeOfDay.parseOrNull(onTime)
                            val off = TimeOfDay.parseOrNull(offTime)
                            // A schedule missing either edge is not a schedule the worker
                            // can evaluate, so it saves as no schedule rather than as half
                            // of one.
                            if (scheduleEnabled && on != null && off != null) {
                                DeviceConfig.Light(true, on, off)
                            } else {
                                DeviceConfig.Light(false, on, off)
                            }
                        }
                        DeviceType.CAMERA -> DeviceConfig.Camera(streamUri.trim(), snapshotUri.trim())
                        DeviceType.MULTI_SWITCH -> DeviceConfig.MultiSwitch(channelCount)
                        DeviceType.OUTLET -> DeviceConfig.Outlet
                    }
                    onConfirm(config, channelNames.map { it.trim() })
                },
            ) {
                Text("Save", style = AppType.label, color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Rename device", style = AppType.sectionHeader) },
        text = {
            LabeledTextField(label = "Device name", value = name, onValueChange = { name = it })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save", style = AppType.label, color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    body: String? = null,
    destructive: Boolean = false,
) {
    val colors = SmartHomeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text(title, style = AppType.sectionHeader) },
        text = body?.let { { Text(it, style = AppType.body, color = colors.textSecondary) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = AppType.label,
                    color = if (destructive) colors.stateError else colors.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun LoadFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SmartHomeTheme.colors
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = message, style = AppType.body, color = colors.stateError)
        TextButton(onClick = onRetry) {
            Text("Try again", style = AppType.label, color = colors.primary)
        }
    }
}

private val DirtyDotSize = 8.dp
private val SaveWidth = 76.dp
private val SaveIndicatorSize = 20.dp
private val TrayItemWidth = 76.dp
private val GridDialogPreviewHeight = 220.dp
private const val SavingAlpha = 0.6f
private const val MaxChannels = 6

// ---------------------------------------------------------------------------
// Artboards — the section 11 deliverable
// ---------------------------------------------------------------------------

private val PreviewDevices = listOf(
    DraftDevice("d1", DeviceType.LIGHT, "Hall lamp", 1, 2, DeviceConfig.Light.OFF),
    DraftDevice("d2", DeviceType.OUTLET, "Outlet 1", 5, 2, DeviceConfig.Outlet),
    DraftDevice("d3", DeviceType.APPLIANCE, "Iron", 7, 4, DeviceConfig.Appliance(120)),
    DraftDevice("d4", DeviceType.CAMERA, "Porch camera", 3, 4, DeviceConfig.Camera("", "")),
)

private val PreviewDraft = FloorDraft(
    name = "Ground Floor",
    planImageUrl = null,
    gridRows = 6,
    gridCols = 10,
    devices = PreviewDevices,
)

private val PreviewPlacing = EditFloorUiState(
    mode = EditorMode.Place,
    draft = PreviewDraft,
    original = PreviewDraft,
)

@Composable
private fun Artboard(state: EditFloorUiState, dark: Boolean = true) {
    SmartHomeTheme(darkTheme = dark) {
        EditFloorContent(
            state = state,
            onCancel = {},
            onSave = {},
            onUndo = {},
            onNameChange = {},
            onStep = {},
            onFinishCreate = {},
            onPickPhoto = {},
            onChooseSample = {},
            onSkipPlan = {},
            onGridChange = { _, _ -> },
            onArm = {},
            onTapCell = { _, _ -> },
            onSelectDevice = {},
            onMoveDevice = { _, _, _ -> true },
            onStartMove = {},
            onRenameDevice = { _, _ -> },
            onConfigureDevice = { _, _, _ -> },
            onDeleteDevice = {},
            onRetryLoad = {},
        )
    }
}

@Preview(name = "Edit floor · create step 2", widthDp = 412, heightDp = 915)
@Composable
private fun CreateStepPlanPreview() = Artboard(
    EditFloorUiState(
        mode = EditorMode.Create(CreateStep.Plan),
        draft = FloorDraft(name = "Ground Floor"),
    ),
)

@Preview(name = "Edit floor · create step 3", widthDp = 412, heightDp = 915)
@Composable
private fun CreateStepGridPreview() = Artboard(
    EditFloorUiState(
        mode = EditorMode.Create(CreateStep.Grid),
        draft = FloorDraft(name = "Ground Floor", gridRows = 8, gridCols = 10),
    ),
)

@Preview(name = "Edit floor · placement, nothing armed", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementIdlePreview() = Artboard(PreviewPlacing)

@Preview(name = "Edit floor · placement, armed", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementArmedPreview() = Artboard(
    PreviewPlacing.copy(armedType = DeviceType.OUTLET),
)

@Preview(name = "Edit floor · marker selected", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementSelectedPreview() = Artboard(
    PreviewPlacing.copy(selectedDeviceId = "d3"),
)

@Preview(name = "Edit floor · dirty and saving", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementSavingPreview() = Artboard(
    PreviewPlacing.copy(
        draft = PreviewDraft.copy(devices = PreviewDevices.dropLast(1)),
        isSaving = true,
    ),
)

@Preview(name = "Edit floor · empty floor", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementEmptyPreview() = Artboard(
    PreviewPlacing.copy(draft = PreviewDraft.copy(devices = emptyList())),
)

@Preview(name = "Edit floor · save failed", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementSaveFailedPreview() = Artboard(
    PreviewPlacing.copy(
        draft = PreviewDraft.copy(devices = PreviewDevices.dropLast(1)),
        saveError = EditFloorViewModel.SaveFailed,
    ),
)

@Preview(name = "Edit floor · placement, light", widthDp = 412, heightDp = 915)
@Composable
private fun PlacementLightPreview() = Artboard(PreviewPlacing, dark = false)

@Preview(name = "Edit floor · change grid, orphaned", widthDp = 412, heightDp = 560)
@Composable
private fun ChangeGridOrphanedPreview() {
    SmartHomeTheme(darkTheme = true) {
        val colors = SmartHomeTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Change grid", style = AppType.sectionHeader, color = colors.textPrimary)
            // Shrunk to 5 x 6, which strands the two devices out at columns 7 and 5.
            ChangeGridBody(
                draft = PreviewDraft,
                rows = 5,
                cols = 6,
                onRowsChange = {},
                onColsChange = {},
            )
        }
    }
}
