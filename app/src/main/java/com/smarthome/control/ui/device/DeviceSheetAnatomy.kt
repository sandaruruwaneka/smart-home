package com.smarthome.control.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * The five-slot anatomy every device control sheet is built from — the annotated diagram
 * asked for by screen prompt 04 section 11.
 *
 * It is a preview rather than a document because the three sheets that inherit it are
 * written in this package, and a diagram that lives beside the code is one that gets looked
 * at when the code changes. Slot three is the only one that varies; everything else is
 * fixed, and any structural drift between the four sheets reads as sloppiness across the
 * whole app rather than as a local decision.
 */
@Composable
private fun DeviceSheetAnatomy(dark: Boolean = true) {
    SmartHomeTheme(darkTheme = dark) {
        val colors = SmartHomeTheme.colors

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Device sheet anatomy",
                style = AppType.display,
                color = colors.textPrimary,
            )
            Text(
                text = "Fixed slot order. Only slot 3 changes between the four sheets.",
                style = AppType.body,
                color = colors.textSecondary,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
                    .background(colors.surface, AppShapes.bottomSheet)
                    .padding(Spacing.md),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(colors.outline, RoundedCornerShape(percent = 50)),
                    )

                    Slots.forEach { slot ->
                        SlotBand(
                            index = slot.index,
                            name = slot.name,
                            detail = slot.detail,
                            height = slot.height,
                            variable = slot.variable,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotBand(
    index: Int,
    name: String,
    detail: String,
    height: Int,
    variable: Boolean,
) {
    val colors = SmartHomeTheme.colors
    // The variable slot is the one worth marking, so it gets the emphasis border every
    // other "look at this" in the app uses.
    val borderColor = if (variable) colors.stateOn else colors.outline
    val borderWidth = if (variable) AppBorders.emphasis else AppBorders.hairline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(colors.surfaceVariant, AppShapes.card)
            .border(borderWidth, borderColor, AppShapes.card)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = AppType.numeric,
            color = if (variable) colors.stateOn else colors.textSecondary,
            modifier = Modifier.width(24.dp),
        )
        Column {
            Text(text = name, style = AppType.sectionHeader, color = colors.textPrimary)
            Text(text = detail, style = AppType.label, color = colors.textSecondary)
        }
    }
}

private data class SlotSpec(
    val index: Int,
    val name: String,
    val detail: String,
    val height: Int,
    val variable: Boolean = false,
)

private val Slots = listOf(
    SlotSpec(1, "Identity", "Name, location, overflow", 64),
    SlotSpec(2, "Primary control", "96 dp card, whole card tappable", 96),
    SlotSpec(
        index = 3,
        name = "Type-specific configuration",
        detail = "Empty for outlets · channels · duration · schedule",
        height = 72,
        variable = true,
    ),
    SlotSpec(4, "Usage", "Two StatCards, then the 24-hour timeline", 120),
    SlotSpec(5, "Metadata footer", "Last changed, relative under 24 h", 40),
)

@Preview(name = "Device sheet anatomy · dark", widthDp = 412, heightDp = 720)
@Composable
private fun DeviceSheetAnatomyDark() = DeviceSheetAnatomy(dark = true)

@Preview(name = "Device sheet anatomy · light", widthDp = 412, heightDp = 720)
@Composable
private fun DeviceSheetAnatomyLight() = DeviceSheetAnatomy(dark = false)
