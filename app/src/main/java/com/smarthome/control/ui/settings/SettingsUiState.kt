package com.smarthome.control.ui.settings

import java.time.ZoneId

/**
 * Everything the settings screen draws, which is deliberately not much.
 *
 * The temptation with a settings screen is to fill it — notification preferences, units,
 * refresh intervals — and every one of those is a feature somebody then has to make work.
 * Four rows that all function beat twelve rows where eight are inert.
 *
 * One row here is not cosmetic. The timezone drives the scheduler (SCHEMA.md section 9), so
 * if it is wrong every light schedule fires at the wrong hour. That single dependency is
 * why this screen exists at all rather than living in an overflow menu.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val email: String = "",
    val timezone: String = "",
    val appearance: Appearance = Appearance.Dark,
    val versionLabel: String = "",
    val isSavingTimezone: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
    /** Set once the session is gone, so the host can send the user back to Login. */
    val isSignedOut: Boolean = false,
    val phoneZone: String = ZoneId.systemDefault().id,
) {
    /**
     * `Your phone is set to Asia/Dubai. Schedules will use Asia/Colombo.`
     *
     * Shown only on a mismatch, and in `stateOn` rather than `stateError`: this is not a
     * fault, it is a fact somebody travelling would want to know before wondering why their
     * porch light came on at the wrong time.
     */
    val timezoneMismatch: String?
        get() = if (timezone.isNotBlank() && timezone != phoneZone) {
            "Your phone is set to $phoneZone. Schedules will use $timezone."
        } else {
            null
        }

    /** `Schedules will now run on Asia/Colombo time. Times you've already set won't change.` */
    fun timezoneConfirmation(newZone: String): String =
        "Schedules will now run on $newZone time. Times you've already set won't change."

    /** `Timezone, Asia/Colombo. Double tap to change.` — section 6. */
    val timezoneSpoken: String get() = "Timezone, $timezone. Double tap to change."
}

/**
 * Dark by default, per the master prompt.
 *
 * `Follow system` exists but is not the default: the app is designed dark, every artboard
 * is dark, and a first launch that lands on white because the phone happens to be in light
 * mode shows the user a theme nobody designed against.
 */
enum class Appearance(val label: String) {
    Dark("Dark"),
    Light("Light"),
    System("Follow system"),
}

/** Every IANA zone, sorted, filtered by whatever the user has typed. */
fun timezoneOptions(query: String): List<String> {
    val all = ZoneId.getAvailableZoneIds().sorted()
    if (query.isBlank()) return all
    return all.filter { it.contains(query, ignoreCase = true) }
}
