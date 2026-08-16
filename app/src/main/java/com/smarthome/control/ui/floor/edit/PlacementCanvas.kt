package com.smarthome.control.ui.floor.edit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.smarthome.control.ui.components.DeviceMarker
import com.smarthome.control.ui.components.dashedBorder
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The arrangement surface — the grid, the plan under it, and the markers on it.
 *
 * ### The grid is drawn as full lines here, and that is deliberate
 *
 * Screen prompt 03 draws the dashboard's grid as dots so the architecture reads through it.
 * This screen inverts that rule: here the user is reasoning about cells as *targets*, so
 * the cell boundaries are the content rather than the scaffolding. The inversion is also
 * the mode signal — switching from dots to lines tells the user wordlessly that this canvas
 * behaves differently, which no banner does as quickly.
 *
 * ### Nothing here toggles anything
 *
 * Not one gesture on this canvas changes a device's power state. Mixing "control" and
 * "arrange" on one surface is how somebody turns an iron on while trying to drag it, and
 * avoiding that is the reason this screen exists apart from the dashboard at all.
 *
 * @param onMoveDevice returns false when the drop is refused, which is what drives the
 *   spring-back and the red flash. The canvas asks rather than deciding, so that the rule
 *   about occupied cells lives with the draft and not in a gesture handler.
 */
