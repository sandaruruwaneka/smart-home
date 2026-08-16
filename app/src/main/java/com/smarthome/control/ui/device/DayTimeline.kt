package com.smarthome.control.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Twenty-four hours of a binary device, drawn as a bar.
 *
 * Screen prompt 04 section 5 puts this in place of the master prompt's generic sparkline,
 * and the reason is worth keeping written down: a line chart of a value that is only ever
 * 0 or 1 is a rectangle pretending to be a graph. Time runs left to right, so a single
 * unbroken run reads as one unbroken block and six separate switches read as six — which
 * is the only question anybody asks of a day's usage at a glance.
 *
 * Each hour is filled from its own left edge in proportion to the minutes spent on inside
 * it, so a period that straddles the hour draws as one continuous block rather than as two
 * segments with a gap between them.
 *
 * @param hourFractions 24 values in 0..1. Anything else is ignored rather than scaled — a
 *   list of the wrong length is a bug upstream, and stretching it would hide that.
 */
@Composable
fun DayTimeline(
    hourFractions: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    if (hourFractions.size != HoursInDay) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            // One node, one sentence. Twenty-four unlabelled segments would otherwise be
            // twenty-four stops on the way past a graphic (section 10).
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            drawRect(color = colors.surfaceVariant)

            val hourWidth = size.width / HoursInDay
            hourFractions.forEachIndexed { hour, fraction ->
                if (fraction <= 0f) return@forEachIndexed
                drawRect(
                    color = colors.stateOn,
                    topLeft = Offset(hour * hourWidth, 0f),
                    size = Size(hourWidth * fraction.coerceIn(0f, 1f), size.height),
                )
            }
        }

        // Five labels at 0, 6, 12, 18 and 24 hours — evenly spaced, so SpaceBetween puts
        // each one exactly over the hour it names.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisLabels.forEach { label ->
                Text(text = label, style = AppType.label, color = colors.textSecondary)
            }
        }
    }
}

private val BarHeight = 32.dp

private val AxisLabels = listOf("12a", "6a", "12p", "6p", "12a")
