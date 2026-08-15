package com.smarthome.control.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A document together with the one piece of listener metadata the UI needs.
 *
 * SCHEMA.md section 4 is explicit that the "someone else changed this" flash must not be
 * driven by comparing `last_changed_by`, because that races: the app writes the field and
 * the field comes back, and there is no reliable way to tell a reflection of your own
 * write from a genuine remote one by looking at its contents.
 *
 * Firestore already answers the question directly. [isFromServer] is
 * `!snapshot.metadata.hasPendingWrites` -- false while a local write is still in flight,
 * true for anything that actually arrived from the server. The screen flashes when this
 * is true *and* the value differs from what it last rendered.
 *
 * `last_changed_by` is still parsed onto the model, but only for alert copy and demo
 * debugging, never for UI logic.
 */
data class Live<out T>(
    val value: T,
    val isFromServer: Boolean,
)

/**
 * Listens to a single document for as long as the collecting coroutine is alive.
 *
 * Metadata-only changes are deliberately *not* requested. When the app writes, Firestore
 * fires immediately from cache with `hasPendingWrites = true`; when the server
 * acknowledges, the only thing that changed is metadata. Including those events would
 * deliver a second copy of the app's own write with `isFromServer = true`, which is
 * exactly the false flash this design is trying to avoid.
 *
 * The flow fails with the Firestore exception rather than silently going quiet, so a
 * missing composite index (section 12) or a rules rejection is visible instead of looking
 * like an empty collection.
 */
internal fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/** The query equivalent of [snapshotFlow], with the same error and metadata behaviour. */
internal fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/**
 * Maps every document in a query result, dropping any that fail to parse.
 *
 * Dropping rather than throwing is deliberate. The simulator and the worker write to the
 * same collections, and a document written by a newer version of either -- an enum value
 * this build has never heard of, say -- must not take down a whole screen. One unreadable
 * row disappears; the rest of the floor still renders.
 */
internal fun <T> QuerySnapshot.mapDocuments(transform: (DocumentSnapshot) -> T?): List<T> =
    documents.mapNotNull { runCatching { transform(it) }.getOrNull() }

/** As [mapDocuments], but tagging each row with its own pending-write state. */
internal fun <T> QuerySnapshot.mapDocumentsLive(
    transform: (DocumentSnapshot) -> T?,
): List<Live<T>> = documents.mapNotNull { document ->
    val value = runCatching { transform(document) }.getOrNull() ?: return@mapNotNull null
    Live(value, isFromServer = !document.metadata.hasPendingWrites())
}

/**
 * Parses an UPPERCASE enum string from Firestore, returning null for anything unknown.
 *
 * Section 1 fixes the enum values as UPPERCASE strings matching the Kotlin constants, so
 * [Enum.name] round-trips and no mapping table is needed.
 */
internal inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? {
    if (this == null) return null
    return enumValues<T>().firstOrNull { it.name == this }
}

/** Firestore hands back every number as [Long]; grid coordinates and counts are [Int]. */
internal fun Any?.asIntOrNull(): Int? = (this as? Number)?.toInt()
