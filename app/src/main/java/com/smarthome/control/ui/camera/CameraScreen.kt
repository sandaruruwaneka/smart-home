package com.smarthome.control.ui.camera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil3.compose.rememberAsyncImagePainter
import com.smarthome.control.ui.common.rememberIsOffline
import com.smarthome.control.ui.components.EmptyState
import com.smarthome.control.ui.device.RenameDeviceDialog
import com.smarthome.control.ui.device.SheetConfirmDialog
import com.smarthome.control.ui.device.SheetMenuItem
import com.smarthome.control.ui.device.SheetOverflowButton
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion

/**
 * Screen prompt 10 — the camera view.
 *
 * Cameras are the one device type the user *watches* rather than controls. There is no
 * toggle here and no state to change: the screen is a viewport plus honest metadata about
 * what is in it. The honesty is the point — see [cameraPresentation].
 */
@Composable
fun CameraScreen(
    deviceId: String,
    onBack: () -> Unit,
    onOpenCamera: (deviceId: String) -> Unit,
    onViewWall: () -> Unit,
    onMoveDevice: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel(
        factory = CameraViewModel.factory(deviceId),
        key = deviceId,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isMissing) { if (state.isMissing) onBack() }

    CameraContent(
        state = state,
        isOffline = rememberIsOffline(),
        onBack = onBack,
        onOpenCamera = onOpenCamera,
        onViewWall = onViewWall,
        onMove = { onMoveDevice(deviceId) },
        onRename = viewModel::rename,
        onUpdateUris = viewModel::updateUris,
        onDelete = viewModel::delete,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraContent(
    state: CameraUiState,
    isOffline: Boolean,
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    onViewWall: () -> Unit,
    onMove: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateUris: (String, String) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Artboards force a phase; the real screen reads it off the player. */
    previewPhase: PlaybackPhase? = null,
) {
    val colors = SmartHomeTheme.colors
    var fullscreen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingUris by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var snapshotFetchedAt by remember { mutableStateOf(state.nowMillis) }

    val playback = rememberCameraPlayback(state.streamUri)
    val phase = previewPhase ?: playback.phase
    val presentation = cameraPresentation(
        status = state.status,
        phase = phase,
        hasStream = state.hasStream,
        hasSnapshot = state.hasSnapshot,
    )
    val snapshotAge = if (presentation.showsSnapshot) {
        snapshotAgeLine(snapshotFetchedAt, state.nowMillis)
    } else {
        null
    }

    if (fullscreen) {
        // Immersive: chrome gone, viewport edge to edge, nothing else competing for the
        // display. The 16:9 feed is the only thing the user came for.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .clickable { fullscreen = false },
            contentAlignment = Alignment.Center,
        ) {
            Viewport(
                state = state,
                presentation = presentation,
                playback = playback,
                snapshotAge = snapshotAge,
                isOffline = isOffline,
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            IconButton(
                onClick = { fullscreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            ) {
                Icon(Icons.Rounded.FullscreenExit, "Exit fullscreen", tint = colors.textPrimary)
            }
        }
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = colors.textPrimary)
                    }
                },
                title = {
                    Text(
                        state.deviceName,
                        style = AppType.display,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                ),
                actions = {
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Rounded.Fullscreen, "Fullscreen", tint = colors.textSecondary)
                    }
                    SheetOverflowButton { dismiss ->
                        SheetMenuItem("View all cameras") { dismiss(); onViewWall() }
                        SheetMenuItem("Rename") { dismiss(); renaming = true }
                        SheetMenuItem("Move to another cell") { dismiss(); onMove() }
                        SheetMenuItem("Change stream URL") { dismiss(); editingUris = true }
                        SheetMenuItem("Delete device", destructive = true) {
                            dismiss(); confirmingDelete = true
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Viewport(
                state = state,
                presentation = presentation,
                playback = playback,
                snapshotAge = snapshotAge,
                isOffline = isOffline,
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                Text(state.deviceName, style = AppType.sectionHeader, color = colors.textPrimary)
                Text(state.locationLine, style = AppType.label, color = colors.textSecondary)
            }

            state.actionError?.let {
                Text(
                    it,
                    style = AppType.body,
                    color = colors.stateError,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }

            // Prominent only in snapshot mode, where it is the only way to get a newer
            // frame. Over a live stream it would be a button that does nothing visible.
            if (presentation.showsSnapshot) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = Spacing.screenHorizontal)
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, AppShapes.card)
                        .clickable { snapshotFetchedAt = System.currentTimeMillis() }
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.primary)
                    Text("Refresh", style = AppType.body, color = colors.textPrimary)
                }
            }

            if (state.otherCameras.isNotEmpty()) {
                Text(
                    "OTHER CAMERAS",
                    style = AppType.label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    state.otherCameras.forEach { thumb ->
                        CameraTile(
                            thumb = thumb,
                            onClick = { onOpenCamera(thumb.deviceId) },
                            modifier = Modifier.width(ThumbWidth),
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        RenameDeviceDialog(
            currentName = state.deviceName,
            label = "Camera name",
            title = "Rename camera",
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRename(it) },
        )
    }

    if (editingUris) {
        CameraUrisDialog(
            stream = state.streamUri.orEmpty(),
            snapshot = state.snapshotUri.orEmpty(),
            onDismiss = { editingUris = false },
            onConfirm = { s, n -> editingUris = false; onUpdateUris(s, n) },
        )
    }

    if (confirmingDelete) {
        SheetConfirmDialog(
            title = "Delete ${state.deviceName}?",
            body = "This removes the camera from its floor plan. This can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; onDelete() },
        )
    }
}

/**
 * The viewport: 16:9, letterboxed, never cropped or stretched.
 *
 * Overlays sit on a scrim gradient at the top and bottom only. A full-surface scrim would
 * dim the image the user opened the screen to look at.
 */
@Composable
private fun Viewport(
    state: CameraUiState,
    presentation: CameraPresentation,
    playback: CameraPlayback,
    snapshotAge: String?,
    isOffline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .background(colors.background)
            .semantics {
                contentDescription = cameraSpoken(
                    deviceName = state.deviceName,
                    floorName = state.floorName,
                    badge = presentation.badge,
                    snapshotAge = snapshotAge,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            presentation.showsPlayer && playback.player != null -> AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        player = playback.player
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (presentation.holdsLastFrame) StaleAlpha else 1f),
            )

            presentation.showsSnapshot -> Image(
                painter = rememberAsyncImagePainter(state.snapshotUri),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isOffline) StaleAlpha else 1f),
            )

            // Never a black rectangle. Black reads as a failed feed, and the whole point of
            // `Connecting…` is to say that nothing has failed yet.
            else -> Box(
                modifier = Modifier.fillMaxSize().background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(IconSize),
                    )
                    Text(
                        presentation.statusLine ?: "Can't reach this camera",
                        style = AppType.body,
                        color = colors.textSecondary,
                    )
                    state.lastSeenLine?.let {
                        Text(it, style = AppType.label, color = colors.textSecondary)
                    }
                    androidx.compose.material3.TextButton(onClick = onRetry) {
                        Text("Retry", style = AppType.label, color = colors.primary)
                    }
                }
            }
        }

        // Top and bottom scrims, so the overlays stay readable over a bright frame without
        // dimming the middle of the picture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.background.copy(alpha = ScrimAlpha),
                        0.25f to androidx.compose.ui.graphics.Color.Transparent,
                        0.75f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to colors.background.copy(alpha = ScrimAlpha),
                    ),
                )
                .clearAndSetSemantics { },
        )

        presentation.badge?.let { badge ->
            SourceBadge(
                badge = badge,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md),
            )
        }

        if (presentation.statusLine != null && presentation.showsPlayer) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize),
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
                Text(presentation.statusLine, style = AppType.label, color = colors.textSecondary)
            }
        }

        val bottomRight = when {
            presentation.badge == CameraBadge.Live -> formatElapsed(playback.elapsedMillis)
            snapshotAge != null -> snapshotAge
            else -> null
        }
        bottomRight?.let {
            Text(
                text = it,
                style = AppType.label,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
            )
        }

        if (isOffline) {
            Text(
                text = "You're offline. Showing the last image received.",
                style = AppType.label,
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.md),
            )
        }
    }
}

