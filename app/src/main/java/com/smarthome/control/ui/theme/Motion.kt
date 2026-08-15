package com.smarthome.control.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/*
 * Master prompt section 10 — motion.
 *
 * Three numbers and one curve, named once. Every screen that animates reads them from
 * here, because a 200 ms cross-fade on one screen and a 250 ms one on the next is the
 * kind of inconsistency nobody can point at but everybody feels.
 */
object Motion {

    /** State change: colour cross-fades, value swaps, a status dot appearing. */
    const val StateChangeMillis = 200

    /** Sheet and banner entry. */
    const val EntryMillis = 300

    /**
     * Material 3's emphasised decelerate curve — fast out of the gate, long settle.
     *
     * Used for anything that arrives from off-screen. The long tail is what makes an
     * AlertBanner read as having *landed* rather than having been snapped into place.
     */
    val EmphasisedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/**
 * [Motion.StateChangeMillis], or zero when the user has asked the system to remove
 * animation.
 *
 * Section 10 requires transitions to become instant under reduced motion rather than
 * merely shorter. Zero-duration `tween`s do exactly that and keep every call site free of
 * an `if`.
 */
@Composable
fun stateChangeDuration(): Int = if (rememberReducedMotion()) 0 else Motion.StateChangeMillis

/** [Motion.EntryMillis] under the same reduced-motion rule as [stateChangeDuration]. */
@Composable
fun entryDuration(): Int = if (rememberReducedMotion()) 0 else Motion.EntryMillis

/** The standard state-change spec, honouring reduced motion. */
@Composable
fun <T> stateChangeSpec(): FiniteAnimationSpec<T> = tween(stateChangeDuration())

/** The entry spec — emphasised decelerate — honouring reduced motion. */
@Composable
fun <T> entrySpec(): FiniteAnimationSpec<T> =
    tween(entryDuration(), easing = Motion.EmphasisedDecelerate)
