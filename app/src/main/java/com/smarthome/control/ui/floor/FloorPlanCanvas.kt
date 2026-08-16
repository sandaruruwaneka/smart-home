package com.smarthome.control.ui.floor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil3.compose.rememberAsyncImagePainter
import com.smarthome.control.ui.components.DeviceMarker
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Zoom and pan, hoisted.
 *
 * The overflow menu has to be able to drive the same viewport the pinch gesture does —
 * screen prompt 03 section 11 requires a non-gesture equivalent for every zoom action, and
 * an accessibility affordance that controls a *different* copy of the state is not an
 * equivalent. So the state lives here and both the canvas and the menu hold it.
 */
class FloorPlanViewport {
    var scale by mutableFloatStateOf(MinScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Canvas bounds in pixels, recorded at layout so panning can be clamped to them. */
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    val isZoomed: Boolean get() = scale > MinScale + 0.01f

    fun onSized(width: Float, height: Float) {
        viewportWidth = width
        viewportHeight = height
        clamp()
    }

    /**
     * Applies a pinch about its focal point.
     *
     * The focal correction is what makes zoom feel like it is happening to the plan rather
     * than to the screen: without it, pinching on a corner walks the whole floor plan out
     * from under the fingers doing the pinching.
     */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val next = (scale * zoom).coerceIn(MinScale, MaxScale)
        // The applied zoom, which is not the requested one once the pinch hits a limit.
        // Using the requested figure here is what makes a plan creep sideways while the
        // user keeps pinching against the 3x ceiling.
        val applied = next / scale.coerceAtLeast(0.01f)

        // The layer scales about its own centre, so the fixed point is expressed relative
        // to that centre. Solving "the content under the fingers must stay under the
        // fingers" for the new translation gives offset' = offset * a + focal * (1 - a).
        val centre = Offset(viewportWidth / 2f, viewportHeight / 2f)
        val focal = centroid - centre

        offset = offset * applied + focal * (1f - applied) + pan
        scale = next
        clamp()
    }

    fun zoomIn() = zoomTo(scale + ZoomStep)

    fun zoomOut() = zoomTo(scale - ZoomStep)

    /** Back to fit. Also what a double-tap does. */
    fun fit() {
        scale = MinScale
        offset = Offset.Zero
    }

    private fun zoomTo(target: Float) {
        scale = target.coerceIn(MinScale, MaxScale)
        if (scale == MinScale) offset = Offset.Zero else clamp()
    }

    /**
     * Keeps the plan covering the canvas.
     *
     * At 1× there is nothing to pan, so the offset is pinned at zero; beyond that the
     * content may travel by half the overflow in each direction and no further, which is
     * what stops a drag flinging the floor plan off the screen entirely.
     */
    private fun clamp() {
        val maxX = max(0f, viewportWidth * (scale - 1f) / 2f)
        val maxY = max(0f, viewportHeight * (scale - 1f) / 2f)
        offset = Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }

    companion object {
        const val MinScale = 1f
        const val MaxScale = 3f
        private const val ZoomStep = 0.5f
    }
}

@Composable
fun rememberFloorPlanViewport(): FloorPlanViewport = remember { FloorPlanViewport() }

/**
 * The floor plan: image, grid, markers.
 *
 * ### Dots, not lines
 *
 * The grid is drawn as 1 dp dots at 40 % `outline`, one per cell, and never as full lines.
 * Lines fight the plan's own wall lines and turn the canvas into a lattice of noise in
 * which the architecture stops being readable — which defeats the point of showing the
 * plan at all. Dots communicate the same coordinate system and leave the drawing intact.
 * This is the single most consequential visual decision on the screen.
 *
 * A cell holding a device draws no dot: the marker is the dot, and drawing both puts a
 * speck behind every icon.
 *
 * ### The grid belongs to the image, not to the canvas
 *
 * Everything is laid out inside a box the exact aspect ratio of the fitted plan, so the
 * grid lands on the drawing rather than on the card around it. With no plan image the same
 * box takes the grid's own aspect ratio over a flat `surfaceVariant` field — the screen
 * stays fully functional without the image, because the grid is the real coordinate system
 * and the image is context.
 *
 * @param dimmed markers drop to 60 % while offline, where their state is last-known rather
 *   than current.
 */