/**
 * The badge, and the one pulse it is allowed.
 *
 * Only `LIVE` pulses, because only `LIVE` is claiming something is happening right now.
 * Under reduced motion the dot goes solid — it still says live, it just stops moving.
 */
@Composable
private fun SourceBadge(badge: CameraBadge, modifier: Modifier = Modifier) {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()

    val tint = when (badge) {
        CameraBadge.Live -> colors.stateOn
        CameraBadge.Snapshot -> colors.textSecondary
        CameraBadge.Offline -> colors.stateDisconnected
    }

    val transition = rememberInfiniteTransition(label = "live pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (badge == CameraBadge.Live && !reducedMotion) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(PulseMillis), RepeatMode.Reverse),
        label = "live dot",
    )

    Row(
        modifier = modifier
            .background(colors.background.copy(alpha = BadgeScrim), RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (badge == CameraBadge.Live) {
            Box(modifier = Modifier.size(DotSize).alpha(dotAlpha).background(tint, CircleShape))
        }
        Text(text = badge.label, style = AppType.label, color = tint)
    }
}

/** A tile on the strip or the wall. Never a broken-image glyph, never a blank rectangle. */
@Composable
internal fun CameraTile(
    thumb: CameraThumb,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${thumb.name}, ${thumb.floorName}" +
                    if (thumb.isReachable) "" else ", unreachable"
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb.isReachable && thumb.snapshotUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(thumb.snapshotUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = if (thumb.isReachable) Icons.Rounded.Videocam else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }

            if (!thumb.isReachable) {
                Text(
                    text = DeviceState.DISCONNECTED.label,
                    style = AppType.label,
                    color = colors.stateError,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.xs)
                        .background(
                            colors.background.copy(alpha = BadgeScrim),
                            RoundedCornerShape(percent = 50),
                        )
                        .padding(horizontal = Spacing.xs),
                )
            }
        }

        Text(
            text = thumb.name,
            style = AppType.label,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
}

