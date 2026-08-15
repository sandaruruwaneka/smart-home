package com.smarthome.control.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.smarthome.control.data.DeviceFields
import com.smarthome.control.data.asIntOrNull
import com.smarthome.control.data.toEnumOrNull
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType

/**
 * A placed device -- `devices/{device_id}`, SCHEMA.md section 4.
 *
 * Top-level and flat, carrying [ownerUid] and [floorId] rather than nesting under either.
 * Nesting was rejected because the safety worker needs one collection-group query across
 * every device in the system, and flat collections make that a single query instead of a
 * fan-out per user.
 */
data class Device(
    val id: String,
    val ownerUid: String,
    val floorId: String,
    val type: DeviceType,
    val name: String,
    /** Column, 0-based, always less than the floor's `grid_cols`. */
    val gridX: Int,
    /** Row, 0-based, always less than the floor's `grid_rows`. */
    val gridY: Int,
    val status: DeviceState,
    /**
     * When the device last became `ON`, and null in every other state.
     *
     * The safety worker reads *only* this field to decide cutoffs, so it must never be
     * left stale: a device switched off with this still set is one the worker will
     * eventually decide has been on for hours. Every write that changes [status] sets or
     * clears it in the same operation -- which is why no repository method exposes a way
     * to write one without the other.
     */
    val turnedOnAt: Timestamp?,
    val lastChangedAt: Timestamp?,
    /**
     * Present for alert copy and demo debugging only.
     *
     * Not the input to the external-change flash; see [com.smarthome.control.data.Live].
     */
    val lastChangedBy: ChangeSource?,
    val config: DeviceConfig,
) {
    /** Non-null exactly when this is an appliance, and therefore subject to the cutoff. */
    val applianceConfig: DeviceConfig.Appliance? get() = config as? DeviceConfig.Appliance

    /** Non-null exactly when this is a light, and therefore schedulable. */
    val lightConfig: DeviceConfig.Light? get() = config as? DeviceConfig.Light

    /** Non-null exactly when this is a switch bank, and therefore has channels. */
    val multiSwitchConfig: DeviceConfig.MultiSwitch? get() = config as? DeviceConfig.MultiSwitch

    /** Non-null exactly when this is a camera, and therefore has stream URIs. */
    val cameraConfig: DeviceConfig.Camera? get() = config as? DeviceConfig.Camera

    /**
     * Whether this device is currently running a countdown the user should see.
     *
     * True only for an appliance that is on and has a start time. A device that is on
     * with no [turnedOnAt] is a contract violation somewhere upstream; the UI shows no
     * countdown rather than an invented one.
     */
    val hasActiveCountdown: Boolean
        get() = applianceConfig != null && status == DeviceState.ON && turnedOnAt != null

    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): Device? {
            val ownerUid = snapshot.getString(DeviceFields.OWNER_UID) ?: return null
            val floorId = snapshot.getString(DeviceFields.FLOOR_ID) ?: return null
            // An unrecognised type has no config shape, no icon and no controls, so there
            // is nothing useful to render. This is the case that makes a newer simulator
            // build's devices vanish from an older app rather than crash it.
            val type = snapshot.getString(DeviceFields.TYPE).toEnumOrNull<DeviceType>()
                ?: return null

            @Suppress("UNCHECKED_CAST")
            val rawConfig = snapshot.get(DeviceFields.CONFIG) as? Map<String, Any?>
            val config = DeviceConfig.fromMap(type, rawConfig) ?: return null

            return Device(
                id = snapshot.id,
                ownerUid = ownerUid,
                floorId = floorId,
                type = type,
                name = snapshot.getString(DeviceFields.NAME).orEmpty(),
                gridX = snapshot.get(DeviceFields.GRID_X).asIntOrNull() ?: 0,
                gridY = snapshot.get(DeviceFields.GRID_Y).asIntOrNull() ?: 0,
                // An unreadable status is a device the app cannot vouch for. DISCONNECTED
                // is the honest rendering of that, and it is the one state with no
                // controls attached, so it cannot invite an action that would not work.
                status = snapshot.getString(DeviceFields.STATUS).toEnumOrNull<DeviceState>()
                    ?: DeviceState.DISCONNECTED,
                turnedOnAt = snapshot.getTimestamp(DeviceFields.TURNED_ON_AT),
                lastChangedAt = snapshot.getTimestamp(DeviceFields.LAST_CHANGED_AT),
                lastChangedBy = snapshot.getString(DeviceFields.LAST_CHANGED_BY)
                    .toEnumOrNull<ChangeSource>(),
                config = config,
            )
        }
    }
}
