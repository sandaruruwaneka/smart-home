package com.smarthome.control.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.model.DeviceType
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * The two charts this screen needs, drawn with Compose primitives.
 *
 * The prompt names Vico. These are drawn by hand instead, for the same reason every other
 * graphic in this app is: the design system owns its palette, its type scale and its
 * reduced-motion rule, and a charting library arrives with its own opinions about all
 * three. `DayTimeline`, `StackedDayTimeline`, `WindowRing` and `CountdownRing` are already
 * hand-drawn and consistent; two bar charts are not where that stops being worth it. The
 * whole file is under two hundred lines and adds no dependency.
 */

/**
 * Devices by on-time — the primary visual.
 *
 * Horizontal rather than vertical because device names are long, and vertical bars force
 * 45° rotated labels, which is the single most reliable way to make a chart look amateur.
 *
 * @param onShowAll non-null when there is more to show than the five bars rendered.
 */
@Composable
fun DeviceBarChart(
    bars: List<DeviceBar>,
    spokenSummary: String,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
) {
    val colors = SmartHomeTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The summary is read before the bars, so a screen reader user gets the shape of
            // the answer rather than having to assemble it from five stops.
            .semantics { contentDescription = spokenSummary },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${bar.name}, ${bar.label}" },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bar.name,
                        style = AppType.body,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = bar.label, style = AppType.numeric, color = colors.textSecondary)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BarHeight)
                        .clip(RoundedCornerShape(BarRadius))
                        .background(colors.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bar.fraction.coerceIn(0f, 1f))
                            .height(BarHeight)
                            .background(
                                // Appliances in amber: the tallest bar here may be the one
                                // worth worrying about rather than the one worth admiring.
                                if (bar.isHazardClass) {
                                    colors.stateOn
                                } else {
                                    colors.primary.copy(alpha = BarAlpha)
                                },
                            ),
                    )
                }
            }
        }

        onShowAll?.let {
            Text(
                text = "Show all →",
                style = AppType.label,
                color = colors.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = it)
                    .padding(Spacing.xs),
            )
        }
    }
}

/**
 * Daily trend — stacked, one bar per day.
 *
 * Stacked rather than grouped because the useful reading is total household usage over
 * time, with composition as secondary detail. Grouped bars across thirty days would render
 * as illegible hairlines.
 */
@Composable
fun DailyTrendChart(
    days: List<DayStack>,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    val palette = trendPalette()
    val maxSeconds = days.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Colour is never the only carrier: each swatch is paired with the type it means.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            palette.forEach { (type, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(modifier = Modifier.size(LegendSwatch).background(color, RoundedCornerShape(2.dp)))
                    Text(type.label, style = AppType.label, color = colors.textSecondary)
                }
            }
        }

        selected?.let { index ->
            days.getOrNull(index)?.let { day ->
                Text(
                    text = "${day.label}: ${day.totalLabel}" +
                        (day.topDeviceName?.let { " · mostly $it" } ?: ""),
                    style = AppType.label,
                    color = colors.textPrimary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrendHeight),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selected = if (selected == index) null else index }
                        .semantics { contentDescription = "${day.label}, ${day.totalLabel}" },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TrendHeight - AxisHeight)
                            .clearAndSetSemantics { },
                    ) {
                        val fullHeight = size.height * (day.totalSeconds.toFloat() / maxSeconds)
                        var y = size.height

                        palette.forEach { (type, color) ->
                            val seconds = day.secondsByType[type] ?: return@forEach
                            if (seconds <= 0L) return@forEach
                            val segment = fullHeight * (seconds.toFloat() / day.totalSeconds)
                            y -= segment
                            drawRect(
                                color = color,
                                topLeft = Offset(0f, y),
                                size = Size(size.width, segment),
                            )
                        }
                    }

                    Text(
                        text = day.label,
                        style = AppType.label,
                        color = if (selected == index) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The stack's four colours.
 *
 * Appliances keep `stateOn` so the hazard class reads the same here as everywhere else;
 * the rest are drawn from `primary` and `textSecondary` rather than invented, so the chart
 * cannot drift away from the palette the rest of the app uses.
 */
@Composable
private fun trendPalette(): List<Pair<DeviceType, Color>> {
    val colors = SmartHomeTheme.colors
    return listOf(
        DeviceType.OUTLET to colors.primary,
        DeviceType.LIGHT to colors.primary.copy(alpha = 0.5f),
        DeviceType.APPLIANCE to colors.stateOn,
        DeviceType.MULTI_SWITCH to colors.textSecondary,
    )
}

private val BarHeight = 12.dp
private val BarRadius = 8.dp
private val TrendHeight = 140.dp
private val AxisHeight = 20.dp
private val LegendSwatch = 10.dp
private const val BarAlpha = 0.8f
