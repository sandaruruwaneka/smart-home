package com.smarthome.control.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smarthome.control.data.AlertFields
import com.smarthome.control.data.Collections
import com.smarthome.control.data.mapDocuments
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Safety alerts -- `alerts`, SCHEMA.md section 8.
 *
 * Read and acknowledge only. There is no `create` here and there never should be: the
 * safety worker owns raising alerts, and once it is live the security rules deny client
 * creates outright. An alert the app invented would be one no worker could ever clear.
 */
class AlertRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val alerts = firestore.collection(Collections.ALERTS)

    /**
     * Every alert for the user, newest first.
     *
     * Needs the `owner_uid ASC, created_at DESC` composite index.
     *
     * Bounded, and deliberately so. At this project's scale a second page will never be
     * reached, but an unbounded collection listener is the most common way to run up a
     * Firestore bill: it re-reads every document in the collection on every reconnect, and
     * alerts are the one collection here that grows forever with no retention policy.
     */
    fun observeAlerts(ownerUid: String, limit: Long = DEFAULT_PAGE): Flow<List<Alert>> =
        alerts
            .whereEqualTo(AlertFields.OWNER_UID, ownerUid)
            .orderBy(AlertFields.CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { it.mapDocuments(Alert::fromSnapshot) }

    /**
     * The alerts still awaiting acknowledgement.
     *
     * A `Critical`-tier banner shows for as long as this is non-empty, so it is filtered
     * from the same listener as [observeAlerts] rather than run as a second query -- the
     * banner and the list must never disagree about whether anything is outstanding.
     */
    fun observeUnacknowledged(ownerUid: String): Flow<List<Alert>> =
        observeAlerts(ownerUid).map { list -> list.filter { !it.acknowledged } }

    /**
     * Alerts raised since [since], read once — the safety half of Reports.
     *
     * Uses the same `owner_uid ASC, created_at DESC` index the listener does, so the range
     * filter costs no extra index.
     */
    suspend fun getSince(ownerUid: String, since: Timestamp): List<Alert> =
        alerts
            .whereEqualTo(AlertFields.OWNER_UID, ownerUid)
            .whereGreaterThanOrEqualTo(AlertFields.CREATED_AT, since)
            .orderBy(AlertFields.CREATED_AT, Query.Direction.DESCENDING)
            .get()
            .await()
            .mapDocuments(Alert::fromSnapshot)

    suspend fun acknowledge(alertId: String) {
        alerts.document(alertId).update(AlertFields.ACKNOWLEDGED, true).await()
    }

    /**
     * Acknowledges several alerts at once, for the "dismiss all" action.
     *
     * Chunked to Firestore's 500-write batch limit. Each chunk is atomic; a partial
     * failure leaves the remainder outstanding, which keeps the banner up -- the right
     * direction for a hazard notice.
     */
    suspend fun acknowledgeAll(alertIds: List<String>) {
        alertIds.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.update(alerts.document(it), AlertFields.ACKNOWLEDGED, true) }
            batch.commit().await()
        }
    }

    private companion object {
        const val MAX_BATCH_WRITES = 500

        /** Section 9 of screen prompt 09 fixes the page at 50. */
        const val DEFAULT_PAGE = 50L
    }
}
