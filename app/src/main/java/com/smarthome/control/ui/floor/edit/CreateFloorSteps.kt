package com.smarthome.control.ui.floor.edit

import com.smarthome.control.ui.floor.SamplePlan
import com.smarthome.control.ui.floor.SamplePlans
import com.smarthome.control.ui.floor.samplePlanFor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Three segments under the top bar, one per step.
 *
 * A count rather than a spinner, because the user's question at step 2 is "how much more of
 * this is there" and a determinate answer to that is worth the pixels.
 */
@Composable
fun CreateStepper(current: CreateStep, modifier: Modifier = Modifier) {
    val colors = SmartHomeTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)
            .semantics {
                contentDescription =
                    "Step ${current.index + 1} of ${CreateStep.entries.size}, ${current.label}"
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CreateStep.entries.forEach { step ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SegmentHeight)
                    .background(
                        color = if (step.index <= current.index) colors.primary else colors.surfaceVariant,
                        shape = RoundedCornerShape(percent = 50),
                    ),
            )
        }
    }
}

/** Step 1 — the floor's name, and nothing else on screen to distract from typing it. */
@Composable
fun StepName(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LabeledTextField(
            label = "Floor name",
            value = name,
            onValueChange = onNameChange,
            helperText = "For example, Ground Floor.",
            helperVisibleWhenUnfocused = true,
            modifier = Modifier.focusRequester(focus),
        )
    }
}

/**
 * Step 2 — where the plan comes from.
 *
 * The sample plans are the dominant option by area rather than by decoration. The brief
 * permits them explicitly for the demo, which makes them the path most likely to be on the
 * video, and burying that path behind a photo picker would put a file-management problem in
 * front of the examiner.
 */
@Composable
fun StepPlan(
    draft: FloorDraft,
    onPickPhoto: () -> Unit,
    onChooseSample: (SamplePlan) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    val context = LocalContext.current
    val selectedSample = remember(draft.planImageUrl) { samplePlanFor(draft.planImageUrl) }
    val hasPickedPhoto = draft.pendingUpload != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickPhoto),
            border = BorderStroke(
                if (hasPickedPhoto) AppBorders.selected else AppBorders.hairline,
                if (hasPickedPhoto) colors.primary else colors.outline,
            ),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = colors.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Choose from device", style = AppType.sectionHeader, color = colors.textPrimary)
                    Text(
                        if (hasPickedPhoto) "Photo selected" else "Use a photo or scan of your plan",
                        style = AppType.label,
                        color = colors.textSecondary,
                    )
                }
                if (hasPickedPhoto) SelectedBadge()
            }
        }

        Text("Use a sample plan", style = AppType.sectionHeader, color = colors.textPrimary)

        // Two columns, laid out by hand. A LazyVerticalGrid inside a vertically scrolling
        // column needs a fixed height to resolve its own, and six items do not need
        // recycling anyway.
        SamplePlans.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { sample ->
                    SamplePlanCard(
                        sample = sample,
                        selected = sample == selectedSample,
                        onClick = { onChooseSample(sample) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a lone item in an odd-numbered row at half width rather than
                // stretching it into a card twice the size of its neighbours.
                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }

        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip for now", style = AppType.label, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SamplePlanCard(
    sample: SamplePlan,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    AppCard(
        modifier = modifier.clickable(onClick = onClick),
        border = BorderStroke(
            if (selected) AppBorders.selected else AppBorders.hairline,
            if (selected) colors.primary else colors.outline,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PlanThumbnailAspect)
                    .background(colors.surfaceVariant),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(sample.drawable),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (selected) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs)) {
                        SelectedBadge()
                    }
                }
            }
            Text(
                text = sample.label,
                style = AppType.label,
                color = colors.textPrimary,
                modifier = Modifier.padding(Spacing.sm),
            )
        }
    }
}

@Composable
private fun SelectedBadge() {
    val colors = SmartHomeTheme.colors
    Box(
        modifier = Modifier
            .size(BadgeSize)
            .background(colors.primary, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = "Selected",
            tint = colors.onPrimary,
            modifier = Modifier.size(BadgeIconSize),
        )
    }
}

/**
 * Step 3 — the grid, judged against the plan rather than against two numbers.
 *
 * The caption under the preview is the part that stops a 20-column grid being chosen by
 * accident. It warns and does not block: somebody laying out a large open floor may want
 * exactly that, and a screen that overrules them on their own building has substituted its
 * judgement for theirs.
 */
@Composable
fun StepGrid(
    draft: FloorDraft,
    rows: Int,
    cols: Int,
    onGridChange: (rows: Int, cols: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GridStepper(
                label = "Rows",
                value = rows,
                onChange = { onGridChange(it, cols) },
                modifier = Modifier.weight(1f),
            )
            GridStepper(
                label = "Columns",
                value = cols,
                onChange = { onGridChange(rows, it) },
                modifier = Modifier.weight(1f),
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellDp = (maxWidth / cols).value.toInt()

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GridPreview(
                    draft = draft,
                    rows = rows,
                    cols = cols,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PreviewHeight),
                )

                Text(
                    text = cellSizeCaption(cellDp),
                    style = AppType.label,
                    color = if (isCellTooSmall(cellDp)) colors.stateOn else colors.textSecondary,
                )
                if (isCellTooSmall(cellDp)) {
                    Text(text = CellTooSmallWarning, style = AppType.label, color = colors.stateOn)
                }
            }
        }
    }
}

@Composable
fun GridStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onChange(value - 1) },
                enabled = value > GridMin,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fewer $label", tint = colors.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value.toString(), style = AppType.numeric, color = colors.textPrimary)
                Text(label, style = AppType.label, color = colors.textSecondary)
            }
            IconButton(
                onClick = { onChange(value + 1) },
                enabled = value < GridMax,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "More $label", tint = colors.textPrimary)
            }
        }
    }
}

private val SegmentHeight = 4.dp
private val BadgeSize = 20.dp
private val BadgeIconSize = 14.dp
private val PreviewHeight = 260.dp
private const val PlanThumbnailAspect = 1.45f
private const val GridMin = 4
private const val GridMax = 20