@Composable
fun FloorPlanCanvas(
    state: FloorDashboardUiState,
    viewport: FloorPlanViewport,
    selectedDeviceId: String?,
    onSelectMarker: (MarkerUiState) -> Unit,
    onLongPressMarker: (MarkerUiState) -> Unit,
    onTapEmptySpace: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    showMarkers: Boolean = true,
) {
    val colors = SmartHomeTheme.colors
    val density = LocalDensity.current

    // Resource-first, then Coil -- see PlanImage. The bundled sample plans are referenced
    // by `android.resource://` URI, which Coil does not resolve, so routing every plan
    // through it left a floor with a sample plan rendering as a bare grid.
    val painter = state.planImageUrl?.takeIf { it.isNotBlank() }?.let { planPainter(it) }
    val intrinsic = painter?.intrinsicSize
    val imageAspect = intrinsic
        ?.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
        ?.let { it.width / it.height }

    BoxWithConstraints(
        modifier = modifier
            .clip(AppShapes.card)
            .background(colors.surface)
            .border(AppBorders.hairline, colors.outline, AppShapes.card)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport.transform(centroid, pan, zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    // Tapping bare canvas clears the selection. It deliberately does not
                    // open an "add device" flow — placement belongs to Edit Floor, and a
                    // stray tap on a plan should never create anything.
                    onTap = { onTapEmptySpace() },
                    onDoubleTap = { viewport.fit() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight

        // Recorded for pan clamping, which needs pixels rather than dp. In an effect
        // rather than inline, because clamping writes state the same composition reads.
        val widthPx = with(density) { canvasWidth.toPx() }
        val heightPx = with(density) { canvasHeight.toPx() }
        LaunchedEffect(widthPx, heightPx) { viewport.onSized(widthPx, heightPx) }

        val aspect = imageAspect
            ?: (state.gridCols.toFloat() / state.gridRows.toFloat().coerceAtLeast(1f))
        val canvasAspect = canvasWidth / canvasHeight

        // Aspect-fit, never crop: a cropped floor plan loses rooms, and a room the user
        // cannot see is a room they cannot control.
        val planWidth: Dp
        val planHeight: Dp
        if (aspect >= canvasAspect) {
            planWidth = canvasWidth
            planHeight = canvasWidth / aspect
        } else {
            planHeight = canvasHeight
            planWidth = canvasHeight * aspect
        }

        val cellWidth = planWidth / state.gridCols.coerceAtLeast(1)
        val cellHeight = planHeight / state.gridRows.coerceAtLeast(1)
        // Markers are square, so they are sized by the tighter of the two axes.
        val cell = min(cellWidth.value, cellHeight.value).dp
        val markerSize = min(cell.value * 0.8f, 44f).coerceAtLeast(32f).dp
        // Effective size once zoom is applied — what the finger and the eye actually get.
        val effectiveCell = cell * viewport.scale

        Box(
            modifier = Modifier
                .size(planWidth, planHeight)
                .graphicsLayer {
                    scaleX = viewport.scale
                    scaleY = viewport.scale
                    translationX = viewport.offset.x
                    translationY = viewport.offset.y
                },
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(colors.surfaceVariant))
            }

            GridDots(
                rows = state.gridRows,
                cols = state.gridCols,
                occupied = if (showMarkers) {
                    state.markers.map { it.gridX to it.gridY }.toSet()
                } else {
                    emptySet()
                },
                // Surroundings recede while a device is selected, so the marker the user
                // is working with is the only thing with any contrast near it.
                dimmed = selectedDeviceId != null,
            )

            if (showMarkers) {
                state.markers.forEach { marker ->
                    MarkerCell(
                        marker = marker,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        markerSize = markerSize,
                        showLabel = effectiveCell >= LabelThreshold,
                        selected = marker.deviceId == selectedDeviceId,
                        dimmed = dimmed,
                        onClick = { onSelectMarker(marker) },
                        onLongClick = { onLongPressMarker(marker) },
                    )
                }
            }
        }
    }
}

/**
 * One marker in its cell, with its label beneath.
 *
 * Labels truncate at ten characters and disappear below a 40 dp cell. A name rendered into
 * four pixels of space is not information, and twenty of them turn the plan into a smear —
 * zooming in brings them back, which is the point of zoom.
 */
@Composable
private fun MarkerCell(
    marker: MarkerUiState,
    cellWidth: Dp,
    cellHeight: Dp,
    markerSize: Dp,
    showLabel: Boolean,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    Box(
        modifier = Modifier
            .offset(x = cellWidth * marker.gridX, y = cellHeight * marker.gridY)
            .size(cellWidth, cellHeight)
            .alpha(if (dimmed) 0.6f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .pointerInput(marker.deviceId) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { onLongClick() },
                        )
                    }
                    // Replaces the marker's own description with the fuller one the screen
                    // prompt specifies, including where on the grid it sits — a screen
                    // reader user cannot see that a marker is in the top-left corner.
                    .clearAndSetSemantics {
                        contentDescription = marker.spokenDescription
                        role = Role.Button
                    },
            ) {
                DeviceMarker(
                    type = marker.type,
                    state = marker.state,
                    deviceName = marker.name,
                    hazardActive = marker.hazardActive,
                    selected = selected,
                    size = markerSize,
                    channelBadge = marker.channelBadge,
                    pendingWrite = marker.pendingWrite,
                    externalChangeToken = marker.externalChangeToken,
                )
            }

            if (showLabel) {
                Text(
                    text = marker.name.take(MaxLabelChars),
                    style = AppType.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    // The marker above already carries the full spoken description; the
                    // label is the same fact truncated, and reading both aloud is noise.
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * The grid, as dots.
 *
 * Drawn in one [Canvas] rather than as composables: a 20 × 20 grid is 400 cells, and 400
 * layout nodes to draw 400 specks would cost more than the plan itself.
 */
@Composable
private fun GridDots(
    rows: Int,
    cols: Int,
    occupied: Set<Pair<Int, Int>>,
    dimmed: Boolean,
) {
    val colors = SmartHomeTheme.colors
    val alpha = if (dimmed) 0.15f else 0.40f

    Canvas(Modifier.fillMaxSize()) {
        if (rows <= 0 || cols <= 0) return@Canvas
        val cellW = size.width / cols
        val cellH = size.height / rows
        val radius = 1.dp.toPx()

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (x to y in occupied) continue
                drawCircle(
                    color = colors.outline.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(
                        x = cellW * (x + 0.5f),
                        y = cellH * (y + 0.5f),
                    ),
                )
            }
        }
    }
}

/** Below this effective cell size a label is a smear rather than a name. */
private val LabelThreshold = 40.dp

private const val MaxLabelChars = 10