@Composable
private fun CameraUrisDialog(
    stream: String,
    snapshot: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var streamValue by remember { mutableStateOf(stream) }
    var snapshotValue by remember { mutableStateOf(snapshot) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text("Change stream URL", style = AppType.sectionHeader) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                com.smarthome.control.ui.components.LabeledTextField(
                    label = "Stream URL",
                    value = streamValue,
                    onValueChange = { streamValue = it },
                    // The brief permits mock feeds, so say so rather than letting somebody
                    // think a real camera is required.
                    helperText = "Paste an HLS stream or image URL.",
                    helperVisibleWhenUnfocused = true,
                )
                com.smarthome.control.ui.components.LabeledTextField(
                    label = "Snapshot URL",
                    value = snapshotValue,
                    onValueChange = { snapshotValue = it },
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(streamValue, snapshotValue) },
                enabled = streamValue.isNotBlank() || snapshotValue.isNotBlank(),
            ) {
                Text("Save", style = AppType.label, color = colors.primary)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

private val ThumbWidth = 128.dp
private val DotSize = 8.dp
private val IconSize = 32.dp
private const val ScrimAlpha = 0.4f
private const val BadgeScrim = 0.6f
private const val StaleAlpha = 0.6f
private const val PulseMillis = 1_000

// ---------------------------------------------------------------------------
// Artboards — the section 12 deliverable
// ---------------------------------------------------------------------------

private const val PreviewNow = 1_755_267_300_000L

private val PreviewState = CameraUiState(
    isLoading = false,
    deviceId = "c1",
    deviceName = "Front Door",
    locationLine = "Ground Floor · R1 C4",
    floorName = "Ground Floor",
    status = DeviceState.OFF,
    streamUri = "https://example/live.m3u8",
    snapshotUri = "https://example/still.jpg",
    lastSeenMillis = PreviewNow - 12 * 60_000L,
    otherCameras = listOf(
        CameraThumb("c2", "Back Door", "ground", "Ground Floor", null, true),
        CameraThumb("c3", "Garage", "ground", "Ground Floor", null, true),
        CameraThumb("c4", "Hall", "first", "First Floor", null, false),
    ),
    nowMillis = PreviewNow,
)

@Composable
private fun Artboard(
    state: CameraUiState,
    phase: PlaybackPhase,
    dark: Boolean = true,
    isOffline: Boolean = false,
) {
    SmartHomeTheme(darkTheme = dark) {
        CameraContent(
            state = state,
            isOffline = isOffline,
            onBack = {},
            onOpenCamera = {},
            onViewWall = {},
            onMove = {},
            onRename = {},
            onUpdateUris = { _, _ -> },
            onDelete = {},
            onRetry = {},
            previewPhase = phase,
        )
    }
}

@Preview(name = "Camera · streaming", widthDp = 412, heightDp = 915)
@Composable
private fun CameraLivePreview() = Artboard(PreviewState, PlaybackPhase.Playing)

@Preview(name = "Camera · snapshot mode", widthDp = 412, heightDp = 915)
@Composable
private fun CameraSnapshotPreview() = Artboard(PreviewState, PlaybackPhase.Failed)

@Preview(name = "Camera · connecting", widthDp = 412, heightDp = 915)
@Composable
private fun CameraConnectingPreview() = Artboard(
    PreviewState.copy(snapshotUri = null),
    PlaybackPhase.Connecting,
)

@Preview(name = "Camera · reconnecting", widthDp = 412, heightDp = 915)
@Composable
private fun CameraStalledPreview() = Artboard(
    PreviewState.copy(snapshotUri = null),
    PlaybackPhase.Stalled,
)

@Preview(name = "Camera · unreachable", widthDp = 412, heightDp = 915)
@Composable
private fun CameraUnreachablePreview() = Artboard(
    PreviewState.copy(status = DeviceState.DISCONNECTED),
    PlaybackPhase.Failed,
)

@Preview(name = "Camera · app offline", widthDp = 412, heightDp = 915)
@Composable
private fun CameraOfflinePreview() = Artboard(PreviewState, PlaybackPhase.Failed, isOffline = true)

@Preview(name = "Camera · streaming, light", widthDp = 412, heightDp = 915)
@Composable
private fun CameraLightPreview() = Artboard(PreviewState, PlaybackPhase.Playing, dark = false)
