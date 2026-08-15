package com.smarthome.control.ui.model

/**
 * How loudly the interface should speak about something. Master prompt section 5.
 *
 * The device-state colours say *what* a device is doing. This scale is orthogonal to
 * them: it says how much of the user's attention the thing deserves. An ordinary light
 * being ON is [NORMAL], not [ATTENTION] — over-escalation destroys the hierarchy faster
 * than under-escalation does.
 */
enum class PriorityTier {

    /**
     * Standard surface, textPrimary, no accent border.
     *
     * Ordinary device rows and markers in any state, floor cards, usage stats.
     */
    NORMAL,

    /**
     * surfaceVariant fill, 1 dp stateOn left bar, icon tinted amber.
     *
     * Hazard-class devices currently ON, devices approaching their duration limit,
     * scheduled devices running now.
     */
    ATTENTION,

    /**
     * Full-width banner, stateError 1 dp border over surface, red icon, always positioned
     * above all other content in its container.
     *
     * Automatic safety cutoffs, devices in ERROR, duration limit breached.
     *
     * Two rules travel with this tier and are enforced by the components that use it:
     * only one Critical element may be visible at a time — multiples collapse into a
     * single banner carrying a count — and Critical elements are never dismissible by
     * swipe. They clear when the underlying condition clears, or when the user
     * acknowledges explicitly.
     */
    CRITICAL,
}

/**
 * The tier a device row should be rendered at, derived rather than stored.
 *
 * Centralised here so that no screen has to re-derive it and quietly disagree with
 * another. Everything this reads is either on the device document or computed from it
 * (SCHEMA.md section 14) — nothing new is persisted.
 *
 * @param state the device's current status
 * @param type the device type; only [DeviceType.APPLIANCE] is hazard class
 * @param elapsedSeconds seconds since `turned_on_at`, null when the device is not on
 * @param maxOnSeconds `config.max_on_duration`, null for types that do not carry one
 * @param runningOnSchedule true when a scheduled light is inside its window right now
 */
fun priorityTierOf(
    state: DeviceState,
    type: DeviceType,
    elapsedSeconds: Long? = null,
    maxOnSeconds: Long? = null,
    runningOnSchedule: Boolean = false,
): PriorityTier = when {
    // Critical: the device is faulted, or it has already outrun its limit. The second
    // case is visible in the app for up to 60 s before the safety worker's tick catches
    // it (SCHEMA.md section 10.1), and the user should see it during that window.
    state == DeviceState.ERROR -> PriorityTier.CRITICAL
    maxOnSeconds != null && elapsedSeconds != null && elapsedSeconds >= maxOnSeconds ->
        PriorityTier.CRITICAL

    // Attention: a hazard-class device drawing power, a scheduled device running now.
    state == DeviceState.ON && type.isHazardClass -> PriorityTier.ATTENTION
    state == DeviceState.ON && runningOnSchedule -> PriorityTier.ATTENTION

    else -> PriorityTier.NORMAL
}

/**
 * True once a running device is inside the last tenth of its allowance — the point at
 * which the CountdownRing turns red (section 8.5) and the marker begins to pulse.
 */
fun isApproachingLimit(elapsedSeconds: Long?, maxOnSeconds: Long?): Boolean {
    if (elapsedSeconds == null || maxOnSeconds == null || maxOnSeconds <= 0L) return false
    val remaining = maxOnSeconds - elapsedSeconds
    return remaining > 0L && remaining <= maxOnSeconds / 10
}
