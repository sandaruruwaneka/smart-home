package com.smarthome.control.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.components.DeviceMarker
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 13, item 1 — the colour swatch sheet, with every token named.
 *
 * Reads its values from the live theme rather than from a hard-coded list, so the sheet
 * cannot drift out of date. Flipping the app bar's theme toggle re-renders it with the
 * light palette and the same token names.
 */
@Composable
fun ColourSheet() {
    val colors = SmartHomeTheme.colors
    val theme = if (colors.isDark) "Dark theme (primary)" else "Light theme (secondary)"

    GallerySheet {
        GallerySection(theme, "Surfaces, interaction, and text") {
            SwatchRow("background", colors.background.hex(), "Screen background", colors.background)
            SwatchRow("surface", colors.surface.hex(), "Cards, sheets", colors.surface)
            SwatchRow(
                "surfaceVariant",
                colors.surfaceVariant.hex(),
                "Nested surfaces, input fields",
                colors.surfaceVariant,
            )
            SwatchRow("outline", colors.outline.hex(), "Card borders, dividers", colors.outline)
            SwatchRow(
                "primary",
                colors.primary.hex(),
                "Interactive elements, FABs, selection",
                colors.primary,
            )
            SwatchRow("onPrimary", colors.onPrimary.hex(), "Text/icons on primary", colors.onPrimary)
            SwatchRow("textPrimary", colors.textPrimary.hex(), "Body and headings", colors.textPrimary)
            SwatchRow(
                "textSecondary",
                colors.textSecondary.hex(),
                "Labels, captions, metadata",
                colors.textSecondary,
            )
        }

        GallerySection(
            "Device state",
            "Semantic. Used identically on every screen and component.",
        ) {
            SwatchRow(
                "stateOn",
                colors.stateOn.hex(),
                "Warm amber — energised, drawing power",
                colors.stateOn,
            )
            SwatchRow("stateOff", colors.stateOff.hex(), "Muted slate — low emphasis", colors.stateOff)
            SwatchRow("stateError", colors.stateError.hex(), "Red", colors.stateError)
            SwatchRow(
                "stateDisconnected",
                colors.stateDisconnected.hex(),
                "Desaturated, always paired with dashed outline",
                colors.stateDisconnected,
            )
        }

        GallerySection("Why ON is not green") {
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    DeviceMarker(DeviceType.APPLIANCE, DeviceState.ON, hazardActive = true)
                    Text(
                        text = "In this app \"on\" is not always good — an iron left on is a " +
                            "hazard. The ON colour reads as active and warm, never as safe.",
                        style = AppType.body,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** Formats a colour back to the `#RRGGBB` notation the design document uses. */
private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@Preview(name = "Colour sheet · dark", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ColourSheetPreviewDark() {
    SmartHomeTheme(darkTheme = true) { ColourSheet() }
}

@Preview(name = "Colour sheet · light", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ColourSheetPreviewLight() {
    SmartHomeTheme(darkTheme = false) { ColourSheet() }
}
