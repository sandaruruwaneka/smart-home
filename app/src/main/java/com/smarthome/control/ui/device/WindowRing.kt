package com.smarthome.control.ui.device

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.stateChangeSpec
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 24-hour clock face with the scheduled window drawn on it.
 *
 * ### Why a ring and not a bar
 *
 * The most common lighting schedule there is runs evening to morning — `22:00` to `06:00` —
 * and that window crosses midnight. On a linear bar it has to be drawn as two disconnected
 * segments at opposite ends, which reads as two schedules and confuses the user at exactly
 * the moment they are trying to check one. On a ring it is a single continuous arc through
 * 12 o'clock. The shape is chosen by the data, not for decoration.
 *
 * ### What it answers without being read
 *
 * The arc is full strength while now falls inside the window and drops to 40 % outside, so
 * "is this light supposed to be on right now?" is answered by brightness alone, before any
 * number is parsed. The `primary` hand marks now.
 *
 * @param startFraction the ON edge as a fraction of the day, midnight at 12 o'clock.
 * @param sweepFraction the window's width as a fraction of the day. Already wrapped, so a
 *   window through midnight is one positive sweep rather than a negative one.
 * @param overrideFraction where a manual override began, drawn as a break in the arc
 *   (section 6). Null when the schedule is in charge.
 * @param spokenDescription the whole ring in one sentence; the ring itself is decorative
 *   for traversal, since nothing about it survives being described geometrically.
 */
@Composable
fun WindowRing(
    startFraction: Float?,
    sweepFraction: Float?,
    nowFraction: Float,
    enabled: Boolean,
    insideWindow: Boolean,
    spokenDescription: String,
    modifier: Modifier = Modifier,
    overrideFraction: Float? = null,
    dimmed: Boolean = false,
    centreContent: @Composable () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors

    // The whole reason the ring exists is that it redraws while the time picker moves, so
    // the edges animate rather than jump — the user is meant to watch the window change.
    val animatedStart by animateFloatAsState(
        targetValue = startFraction ?: 0f,
        animationSpec = stateChangeSpec(),
        label = "window start",
    )
    val animatedSweep by animateFloatAsState(
        targetValue = sweepFraction ?: 0f,
        animationSpec = stateChangeSpec(),
        label = "window sweep",
    )

    val arcAlpha = when {
        !enabled -> DisabledRingAlpha
        insideWindow -> 1f
        else -> OutsideWindowAlpha
    }

    Box(
        modifier = modifier
            .size(RingSize)
            .clearAndSetSemantics { contentDescription = spokenDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(RingSize)) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = colors.surfaceVariant.copy(alpha = if (dimmed) DisabledRingAlpha else 1f),
                startAngle = 0f,
                sweepAngle = FullCircle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            if (enabled && sweepFraction != null && sweepFraction > 0f && startFraction != null) {
                drawArc(
                    color = colors.stateOn.copy(alpha = arcAlpha),
                    // Midnight sits at 12 o'clock, and Compose measures from 3 o'clock.
                    startAngle = animatedStart * FullCircle + MidnightOffset,
                    sweepAngle = animatedSweep * FullCircle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }

            // The override break: a gap punched in the arc at the moment the user took
            // manual control, so the ring shows *when* the schedule stopped being obeyed.
            overrideFraction?.let { fraction ->
                drawArc(
                    color = colors.primary,
                    startAngle = fraction * FullCircle + MidnightOffset,
                    sweepAngle = BreakSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }

            drawNowHand(nowFraction = nowFraction, colors.primary, stroke)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            centreContent()
        }

        // Hour ticks at 6 / 12 / 18. Not 0 — midnight is where the ring starts and the eye
        // already reads the top as the top.
        Canvas(modifier = Modifier.size(RingSize)) {
            val labelRadius = size.minDimension / 2f - RingStroke.toPx() - TickInset.toPx()
            HourTicks.forEach { hour ->
                val angle = (hour / 24f) * FullCircle + MidnightOffset
                val radians = Math.toRadians(angle.toDouble())
                drawCircle(
                    color = colors.textSecondary,
                    radius = TickRadius.toPx(),
                    center = center + Offset(
                        (cos(radians) * labelRadius).toFloat(),
                        (sin(radians) * labelRadius).toFloat(),
                    ),
                )
            }
        }
    }
}

/** The 2 dp `primary` hand marking now, drawn from just inside the track to its outer edge. */
private fun DrawScope.drawNowHand(nowFraction: Float, color: androidx.compose.ui.graphics.Color, stroke: Float) {
    val angle = nowFraction * FullCircle + MidnightOffset
    val radians = Math.toRadians(angle.toDouble())
    val outer = size.minDimension / 2f
    val inner = outer - stroke * HandLengthMultiplier

    drawLine(
        color = color,
        start = center + Offset((cos(radians) * inner).toFloat(), (sin(radians) * inner).toFloat()),
        end = center + Offset((cos(radians) * outer).toFloat(), (sin(radians) * outer).toFloat()),
        strokeWidth = HandWidth,
        cap = StrokeCap.Round,
    )
}

/** The `18:30` / `23:00` pair printed inside the ring. */
@Composable
fun RingTimes(onTime: String?, offTime: String?, enabled: Boolean) {
    val colors = SmartHomeTheme.colors
    val tint = if (enabled) colors.textPrimary else colors.textSecondary

    Text(text = onTime ?: "--:--", style = AppType.numeric, color = tint)
    Text(text = offTime ?: "--:--", style = AppType.numeric, color = colors.textSecondary)
}

private val RingSize = 140.dp
private val RingStroke = 12.dp
private val TickRadius = 2.dp
private val TickInset = 6.dp
private const val FullCircle = 360f

/** Compose measures arcs from 3 o'clock; the day starts at 12. */
private const val MidnightOffset = -90f
private const val OutsideWindowAlpha = 0.4f
private const val DisabledRingAlpha = 0.3f
private const val BreakSweep = 3f
private const val HandWidth = 5f
private const val HandLengthMultiplier = 1.6f
private val HourTicks = listOf(6, 12, 18)
