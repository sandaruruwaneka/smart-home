package com.smarthome.control.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.smarthome.control.data.Collections
import com.smarthome.control.data.UsageEventFields
import com.smarthome.control.data.mapDocuments
import com.smarthome.control.data.model.UsageEvent
import com.smarthome.control.data.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Usage history -- `usage_events`, SCHEMA.md section 7.
 *
 * The write side of this class is deliberately all `*InBatch`: a usage event is never
 * opened or closed on its own, only alongside the status change that caused it. Exposing
 * a standalone `open()` would make it possible to switch a device on and lose the period,
 * and that loss is unrecoverable -- the time simply passed unrecorded.
 */
class UsageEventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val events = firestore.collection(Collections.USAGE_EVENTS)

    /**
     * Stages a new open period and returns its reference.
     *
     * `started_at` is a server timestamp, never the client clock: the safety worker
     * compares against server time, and a phone running a few minutes fast would produce
     * cutoffs that look arbitrary.
     */
    fun openInBatch(
        batch: WriteBatch,
        ownerUid: String,
        deviceId: String,
        channelId: String? = null,
    ): DocumentReference {
        val reference = events.document()
        batch.set(
            reference,
            mapOf(
                UsageEventFields.OWNER_UID to ownerUid,
                UsageEventFields.DEVICE_ID to deviceId,
                UsageEventFields.CHANNEL_ID to channelId,
                UsageEventFields.STARTED_AT to FieldValue.serverTimestamp(),
                UsageEventFields.ENDED_AT to null,
                UsageEventFields.DURATION_SECONDS to null,
            ),
        )
        return reference
    }

    /**
     * Stages the close of an open period.
     *
     * `ended_at` is a server timestamp, but `duration_seconds` cannot be: there is no way
     * to subtract two server-side sentinels in a single write. It is therefore computed
     * from the already-materialised `started_at` against the local clock, and is accurate
     * to whatever the device's clock skew is -- typically well under a second on a
     * network-synced phone.
     *
     * That is acceptable precisely because nothing safety-critical reads it. The cutoff
     * uses `turned_on_at` and server time only; this field exists so Reports can sum a
     * column instead of subtracting timestamps per row.
     */
    fun closeInBatch(batch: WriteBatch, event: UsageEvent, nowMillis: Long = System.currentTimeMillis()) {
        val startedAtMillis = event.startedAt?.toDate()?.time
        val duration = startedAtMillis?.let { ((nowMillis - it) / 1000L).coerceAtLeast(0L).toInt() }
        batch.update(
            events.document(event.id),
            mapOf(
                UsageEventFields.ENDED_AT to FieldValue.serverTimestamp(),
                UsageEventFields.DURATION_SECONDS to duration,
            ),
        )
    }

    /**
     * Every period for a device that is still open, at most one per channel.
     *
     * Equality-only on purpose. Three equality filters are served by single-field indexes
     * via a merge join, so this needs no composite index -- one less thing to forget
     * before the demo (section 12). Filtering by channel happens in memory afterwards,
     * over a result set bounded by the number of channels on the plate.
     *
     * `owner_uid` is required for the same reason as in [observeForDevice]: without it the
     * rules cannot admit the query, and switching a device off would fail at the point of
     * closing its usage period.
     */
    suspend fun findOpenEvents(ownerUid: String, deviceId: String): List<UsageEvent> =
        events
            .whereEqualTo(UsageEventFields.OWNER_UID, ownerUid)
            .whereEqualTo(UsageEventFields.DEVICE_ID, deviceId)
            .whereEqualTo(UsageEventFields.ENDED_AT, null)
            .get()
            .await()
            .mapDocuments(UsageEvent::fromSnapshot)

    /** The open period for one channel, or for the device itself when [channelId] is null. */
    suspend fun findOpenEvent(
        ownerUid: String,
        deviceId: String,
        channelId: String? = null,
    ): UsageEvent? = findOpenEvents(ownerUid, deviceId).firstOrNull { it.channelId == channelId }

    /**
     * Recent history for one device, newest first.
     *
     * The `owner_uid` filter is not redundant with the security rules -- it is what makes
     * the query legal at all. Firestore rules are not filters: a query it cannot prove will
     * only return documents the caller owns is rejected outright, so filtering by
     * `device_id` alone fails with PERMISSION_DENIED even when every matching document does
     * belong to the caller.
     *
     * Needs the `owner_uid ASC, device_id ASC, started_at DESC` composite index.
     */
    fun observeForDevice(
        ownerUid: String,
        deviceId: String,
        limit: Long = 50,
    ): Flow<List<UsageEvent>> =
        events
            .whereEqualTo(UsageEventFields.OWNER_UID, ownerUid)
            .whereEqualTo(UsageEventFields.DEVICE_ID, deviceId)
            .orderBy(UsageEventFields.STARTED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { it.mapDocuments(UsageEvent::fromSnapshot) }

    /**
     * Everything the user has recorded since [since], newest first -- the query Reports
     * is built on.
     *
     * Needs the `owner_uid ASC, started_at DESC` composite index.
     *
     * A period that began before [since] and is still running is excluded by this filter,
     * because the range applies to `started_at`. Reports that need "on during this window"
     * rather than "started during this window" should widen [since] and clip client-side.
     */
    /**
     * The same window as [observeSince], read once.
     *
     * Reports is the one screen in the app that deliberately holds no listener: it is a
     * retrospective view, and re-aggregating a month of events every time somebody toggles
     * a lamp would burn reads to redraw a chart nobody is watching change. A one-shot read
     * plus a refresh button is the honest shape for that.
     */
    suspend fun getSince(ownerUid: String, since: Timestamp): List<UsageEvent> =
        events
            .whereEqualTo(UsageEventFields.OWNER_UID, ownerUid)
            .whereGreaterThanOrEqualTo(UsageEventFields.STARTED_AT, since)
            .orderBy(UsageEventFields.STARTED_AT, Query.Direction.DESCENDING)
            .get()
            .await()
            .mapDocuments(UsageEvent::fromSnapshot)

    fun observeSince(ownerUid: String, since: Timestamp): Flow<List<UsageEvent>> =
        events
            .whereEqualTo(UsageEventFields.OWNER_UID, ownerUid)
            .whereGreaterThanOrEqualTo(UsageEventFields.STARTED_AT, since)
            .orderBy(UsageEventFields.STARTED_AT, Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { it.mapDocuments(UsageEvent::fromSnapshot) }
}