@Composable
fun PlacementCanvas(
    draft: FloorDraft,
    selectedDeviceId: String?,
    enabled: Boolean,
    onTapCell: (gridX: Int, gridY: Int) -> Unit,
    onSelectDevice: (String) -> Unit,
    onMoveDevice: (deviceId: String, gridX: Int, gridY: Int) -> Boolean,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    highlightOrphansFor: GridSize? = null,
) {
    val colors = SmartHomeTheme.colors
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    var draggingId by remember { mutableStateOf<String?>(null) }
    var hoverCell by remember { mutableStateOf<Cell?>(null) }
    var rejectedCell by remember { mutableStateOf<Cell?>(null) }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // The rejection flash is a moment, not a state. It clears itself so no caller has to
    // remember to.
    LaunchedEffect(rejectedCell) {
        if (rejectedCell != null) {
            delay(RejectFlashMillis)
            rejectedCell = null
        }
    }

    val orphans = highlightOrphansFor
        ?.let { draft.orphanedBy(it.rows, it.cols).map { device -> device.id }.toSet() }
        .orEmpty()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Square cells, sized to whichever axis runs out first. A grid of rectangles would
        // make a 10x8 plan look like a 10x8 room, which is a lie about the building.
        val cell: Dp = min(
            (maxWidth / draft.gridCols).value,
            (maxHeight / draft.gridRows).value,
        ).dp
        val cellPx = with(density) { cell.toPx() }

        Box(
            modifier = Modifier
                .size(cell * draft.gridCols, cell * draft.gridRows)
                .background(colors.surfaceVariant)
                // The mode's second signal, after the lines themselves.
                .dashedBorder(colors.primary, AppBorders.emphasis, 0.dp),
        ) {
            PlanImage(url = draft.planImageUrl, modifier = Modifier.fillMaxSize())

            Canvas(modifier = Modifier.fillMaxSize()) {
                hoverCell?.let { target ->
                    drawRect(
                        color = colors.primary.copy(alpha = HoverAlpha),
                        topLeft = Offset(target.x * cellPx, target.y * cellPx),
                        size = Size(cellPx, cellPx),
                    )
                }
                rejectedCell?.let { target ->
                    drawRect(
                        color = colors.stateError.copy(alpha = RejectAlpha),
                        topLeft = Offset(target.x * cellPx, target.y * cellPx),
                        size = Size(cellPx, cellPx),
                    )
                }

                drawGridLines(
                    rows = draft.gridRows,
                    cols = draft.gridCols,
                    cellPx = cellPx,
                    color = colors.outline,
                    strokePx = with(density) { AppBorders.hairline.toPx() },
                )
            }

            // Every cell is a node a screen reader can land on (section 10). They are
            // transparent and sit under the markers, so a tap on a marker never reaches the
            // cell beneath it.
            Column {
                repeat(draft.gridRows) { y ->
                    Row {
                        repeat(draft.gridCols) { x ->
                            val occupant = draft.deviceAt(x, y)
                            Box(
                                modifier = Modifier
                                    .size(cell)
                                    .clickable(enabled = enabled) { onTapCell(x, y) }
                                    .clearAndSetSemantics {
                                        contentDescription = occupant?.cellDescription
                                            ?: emptyCellDescription(x, y)
                                    },
                            )
                        }
                    }
                }
            }

            draft.devices.forEach { device ->
                val isDragging = draggingId == device.id
                val offset = if (isDragging) dragOffset.value else Offset.Zero

                Box(
                    modifier = Modifier
                        .offset(x = cell * device.gridX, y = cell * device.gridY)
                        // A marker being dragged passes over the ones placed after it in
                        // the list, so it has to come out on top while it moves.
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                offset.x.roundToInt(),
                                offset.y.roundToInt(),
                            )
                        }
                        .size(cell)
                        .pointerInput(device.id, enabled, cellPx, draft) {
                            if (!enabled) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = device.id
                                    onSelectDevice(device.id)
                                    onDraggingChange(true)
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    scope.launch { dragOffset.snapTo(dragOffset.value + delta) }
                                    hoverCell = targetCell(device, dragOffset.value + delta, cellPx)
                                },
                                onDragCancel = {
                                    scope.launch { dragOffset.snapTo(Offset.Zero) }
                                    draggingId = null
                                    hoverCell = null
                                    onDraggingChange(false)
                                },
                                onDragEnd = {
                                    val target = targetCell(device, dragOffset.value, cellPx)
                                    val accepted = onMoveDevice(device.id, target.x, target.y)
                                    if (!accepted) rejectedCell = target

                                    scope.launch {
                                        // Accepted or not, the marker returns to the origin:
                                        // an accepted move redraws it at its new cell, so
                                        // animating the offset away would double the motion.
                                        if (accepted || reducedMotion) {
                                            dragOffset.snapTo(Offset.Zero)
                                        } else {
                                            dragOffset.animateTo(
                                                Offset.Zero,
                                                tween(SpringBackMillis),
                                            )
                                        }
                                    }
                                    draggingId = null
                                    hoverCell = null
                                    onDraggingChange(false)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DeviceMarker(
                        type = device.type,
                        // Placement never shows power. Every marker is drawn in the same
                        // neutral state so the canvas cannot be mistaken for a control
                        // surface, and an orphaned device is the one exception worth
                        // shouting about.
                        state = if (device.id in orphans) DeviceState.ERROR else DeviceState.OFF,
                        deviceName = device.name,
                        selected = device.id == selectedDeviceId,
                        size = cell * MarkerScale,
                        onClick = if (enabled) {
                            { onSelectDevice(device.id) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/**
 * A read-only version of the same canvas, for create step 3 and the change-grid dialog.
 *
 * Step 3 exists because nobody can tell whether an 8 x 10 grid lands sensibly on their
 * rooms by reading the numbers 8 and 10. Showing the plan with the grid over it is the
 * whole step, so the preview is not decoration here — it is the control.
 */
@Composable
fun GridPreview(
    draft: FloorDraft,
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    val density = LocalDensity.current
    val orphans = draft.orphanedBy(rows, cols).map { it.id }.toSet()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val cell: Dp = min((maxWidth / cols).value, (maxHeight / rows).value).dp
        val cellPx = with(density) { cell.toPx() }

        Box(
            modifier = Modifier
                .size(cell * cols, cell * rows)
                .background(colors.surfaceVariant),
        ) {
            PlanImage(url = draft.planImageUrl, modifier = Modifier.fillMaxSize())

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGridLines(
                    rows = rows,
                    cols = cols,
                    cellPx = cellPx,
                    color = colors.outline,
                    strokePx = with(density) { AppBorders.hairline.toPx() },
                )
            }

            draft.devices.forEach { device ->
                // A device outside the proposed bounds is drawn where it actually is, which
                // may be off the box entirely. Clipping it would hide the very thing the
                // warning is about.
                Box(
                    modifier = Modifier
                        .offset(x = cell * device.gridX, y = cell * device.gridY)
                        .size(cell),
                    contentAlignment = Alignment.Center,
                ) {
                    DeviceMarker(
                        type = device.type,
                        state = if (device.id in orphans) DeviceState.ERROR else DeviceState.OFF,
                        deviceName = device.name,
                        size = cell * MarkerScale,
                    )
                }
            }
        }
    }
}

private data class Cell(val x: Int, val y: Int)

/** Where a marker dragged by [offset] would land. */
private fun targetCell(device: DraftDevice, offset: Offset, cellPx: Float): Cell = Cell(
    x = device.gridX + (offset.x / cellPx).roundToInt(),
    y = device.gridY + (offset.y / cellPx).roundToInt(),
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridLines(
    rows: Int,
    cols: Int,
    cellPx: Float,
    color: Color,
    strokePx: Float,
) {
    for (column in 0..cols) {
        val x = (column * cellPx).coerceAtMost(size.width)
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokePx)
    }
    for (row in 0..rows) {
        val y = (row * cellPx).coerceAtMost(size.height)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = strokePx)
    }
}

/** Section 5: cells highlight in `primary` at 20 % as the marker passes over them. */
private const val HoverAlpha = 0.20f
private const val RejectAlpha = 0.35f
private const val RejectFlashMillis = 400L
private const val SpringBackMillis = 200
private const val MarkerScale = 0.78f
