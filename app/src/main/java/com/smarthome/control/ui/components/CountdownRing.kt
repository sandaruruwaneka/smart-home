package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion

/**
 * Section 8.5 — CountdownRing.
 *
 * Circular progress showing elapsed against `config.max_on_duration`. Amber fill,
 * switching to stateError in the final 10 %. The centre shows remaining time in tabular
 * figures.
 *
 * Tabular figures are not a nicety here. This number updates once a second; with
 * proportional digits the string re-centres itself every time a `1` becomes a `0`, and a
 * countdown that jitters horizontally reads as a broken widget rather than as a timer.
 *
 * ### Colour change, not just fill
 *
 * The switch to red at 90 % is a second, independent signal from the arc length. A user
 * glancing at the ring from across the room cannot judge "is that arc 85 % or 92 %?", but
 * they can tell red from amber instantly. Arc length is the precise channel; colour is the
 * glanceable one.
 *
 * @param elapsedSeconds seconds since `turned_on_at`
 * @param maxOnSeconds `config.max_on_duration`. Values at or below zero render an empty
 *   ring rather than dividing by zero — an appliance without a limit should never reach
 *   this component, but a malformed document should not crash the sheet.
 */
@Composable
fun CountdownRing(
    elapsedSeconds: Long,
    maxOnSeconds: Long,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 8.dp,
    /**
     * False draws the arc alone.
     *
     * For the floor dashboard's hazard chips, where the ring is 28 dp and the remaining
     * time sits beside it rather than inside it — at that diameter the centre holds about
     * four pixels, and a countdown nobody can read is just a decoration.
     */
    showLabel: Boolean = true,
) {
    val colors = SmartHomeTheme.colors
    val reducedMotion = rememberReducedMotion()

    val fraction = if (maxOnSeconds <= 0L) {
        0f
    } else {
        (elapsedSeconds.toFloat() / maxOnSeconds.toFloat()).coerceIn(0f, 1f)
    }
    val remaining = (maxOnSeconds - elapsedSeconds).coerceAtLeast(0L)
    val inFinalTenth = fraction >= 0.9f

    val arcColor = if (inFinalTenth) colors.stateError else colors.stateOn
    val animatedColor by animateColorAsState(
        targetValue = arcColor,
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "CountdownRing colour",
    )
    // The sweep animates so a value arriving from a Firestore snapshot slides rather than
    // jumps. Under reduced motion it snaps, per section 10.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "CountdownRing sweep",
    )

    val spoken = if (remaining == 0L) {
        "Maximum on time reached"
    } else {
        "${formatDuration(remaining)} remaining of ${formatDuration(maxOnSeconds)}"
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Track first, so the remaining portion of the allowance stays visible as a
            // reference for how far through the device is.
            drawArc(
                color = colors.outline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            // Elapsed sweep, starting at twelve o'clock and running clockwise.
            drawArc(
                color = animatedColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        if (showLabel) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDuration(remaining),
                    style = AppType.numeric,
                    color = if (inFinalTenth) colors.stateError else colors.textPrimary,
                )
                Text(
                    text = "left",
                    style = AppType.label,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Formats seconds as `m:ss`, or `h:mm:ss` past an hour.
 *
 * Fixed-width by construction: minutes and seconds are always zero-padded, so combined
 * with tabular figures the string never changes width within a magnitude.
 */
internal fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "CountdownRing · dark", showBackground = true, backgroundColor = 0xFF0E1316)
@Composable
private fun CountdownRingPreviewDark() = GalleryPreview(dark = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CountdownRing(elapsedSeconds = 0, maxOnSeconds = 1800)
        CountdownRing(elapsedSeconds = 900, maxOnSeconds = 1800)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // Final 10 % — the arc turns red before the limit is reached, not after.
        CountdownRing(elapsedSeconds = 1700, maxOnSeconds = 1800)
        CountdownRing(elapsedSeconds = 1800, maxOnSeconds = 1800)
    }
    // Demo configuration from SCHEMA.md section 10.1: a 90 s limit so the cutoff fires
    // on camera.
    CountdownRing(elapsedSeconds = 45, maxOnSeconds = 90, size = 72.dp, strokeWidth = 6.dp)
}

@Preview(name = "CountdownRing · light", showBackground = true, backgroundColor = 0xFFF5F7F8)
@Composable
private fun CountdownRingPreviewLight() = GalleryPreview(dark = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CountdownRing(elapsedSeconds = 600, maxOnSeconds = 1800)
        CountdownRing(elapsedSeconds = 1750, maxOnSeconds = 1800)
    }
}
