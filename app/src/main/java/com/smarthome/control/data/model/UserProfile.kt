package com.smarthome.control.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.smarthome.control.data.UserFields
import java.time.ZoneId

/**
 * The signed-in user's profile -- `users/{uid}`, SCHEMA.md section 9.
 *
 * The document id is the Firebase Auth uid, so this is the one collection whose ids are
 * not Firestore auto-ids.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    /**
     * IANA timezone name, e.g. `Asia/Colombo`.
     *
     * Not cosmetic. The scheduler job interprets every light's `schedule_on` and
     * `schedule_off` in this zone, and the app's "time on today" has to find the same
     * midnight the user means. Stored as the IANA name rather than a UTC offset because
     * an offset is wrong for half the year anywhere with daylight saving.
     */
    val timezone: String,
    val createdAt: Timestamp?,
) {
    /**
     * [timezone] as a [ZoneId], falling back to the device's own zone when the stored
     * name is empty or unrecognised.
     *
     * The fallback keeps Reports rendering rather than throwing, and for a user sitting
     * in their own home it is very likely the same zone anyway.
     */
    val zoneId: ZoneId
        get() = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }

    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): UserProfile? {
            if (!snapshot.exists()) return null
            return UserProfile(
                uid = snapshot.id,
                displayName = snapshot.getString(UserFields.DISPLAY_NAME).orEmpty(),
                email = snapshot.getString(UserFields.EMAIL).orEmpty(),
                timezone = snapshot.getString(UserFields.TIMEZONE).orEmpty(),
                createdAt = snapshot.getTimestamp(UserFields.CREATED_AT),
            )
        }
    }
}
