package com.smarthome.control.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.ui.components.EmptyState
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Every camera in the home, two columns wide, grouped by floor.
 *
 * Worth building even though the brief only asks for "interface elements dedicated to
 * monitoring spaces": several tiles at once, one of them deliberately offline, is a strong
 * five seconds of demo footage and it makes the multi-floor data model visible in a way a
 * single feed cannot.
 *
 * The tiles are **stills, not live feeds**. Decoding six streams at once would drop frames
 * during exactly the moment somebody is filming, and a wall of stills answers the question
 * the wall exists for — is every camera reachable — just as well.
 */
@Composable
fun CameraWallScreen(
    onBack: () -> Unit,
    onOpenCamera: (deviceId: String) -> Unit,
    onGoToFloors: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel(factory = CameraViewModel.factory(null), key = "wall"),
) {
    val sections by viewModel.wall.collectAsStateWithLifecycle()

    CameraWallContent(
        sections = sections,
        onBack = onBack,
        onOpenCamera = onOpenCamera,
        onGoToFloors = onGoToFloors,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraWallContent(
    sections: List<CameraWallSection>,
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    onGoToFloors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

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
                title = { Text("Cameras", style = AppType.display) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        if (sections.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Videocam,
                    message = "No cameras yet. Place one on a floor plan to start monitoring.",
                    actionLabel = "Go to floors",
                    onAction = onGoToFloors,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            sections.forEach { section ->
                Text(
                    text = section.floorName.uppercase(),
                    style = AppType.label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )

                // Two columns by hand. A LazyVerticalGrid inside a scrolling column needs a
                // fixed height to resolve its own, and a home has a handful of cameras
                // rather than a feed worth recycling.
                section.cameras.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        pair.forEach { thumb ->
                            CameraTile(
                                thumb = thumb,
                                onClick = { onOpenCamera(thumb.deviceId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            }

            Box(modifier = Modifier.padding(bottom = Spacing.lg))
        }
    }
}

// ---------------------------------------------------------------------------
// Artboards
// ---------------------------------------------------------------------------

private val PreviewSections = listOf(
    CameraWallSection(
        floorName = "Ground Floor",
        cameras = listOf(
            CameraThumb("c1", "Front Door", "ground", "Ground Floor", null, true),
            CameraThumb("c2", "Back Door", "ground", "Ground Floor", null, true),
            CameraThumb("c3", "Garage", "ground", "Ground Floor", null, false),
        ),
    ),
    CameraWallSection(
        floorName = "First Floor",
        cameras = listOf(
            CameraThumb("c4", "Landing", "first", "First Floor", null, true),
        ),
    ),
)

@Preview(name = "Camera wall · one offline", widthDp = 412, heightDp = 915)
@Composable
private fun CameraWallPreview() = SmartHomeTheme(darkTheme = true) {
    CameraWallContent(PreviewSections, onBack = {}, onOpenCamera = {}, onGoToFloors = {})
}

@Preview(name = "Camera wall · empty", widthDp = 412, heightDp = 915)
@Composable
private fun CameraWallEmptyPreview() = SmartHomeTheme(darkTheme = true) {
    CameraWallContent(emptyList(), onBack = {}, onOpenCamera = {}, onGoToFloors = {})
}

@Preview(name = "Camera wall · light", widthDp = 412, heightDp = 915)
@Composable
private fun CameraWallLightPreview() = SmartHomeTheme(darkTheme = false) {
    CameraWallContent(PreviewSections, onBack = {}, onOpenCamera = {}, onGoToFloors = {})
}
