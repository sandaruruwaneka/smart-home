package com.smarthome.control.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * The section 13 deliverable, as a runnable app.
 *
 * Four sheets: the colour swatches, the type scale, the ten components in every state,
 * and the priority-hierarchy comparison. Screens from section 14 are not here yet — this
 * is the design system on its own, which is what the prompt asks for.
 *
 * The theme toggle in the app bar is not a product feature. Light theme is a required
 * secondary (section 2) and the fastest way to find a token that only works in dark is to
 * flip between the two while looking at the same component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemGallery() {
    // Dark is primary, so the gallery opens dark regardless of the system setting.
    var dark by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }

    val sheets = listOf<Pair<String, @Composable () -> Unit>>(
        "Colour" to { ColourSheet() },
        "Type" to { TypeScaleSheet() },
        "Components" to { ComponentSheet() },
        "Hierarchy" to { PriorityHierarchySheet() },
    )

    SmartHomeTheme(darkTheme = dark) {
        val colors = SmartHomeTheme.colors
        Scaffold(
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text("Design system", style = AppType.display) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                    ),
                    actions = {
                        IconButton(onClick = { dark = !dark }) {
                            Icon(
                                imageVector = if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (dark) {
                                    "Switch to light theme"
                                } else {
                                    "Switch to dark theme"
                                },
                                tint = colors.textSecondary,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = tab,
                    containerColor = colors.background,
                    contentColor = colors.primary,
                    edgePadding = Spacing.md,
                ) {
                    sheets.forEachIndexed { index, (title, _) ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title, style = AppType.label) },
                            selectedContentColor = colors.primary,
                            unselectedContentColor = colors.textSecondary,
                        )
                    }
                }
                sheets[tab].second()
            }
        }
    }
}

/**
 * Shared page frame for the four sheets: a scrolling column at screen padding.
 *
 * [LazyColumn] rather than a scrolling [Column] because the component sheet is long and
 * every marker on it runs its own animation — composing all of them at once to measure a
 * scroll container wastes work on the states nobody is looking at.
 */
@Composable
internal fun GallerySheet(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            top = Spacing.md,
            bottom = Spacing.xl,
        ),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

/** A labelled group within a sheet. */
@Composable
internal fun GallerySection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SmartHomeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = AppType.sectionHeader, color = colors.textPrimary)
        if (subtitle != null) {
            Text(subtitle, style = AppType.label, color = colors.textSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
    }
}

/** A single component specimen with its state named beneath it. */
@Composable
internal fun Specimen(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = SmartHomeTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        content()
        Text(label, style = AppType.label, color = colors.textSecondary)
    }
}

/** One row of the colour swatch sheet: the chip, the token name, and its hex value. */
@Composable
internal fun SwatchRow(
    name: String,
    hex: String,
    use: String,
    color: androidx.compose.ui.graphics.Color,
) {
    val colors = SmartHomeTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppCard(
                modifier = Modifier.size(40.dp),
                shape = com.smarthome.control.ui.theme.AppShapes.chip,
                color = color,
            ) {}
            Column(Modifier.weight(1f)) {
                Text(name, style = AppType.body, color = colors.textPrimary)
                Text(use, style = AppType.label, color = colors.textSecondary)
            }
            Text(hex, style = AppType.label, color = colors.textSecondary)
        }
    }
}
