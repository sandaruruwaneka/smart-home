package com.smarthome.control.data.repository

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.smarthome.control.data.Collections
import com.smarthome.control.data.DeviceFields
import com.smarthome.control.data.FloorFields
import com.smarthome.control.data.mapDocuments
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Floors and their plan images -- SCHEMA.md section 3.
 *
 * The only collection written exclusively by this app. Neither the worker nor the
 * simulator touches floor geometry, which is why grid changes need no coordination with
 * the other two codebases -- only with the devices already placed on the grid.
 */
class FloorRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val deviceRepository: DeviceRepository = DeviceRepository(firestore),
) {
    private val floors = firestore.collection(Collections.FLOORS)

    fun observeFloors(ownerUid: String): Flow<List<Floor>> =
        floors
            .whereEqualTo(FloorFields.OWNER_UID, ownerUid)
            .orderBy(FloorFields.CREATED_AT, Query.Direction.ASCENDING)
            .snapshotFlow()
            .map { it.mapDocuments(Floor::fromSnapshot) }

    fun observeFloor(floorId: String): Flow<Floor?> =
        floors.document(floorId).snapshotFlow().map(Floor::fromSnapshot)

    suspend fun getFloor(floorId: String): Floor? =
        Floor.fromSnapshot(floors.document(floorId).get().await())

    /**
     * Creates a floor. The plan image is uploaded separately, because a floor is usable
     * with a blank grid and forcing an upload first would block setup on a file the user
     * may not have yet.
     */
    suspend fun createFloor(
        ownerUid: String,
        name: String,
        gridRows: Int,
        gridCols: Int,
    ): String {
        requireGrid(gridRows, gridCols)
        val reference = floors.document()
        reference.set(
            mapOf(
                FloorFields.OWNER_UID to ownerUid,
                FloorFields.NAME to name.trim(),
                FloorFields.PLAN_IMAGE_URL to null,
                FloorFields.GRID_ROWS to gridRows,
                FloorFields.GRID_COLS to gridCols,
                FloorFields.CREATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        return reference.id
    }

    suspend fun rename(floorId: String, name: String) {
        floors.document(floorId).update(FloorFields.NAME, name.trim()).await()
    }

    /**
     * Resizes the grid.
     *
     * Shrinking can orphan devices that sit outside the new bounds, so this refuses to
     * write until they have been moved. The caller finds them with [devicesOutsideGrid]
     * and walks the user through relocating them -- silently clamping the coordinates
     * would move somebody's devices without telling them, and silently allowing them
     * would leave devices at coordinates the floor no longer has.
     */
    suspend fun resizeGrid(floorId: String, gridRows: Int, gridCols: Int, devicesOnFloor: List<Device>) {
        requireGrid(gridRows, gridCols)
        val orphaned = devicesOnFloor.filter { it.gridX >= gridCols || it.gridY >= gridRows }
        require(orphaned.isEmpty()) {
            "${orphaned.size} device(s) fall outside a ${gridCols}x$gridRows grid; move them first"
        }
        floors.document(floorId).update(
            mapOf(
                FloorFields.GRID_ROWS to gridRows,
                FloorFields.GRID_COLS to gridCols,
            ),
        ).await()
    }

    /** The devices a resize to [gridRows] x [gridCols] would strand, for the warning copy. */
    fun devicesOutsideGrid(devicesOnFloor: List<Device>, gridRows: Int, gridCols: Int): List<Device> =
        devicesOnFloor.filter { it.gridX >= gridCols || it.gridY >= gridRows }

    /**
     * Uploads a floor plan image and stores its download URL on the floor.
     *
     * Stored under the owner's uid so that Storage rules can be written the same way as
     * Firestore's -- the path itself carries the ownership the rules check.
     */
    suspend fun uploadPlanImage(ownerUid: String, floorId: String, image: Uri): String {
        val reference = storage.reference.child("floor_plans/$ownerUid/$floorId.jpg")
        reference.putFile(image).await()
        val downloadUrl = reference.downloadUrl.await().toString()
        floors.document(floorId).update(FloorFields.PLAN_IMAGE_URL, downloadUrl).await()
        return downloadUrl
    }

    /**
     * Removes the plan image, returning the floor to a blank grid.
     *
     * The Storage object is deleted first and its failure ignored: if the file is already
     * gone, the field still needs clearing, and a floor pointing at a URL that 404s is
     * worse than an orphaned file in a demo bucket.
     */
    suspend fun removePlanImage(ownerUid: String, floorId: String) {
        runCatching {
            storage.reference.child("floor_plans/$ownerUid/$floorId.jpg").delete().await()
        }
        floors.document(floorId).update(FloorFields.PLAN_IMAGE_URL, null).await()
    }

    /**
     * Deletes a floor and everything placed on it.
     *
     * The contract spells out the device-level cascade but is silent on floors. Cascading
     * is the only coherent reading: a device's `floor_id` would otherwise point at nothing,
     * and it would keep appearing in the account-wide device listener that the floor list's
     * summary counts are built from, attributed to a floor that no longer exists.
     *
     * Devices go first so that an interrupted delete leaves a floor with fewer devices --
     * recoverable and visible -- rather than devices with no floor.
     */
    suspend fun deleteFloor(ownerUid: String, floorId: String) {
        firestore.collection(Collections.DEVICES)
            .whereEqualTo(DeviceFields.OWNER_UID, ownerUid)
            .whereEqualTo(DeviceFields.FLOOR_ID, floorId)
            .get()
            .await()
            .documents
            .forEach { deviceRepository.deleteDevice(it.id) }

        // The document is about to go, so only the Storage object needs cleaning up.
        runCatching {
            storage.reference.child("floor_plans/$ownerUid/$floorId.jpg").delete().await()
        }
        floors.document(floorId).delete().await()
    }

    private fun requireGrid(gridRows: Int, gridCols: Int) {
        require(gridRows in Floor.GRID_RANGE && gridCols in Floor.GRID_RANGE) {
            "grid must be ${Floor.GRID_RANGE.first}..${Floor.GRID_RANGE.last} on both axes"
        }
    }
}
