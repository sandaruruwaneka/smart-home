package com.smarthome.control.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
    /**
     * Hours in which the safety worker cut the device off, marked with a 2 dp `stateError`
     * tick along the top of the segment (screen prompt 07 section 7).
     *
     * This is what turns the bar from a usage chart into a record of the safety system
     * working, which is exactly the artefact worth having on screen during the demo.
     */
    cutoffHours: Set<Int> = emptySet(),
    /**
     * The scheduled window as (start, sweep) fractions of the day, outlined behind the bars
     * as a 1 dp `primary` dashed region (screen prompt 08 section 10).
     *
     * Intended against actual, in one graphic. When they line up the user sees the schedule
     * working; when they diverge they see exactly which hour somebody intervened in — which
     * is the same question the override chip answers, asked about the past instead of now.
     */
    scheduledWindow: Pair<Float, Float>? = null,
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

            // Drawn over the bars rather than under them: it is an outline, and an outline
            // hidden behind the thing it outlines is just a wasted pixel row. A window
            // crossing midnight becomes two segments here, which on a linear axis it
            // genuinely is -- the ring above is where it reads as one.
            scheduledWindow?.let { (start, sweep) ->
                val dashes = PathEffect.dashPathEffect(floatArrayOf(DashOn, DashOff))
                val startX = start.coerceIn(0f, 1f) * size.width
                val width = sweep.coerceIn(0f, 1f) * size.width
                val segments = if (startX + width <= size.width) {
                    listOf(startX to width)
                } else {
                    listOf(startX to (size.width - startX), 0f to (startX + width - size.width))
                }
                segments.forEach { (x, w) ->
                    if (w <= 0f) return@forEach
                    drawRect(
                        color = colors.primary,
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        style = Stroke(width = OutlineWidth.toPx(), pathEffect = dashes),
                    )
                }
            }

            // Drawn last so a tick is never buried under the amber it sits on.
            val tickHeight = TickHeight.toPx()
            cutoffHours.forEach { hour ->
                if (hour !in 0 until HoursInDay) return@forEach
                drawRect(
                    color = colors.stateError,
                    topLeft = Offset(hour * hourWidth, 0f),
                    size = Size(hourWidth, tickHeight),
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

/**
 * The same day, one band per channel, sharing one axis.
 *
 * This is where the multi-channel model pays for itself visually: the fan running afternoons
 * above the ceiling light running evenings is a fact you can read in about a second, and a
 * single merged bar would throw it away entirely.
 *
 * Bands are 10 dp with 2 dp between them — thin enough that a five-gang stack still fits
 * above the fold, thick enough that a single busy hour is not a hairline. Labels truncate at
 * eight characters, which is a column width rather than a name: the row is identified by its
 * position in the same order as the channel list directly above it.
 *
 * @param bands in plate order, the same order the channel rows are in.
 */
@Composable
fun StackedDayTimeline(
    bands: List<TimelineBand>,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    if (bands.isEmpty() || bands.any { it.hourFractions.size != HoursInDay }) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BandGap),
    ) {
        bands.forEach { band ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // One sentence per band (section 11), rather than one for the stack:
                    // "the fan ran four hours" is the useful unit, not "the unit ran nine".
                    .clearAndSetSemantics { contentDescription = band.spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = band.label.take(MaxLabelChars),
                    style = AppType.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.width(LabelWidth),
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(BandHeight)
                        .clip(RoundedCornerShape(3.dp)),
                ) {
                    drawRect(color = colors.surfaceVariant)
                    val hourWidth = size.width / HoursInDay
                    band.hourFractions.forEachIndexed { hour, fraction ->
                        if (fraction <= 0f) return@forEachIndexed
                        drawRect(
                            color = colors.stateOn,
                            topLeft = Offset(hour * hourWidth, 0f),
                            size = Size(hourWidth * fraction.coerceIn(0f, 1f), size.height),
                        )
                    }
                }
            }
        }

        // One axis for the whole stack, inset by the label column so the hours line up with
        // the bands rather than with the row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Spacer(modifier = Modifier.width(LabelWidth))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisLabels.forEach { label ->
                    Text(text = label, style = AppType.label, color = colors.textSecondary)
                }
            }
        }
    }
}

private val BarHeight = 32.dp
private val TickHeight = 2.dp
private val OutlineWidth = 1.dp
private const val DashOn = 6f
private const val DashOff = 4f
private val BandHeight = 10.dp
private val BandGap = 2.dp
private val LabelWidth = 64.dp

/** Section 6 truncates band labels to eight characters. */
private const val MaxLabelChars = 8

private val AxisLabels = listOf("12a", "6a", "12p", "6p", "12a")
