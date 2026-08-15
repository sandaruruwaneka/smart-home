package com.smarthome.control.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.smarthome.control.ui.components.AppCard
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 13, item 2 — the type scale sheet.
 *
 * Each specimen shows the style rendered at its real size next to the spec it implements,
 * so a mismatch between the document and the code is visible rather than theoretical.
 */
@Composable
fun TypeScaleSheet() {
    GallerySheet {
        GallerySection("Scale", "Inter, falling back to Roboto") {
            TypeSpecimen("Display / screen titles", "24 sp semibold", AppType.display, "Ground Floor")
            TypeSpecimen("Section headers", "16 sp medium", AppType.sectionHeader, "Active devices")
            TypeSpecimen("Body", "14 sp regular", AppType.body, "Maximum on time exceeded")
            TypeSpecimen("Labels / captions", "12 sp medium, +0.2 tracking", AppType.label, "7 devices · 2 active")
            TypeSpecimen("Numeric readouts", "20 sp medium, tabular", AppType.numeric, "0:58")
            TypeSpecimen("Large numerics", "32 sp semibold, tabular", AppType.numericLarge, "12.4")
        }

        GallerySection(
            "Tabular figures",
            "Mandatory on any live-updating number.",
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "Every digit occupies the same width, so a countdown holds its " +
                            "position as the numbers change. Proportional digits would make " +
                            "the readout shuffle sideways once a second, which reads as a " +
                            "rendering fault rather than as a clock.",
                        style = AppType.body,
                        color = SmartHomeTheme.colors.textSecondary,
                    )
                    // Successive values a real countdown passes through. With tabular
                    // figures the right edge of every line sits at the same x.
                    listOf("1:11", "1:10", "1:09", "0:08", "0:07").forEach {
                        Text(it, style = AppType.numeric, color = SmartHomeTheme.colors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSpecimen(name: String, spec: String, style: TextStyle, sample: String) {
    val colors = SmartHomeTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(sample, style = style, color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(name, style = AppType.label, color = colors.textSecondary)
                Text("·", style = AppType.label, color = colors.textSecondary)
                Text(spec, style = AppType.label, color = colors.textSecondary)
            }
        }
    }
}

@Preview(name = "Type scale · dark", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun TypeScaleSheetPreview() {
    SmartHomeTheme(darkTheme = true) { TypeScaleSheet() }
}
