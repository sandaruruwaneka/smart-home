package com.smarthome.control.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Iron
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Outlet
import androidx.compose.material.icons.rounded.ToggleOff
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Device status, matching the `devices.status` enum in SCHEMA.md exactly.
 *
 * The Firestore contract stores these as UPPERCASE strings, so [name] round-trips.
 */
enum class DeviceState {
    ON,
    OFF,
    ERROR,
    DISCONNECTED;

    /**
     * The badge text. Section 8.1 requires an icon *and* a text label — never colour
     * alone — so this string is never optional.
     */
    val label: String get() = name

    /**
     * Shape carries the state as well as colour, per section 11. These four are
     * distinguishable in monochrome: a filled bolt, a hollow power ring, a triangle, a
     * struck-through cloud.
     */
    val icon: ImageVector
        get() = when (this) {
            ON -> Icons.Rounded.Bolt
            OFF -> Icons.Outlined.PowerSettingsNew
            ERROR -> Icons.Rounded.Warning
            DISCONNECTED -> Icons.Rounded.CloudOff
        }

    /**
     * Spoken description. Section 12: name things by what the person sees, not by how
     * the system stores them, so a screen reader says "offline", not "DISCONNECTED".
     */
    val spoken: String
        get() = when (this) {
            ON -> "on"
            OFF -> "off"
            ERROR -> "in error"
            DISCONNECTED -> "offline"
        }
}

/**
 * Device type, matching the `devices.type` enum in SCHEMA.md.
 *
 * [APPLIANCE] is the hazard class: it is the only type carrying `max_on_duration`, and
 * therefore the only type that can earn the reserved pulse in section 8.2.
 */
enum class DeviceType {
    OUTLET,
    MULTI_SWITCH,
    LIGHT,
    APPLIANCE,
    CAMERA;

    /** Filled weight for active states (section 9). */
    val filledIcon: ImageVector
        get() = when (this) {
            OUTLET -> Icons.Rounded.Outlet
            MULTI_SWITCH -> Icons.Rounded.ToggleOn
            LIGHT -> Icons.Rounded.Lightbulb
            APPLIANCE -> Icons.Rounded.Iron
            CAMERA -> Icons.Rounded.Videocam
        }

    /** Outlined weight for inactive states (section 9). */
    val outlinedIcon: ImageVector
        get() = when (this) {
            OUTLET -> Icons.Outlined.Outlet
            MULTI_SWITCH -> Icons.Rounded.ToggleOff
            LIGHT -> Icons.Outlined.Lightbulb
            APPLIANCE -> Icons.Rounded.Iron
            CAMERA -> Icons.Outlined.Videocam
        }

    fun icon(state: DeviceState): ImageVector =
        if (state == DeviceState.ON) filledIcon else outlinedIcon

    /** Label for the type itself, for content descriptions and pickers. */
    val label: String
        get() = when (this) {
            OUTLET -> "Outlet"
            MULTI_SWITCH -> "Switch bank"
            LIGHT -> "Light"
            APPLIANCE -> "Appliance"
            CAMERA -> "Camera"
        }

    /**
     * Whether this type can breach a maximum on time. Only appliances carry
     * `config.max_on_duration`, and only they are eligible for the pulse.
     */
    val isHazardClass: Boolean get() = this == APPLIANCE
}
