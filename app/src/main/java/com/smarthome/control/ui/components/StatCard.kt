package com.smarthome.control.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Section 8.9 — StatCard.
 *
 * One usage metric: large numeric value, unit, label, small sparkline.
 *
 * The value uses tabular figures like every other live number in the app, even though a
 * report total updates far less often than a countdown. Consistency is the point — the
 * user learns one way that numbers look here.
 *
 * The sparkline is deliberately unlabelled and unscaled. It answers "is this going up or
 * down" and nothing more; anyone who needs the actual series opens the Reports screen.
 * Axes and gridlines on a 32 dp-tall graphic would be decoration, and section 3 rules
 * decoration out.
 *
 * @param trend the recent series, oldest first. Fewer than two points renders no line —
 *   a single reading has no trend, and drawing a flat line would imply one.
 */
@Composable
fun StatCard(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    trend: List<Float> = emptyList(),
) {
    val colors = SmartHomeTheme.colors

    AppCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value $unit"
        },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = AppType.numericLarge, color = colors.textPrimary)
                Text(
                    text = " $unit",
                    style = AppType.label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Text(text = label, style = AppType.label, color = colors.textSecondary)

            if (trend.size >= 2) {
                Sparkline(
                    values = trend,
                    color = colors.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                )
            }
        }
    }
}

/**
 * A minimal polyline. No axes, no fill, no points.
 *
 * A flat series would divide by a zero range, so that case is drawn as a centre line
 * rather than guarded against at the call site.
 */
@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = max - min
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        // Inset so the stroke's own width does not clip at the top and bottom edges.
        val inset = 2f
        val usableHeight = size.height - inset * 2

        val path = Path()
        values.forEachIndexed { index, value ->
            val normalised = if (range == 0f) 0.5f else (value - min) / range
            val x = index * stepX
            val y = inset + usableHeight * (1f - normalised)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "StatCard · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun StatCardPreviewDark() = GalleryPreview(dark = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatCard(
            value = "12.4",
            unit = "hrs",
            label = "Total on time this week",
            trend = listOf(2f, 3.5f, 1f, 4f, 3f, 5f, 4.2f),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = "38",
            unit = "switches",
            label = "Toggles this week",
            trend = listOf(6f, 4f, 7f, 5f, 8f, 3f, 5f),
            modifier = Modifier.weight(1f),
        )
    }
    // No trend data yet, and a flat series — both must render without collapsing.
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatCard(value = "0", unit = "hrs", label = "Iron", modifier = Modifier.weight(1f))
        StatCard(
            value = "3.0",
            unit = "hrs",
            label = "Porch light",
            trend = listOf(3f, 3f, 3f, 3f, 3f),
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "StatCard · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun StatCardPreviewLight() = GalleryPreview(dark = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatCard(
            value = "12.4",
            unit = "hrs",
            label = "Total on time this week",
            trend = listOf(2f, 3.5f, 1f, 4f, 3f, 5f, 4.2f),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = "38",
            unit = "switches",
            label = "Toggles this week",
            trend = listOf(6f, 4f, 7f, 5f, 8f, 3f, 5f),
            modifier = Modifier.weight(1f),
        )
    }
}
