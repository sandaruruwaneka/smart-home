package com.smarthome.control.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.smarthome.control.data.UsageEventFields
import com.smarthome.control.data.asIntOrNull

/**
 * One continuous ON period -- `usage_events/{event_id}`, SCHEMA.md section 7.
 *
 * Opened by whoever switches the device on and closed by whoever switches it off, which
 * may be the app, the simulator or the safety worker. That shared ownership is why the
 * open/close pair is always part of the same batch as the status change: a status write
 * that lands without its usage write leaves a period that can never be reconstructed.
 *
 * These are written from day one. The reporting requirement has nothing to display
 * without historical rows, and unlike most data they cannot be backfilled later -- the
 * time simply passed unrecorded.
 */
data class UsageEvent(
    val id: String,
    val ownerUid: String,
    val deviceId: String,
    /** The channel this period belongs to, or null for a single-channel device. */
    val channelId: String?,
    val startedAt: Timestamp?,
    /** Null while the device is still on. */
    val endedAt: Timestamp?,
    /**
     * Written on close, denormalised so Reports can sum a column instead of reading two
     * timestamps per row and subtracting.
     */
    val durationSeconds: Int?,
) {
    /** An open period: the device is still running and this row has no end yet. */
    val isOpen: Boolean get() = endedAt == null

    /**
     * How long this period ran, in seconds, treating an open period as running until
     * [nowMillis].
     *
     * Falls back to the timestamp difference when [durationSeconds] is absent, so a row
     * closed by a client that failed to denormalise still contributes its real length to
     * a report rather than counting as zero.
     */
    fun elapsedSeconds(nowMillis: Long): Long {
        val start = startedAt?.toDate()?.time ?: return 0L
        durationSeconds?.let { return it.toLong() }
        val end = endedAt?.toDate()?.time ?: nowMillis
        return ((end - start) / 1000L).coerceAtLeast(0L)
    }

    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): UsageEvent? {
            val ownerUid = snapshot.getString(UsageEventFields.OWNER_UID) ?: return null
            val deviceId = snapshot.getString(UsageEventFields.DEVICE_ID) ?: return null
            return UsageEvent(
                id = snapshot.id,
                ownerUid = ownerUid,
                deviceId = deviceId,
                channelId = snapshot.getString(UsageEventFields.CHANNEL_ID),
                startedAt = snapshot.getTimestamp(UsageEventFields.STARTED_AT),
                endedAt = snapshot.getTimestamp(UsageEventFields.ENDED_AT),
                durationSeconds = snapshot.get(UsageEventFields.DURATION_SECONDS).asIntOrNull(),
            )
        }
    }
}
