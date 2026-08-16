package com.smarthome.control.ui.common

import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.model.AlertType
import com.smarthome.control.ui.components.AlertType as AlertIconType

/**
 * How an alert is put into words, in one place.
 *
 * The worker writes `message` — `Maximum active time exceeded` — which answers *why*. It
 * deliberately does not name the device or say what became of it: that is the app's half
 * of the sentence, and the app is the one that knows how it is about to be laid out.
 *
 * Both the floor list and the floor dashboard raise a banner for the same alert, and they
 * have to say the same thing about it. Two screens each composing "what happened" from the
 * type and the device name is two screens that agree until somebody edits one of them.
 */
fun Alert.causeLine(): String = when (type) {
    AlertType.MAX_DURATION_EXCEEDED -> "$deviceName switched off automatically"
    AlertType.DEVICE_ERROR -> "$deviceName reported a fault"
}

/**
 * The data layer's alert type mapped onto the icon vocabulary the components use.
 *
 * The two enums carry the same cases for different reasons — one is the Firestore
 * contract, the other is what an [com.smarthome.control.ui.components.AlertRow] draws —
 * and this is the single seam between them.
 */
fun Alert.iconType(): AlertIconType = when (type) {
    AlertType.MAX_DURATION_EXCEEDED -> AlertIconType.MAX_DURATION_EXCEEDED
    AlertType.DEVICE_ERROR -> AlertIconType.DEVICE_ERROR
}

/**
 * [Alert.createdAt] is null for the instant between a local write and the server
 * materialising its timestamp. Treating that as "now" is right: an alert with no server
 * time yet is one that has only just been written.
 */
fun Alert.createdAtMillisOr(nowMillis: Long): Long = createdAt?.toDate()?.time ?: nowMillis
