package com.smarthome.control.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.smarthome.control.data.AlertFields
import com.smarthome.control.data.toEnumOrNull

/**
 * A safety alert raised by the worker -- `alerts/{alert_id}`, SCHEMA.md section 8.
 *
 * Write-once from the app's point of view: the worker creates these, the app reads them
 * and sets [acknowledged]. The app never creates one, and once the worker is live the
 * rules deny client creates outright -- the Admin SDK bypasses rules, so nothing is lost.
 */
data class Alert(
    val id: String,
    val ownerUid: String,
    val deviceId: String,
    /**
     * Denormalised copy of the device's name at the time the alert fired.
     *
     * Two reasons it is stored rather than looked up: the alert list renders without an
     * N+1 read per row, and the alert still reads correctly after the device has been
     * renamed or deleted -- which is exactly what a user does after a hazard.
     */
    val deviceName: String,
    val floorId: String,
    val type: AlertType,
    /** Ready to display as written by the worker; the app does not compose alert copy. */
    val message: String,
    val createdAt: Timestamp?,
    val acknowledged: Boolean,
) {
    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): Alert? {
            val ownerUid = snapshot.getString(AlertFields.OWNER_UID) ?: return null
            // An alert of unknown type cannot be given the right severity or icon, and an
            // alert shown at the wrong severity is worse than one not shown at all.
            val type = snapshot.getString(AlertFields.TYPE).toEnumOrNull<AlertType>() ?: return null
            return Alert(
                id = snapshot.id,
                ownerUid = ownerUid,
                deviceId = snapshot.getString(AlertFields.DEVICE_ID).orEmpty(),
                deviceName = snapshot.getString(AlertFields.DEVICE_NAME).orEmpty(),
                floorId = snapshot.getString(AlertFields.FLOOR_ID).orEmpty(),
                type = type,
                message = snapshot.getString(AlertFields.MESSAGE).orEmpty(),
                createdAt = snapshot.getTimestamp(AlertFields.CREATED_AT),
                // Defaulting to unacknowledged is the safe direction: a missing field
                // keeps the banner up rather than silently clearing a hazard notice.
                acknowledged = snapshot.getBoolean(AlertFields.ACKNOWLEDGED) ?: false,
            )
        }
    }
}
