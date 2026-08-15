package com.smarthome.control.data.model

/**
 * Who last wrote a device document -- the `last_changed_by` enum in SCHEMA.md section 4.
 *
 * Read only for alert copy and for debugging during the demo. It is deliberately *not*
 * the input to the external-change flash; see [com.smarthome.control.data.Live] for why
 * that races and what replaces it.
 */
enum class ChangeSource {
    /** This Kotlin app. */
    APP,

    /** The React simulator, which authenticates as the same user (section 11). */
    SIMULATOR,

    /** The safety cutoff or scheduler job (section 10). */
    WORKER,
}

/**
 * Why the safety worker raised an alert -- the `alerts.type` enum in SCHEMA.md section 8.
 *
 * The app never creates these values, only reads and acknowledges them.
 */
enum class AlertType {
    /** An appliance stayed on past its `config.max_on_duration` (section 10.1). */
    MAX_DURATION_EXCEEDED,

    /** A device reported `ERROR` status. */
    DEVICE_ERROR,
}
