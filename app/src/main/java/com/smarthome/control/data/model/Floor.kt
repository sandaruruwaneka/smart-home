package com.smarthome.control.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.smarthome.control.data.FloorFields
import com.smarthome.control.data.asIntOrNull

/**
 * A floor of the home and the grid its devices are placed on -- SCHEMA.md section 3.
 *
 * The grid is the coordinate system for every device on the floor, which is why
 * [gridRows] and [gridCols] live here rather than being inferred from the plan image.
 * Only the app ever writes this document; the worker and the simulator never do.
 */
data class Floor(
    val id: String,
    val ownerUid: String,
    val name: String,
    /**
     * Firebase Storage download URL for the floor plan image.
     *
     * Nullable by contract, and not as an edge case: a floor is created before any plan
     * is uploaded, and the brief allows a home to have none at all. Every screen that
     * renders a floor must render a working grid over a blank field when this is null.
     */
    val planImageUrl: String?,
    val gridRows: Int,
    val gridCols: Int,
    /**
     * Null only in the instant between a local create and the server materialising
     * `serverTimestamp()`. Sort keys must tolerate it rather than assume it is set.
     */
    val createdAt: Timestamp?,
) {
    /** Whether a grid coordinate lands on this floor. Both axes are 0-based. */
    fun contains(gridX: Int, gridY: Int): Boolean =
        gridX in 0 until gridCols && gridY in 0 until gridRows

    companion object {
        /** Both axes are constrained to 4..20 by the contract. */
        val GRID_RANGE = 4..20

        fun fromSnapshot(snapshot: DocumentSnapshot): Floor? {
            val ownerUid = snapshot.getString(FloorFields.OWNER_UID) ?: return null
            // A floor without geometry cannot place anything, so it is unreadable rather
            // than defaulted -- a guessed grid size would silently move every device.
            val rows = snapshot.get(FloorFields.GRID_ROWS).asIntOrNull() ?: return null
            val cols = snapshot.get(FloorFields.GRID_COLS).asIntOrNull() ?: return null
            return Floor(
                id = snapshot.id,
                ownerUid = ownerUid,
                name = snapshot.getString(FloorFields.NAME).orEmpty(),
                planImageUrl = snapshot.getString(FloorFields.PLAN_IMAGE_URL),
                gridRows = rows.coerceIn(GRID_RANGE),
                gridCols = cols.coerceIn(GRID_RANGE),
                createdAt = snapshot.getTimestamp(FloorFields.CREATED_AT),
            )
        }
    }
}
