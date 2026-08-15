package com.smarthome.control.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.smarthome.control.data.Collections
import com.smarthome.control.data.UserFields
import com.smarthome.control.data.model.UserProfile
import com.smarthome.control.data.snapshotFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.ZoneId

/**
 * Authentication and the `users/{uid}` profile document -- SCHEMA.md section 9.
 *
 * These two are one repository rather than two because they are never independently
 * useful: an account with no profile document has no timezone, and without a timezone the
 * scheduler cannot interpret a single light schedule. Registration therefore creates both
 * or neither.
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val users = firestore.collection(Collections.USERS)

    /** The signed-in uid, or null when signed out. Every query below is scoped by it. */
    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Emits the current uid, and again on every sign-in and sign-out.
     *
     * Screens observe this rather than reading [currentUid] once, so that a sign-out tears
     * down the listeners of the previous user instead of leaving them running against
     * documents the rules now reject.
     */
    fun observeAuthState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /**
     * Creates the account and its profile document.
     *
     * [timezone] defaults to the device's own zone, which is the right guess for someone
     * setting up a home they are standing in, and is editable from Settings afterwards.
     * It is stored as an IANA name because the scheduler needs a zone that survives
     * daylight saving, not a fixed offset.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        timezone: String = ZoneId.systemDefault().id,
    ) {
        val trimmedEmail = email.trim()
        val credential = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
        val uid = credential.user?.uid ?: error("account created without a uid")
        users.document(uid).set(
            mapOf(
                UserFields.DISPLAY_NAME to displayName.trim(),
                UserFields.EMAIL to trimmedEmail,
                UserFields.TIMEZONE to timezone,
                UserFields.CREATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    fun signOut() = auth.signOut()

    /**
     * The signed-in user's profile, re-subscribing when the account changes and emitting
     * null while signed out.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeCurrentProfile(): Flow<UserProfile?> = observeAuthState().flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else users.document(uid).snapshotFlow().map(UserProfile::fromSnapshot)
    }

    suspend fun updateDisplayName(uid: String, displayName: String) {
        users.document(uid).update(UserFields.DISPLAY_NAME, displayName.trim()).await()
    }

    /**
     * Changes the zone the scheduler interprets every light window in.
     *
     * Rejected rather than stored blindly when unparseable: an invalid zone here does not
     * fail visibly, it just makes every schedule fire at the wrong time.
     */
    suspend fun updateTimezone(uid: String, timezone: String) {
        require(runCatching { ZoneId.of(timezone) }.isSuccess) { "not an IANA zone: $timezone" }
        users.document(uid).update(UserFields.TIMEZONE, timezone).await()
    }
}
