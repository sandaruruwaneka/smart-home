package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
 * @param depleting draws the arc as time *left* rather than time used, so the ring empties
 *   as the countdown runs down. Screen prompt 07 section 4 requires it on the hazard sheet,
 *   where the ring is the screen's headline and a filling arc reads backwards — the eye
 *   takes a shrinking arc as "running out" without being told. The floor dashboard's chips
 *   keep the filling arc, which reads as "how far through" at 28 dp where there is no room
 *   for a number.
 * @param label the word under the figure. `left` at chip size, `remaining` on the sheet.
 * @param pulse the slow two-second breath from the master prompt, applied in the final
 *   tenth. It is the only pulse in the app and section 4 says this is where it belongs, so
 *   it is off by default and every other caller leaves it off.
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
    depleting: Boolean = false,
    label: String = "left",
    pulse: Boolean = false,
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

    // The master prompt's slow breath, spent here and nowhere else. Suppressed under
    // reduced motion, where the red is left standing rather than moving.
    val transition = rememberInfiniteTransition(label = "CountdownRing pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse && inFinalTenth && !reducedMotion) PulseFloor else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "CountdownRing pulse alpha",
    )

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
            // From twelve o'clock, clockwise. Filling with elapsed time by default;
            // emptying with the time left when the caller asks for a depleting ring.
            drawArc(
                color = animatedColor.copy(alpha = animatedColor.alpha * pulseAlpha),
                startAngle = -90f,
                sweepAngle = 360f * (if (depleting) 1f - animatedFraction else animatedFraction),
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
                    text = label,
                    style = AppType.label,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** Master prompt section 10 — the two-second breath, and how far down it dips. */
private const val PulseMillis = 1000
private const val PulseFloor = 0.45f

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
