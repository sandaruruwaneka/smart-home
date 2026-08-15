package com.smarthome.control.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.smarthome.control.data.ChannelFields
import com.smarthome.control.data.asIntOrNull
import com.smarthome.control.data.toEnumOrNull
import com.smarthome.control.ui.model.DeviceState

/**
 * One switch on a gang plate -- `devices/{id}/channels/{id}`, SCHEMA.md section 6.
 *
 * Exists only under `MULTI_SWITCH` devices, and is created at placement time in the same
 * batch as its parent, so a switch bank is never briefly channel-less.
 */
data class Channel(
    val id: String,
    /**
     * 0-based position on the physical wall plate.
     *
     * Rows are ordered by this and never alphabetically: the list on screen has to match
     * the order of the switches the user is looking at, or "the second one" means two
     * different things to the app and to the person in the room.
     */
    val index: Int,
    /** May be empty on the wire; use [displayName] to render. */
    val name: String,
    /**
     * `ON`, `OFF` or `ERROR` only.
     *
     * Never `DISCONNECTED` -- a single channel cannot lose connectivity independently of
     * the gang box it is wired into, so that state belongs to the parent alone.
     */
    val status: DeviceState,
    val turnedOnAt: Timestamp?,
    val lastChangedAt: Timestamp?,
) {
    /** The contract allows an empty name; the app falls back to the plate position. */
    val displayName: String get() = name.ifBlank { "Channel ${index + 1}" }

    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): Channel? {
            // Without an index there is no defensible place to put the row, and guessing
            // would reorder the user's wall plate.
            val index = snapshot.get(ChannelFields.INDEX).asIntOrNull() ?: return null
            val status = snapshot.getString(ChannelFields.STATUS).toEnumOrNull<DeviceState>()
                ?: DeviceState.ERROR
            return Channel(
                id = snapshot.id,
                index = index,
                name = snapshot.getString(ChannelFields.NAME).orEmpty(),
                // DISCONNECTED is out of contract here. If it ever arrives, the channel is
                // shown as faulted rather than dropped: hiding a switch the user can see on
                // the wall is worse than showing it in a state that needs attention.
                status = if (status == DeviceState.DISCONNECTED) DeviceState.ERROR else status,
                turnedOnAt = snapshot.getTimestamp(ChannelFields.TURNED_ON_AT),
                lastChangedAt = snapshot.getTimestamp(ChannelFields.LAST_CHANGED_AT),
            )
        }
    }
}

/**
 * The parent device's derived `status` for a switch bank -- SCHEMA.md section 4.
 *
 * The contract's clauses resolve in this order, and both the app and the simulator must
 * compute them identically or the two clients will disagree about the same document:
 *
 *  1. any channel `ON`    -> `ON`
 *  2. otherwise any `ERROR` -> `ERROR`
 *  3. otherwise (all `OFF`) -> `OFF`
 *
 * `DISCONNECTED` is not derivable from channels at all -- it means the whole unit is
 * unreachable, which no individual channel can report -- so it is passed in via
 * [unitUnreachable] rather than inferred.
 *
 * Returns null when [channels] is empty, which means "not derivable yet" rather than
 * "off". Callers must not write a status in that case: an empty list is what a
 * subcollection listener looks like before its first snapshot arrives, and treating that
 * as all-off would switch a live unit off on screen and then write that guess back.
 */
fun deriveMultiSwitchStatus(
    channels: List<Channel>,
    unitUnreachable: Boolean = false,
): DeviceState? = when {
    unitUnreachable -> DeviceState.DISCONNECTED
    channels.isEmpty() -> null
    channels.any { it.status == DeviceState.ON } -> DeviceState.ON
    channels.any { it.status == DeviceState.ERROR } -> DeviceState.ERROR
    else -> DeviceState.OFF
}
