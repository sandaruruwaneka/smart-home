package com.smarthome.control.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.smarthome.control.data.ChannelFields
import com.smarthome.control.data.Collections
import com.smarthome.control.data.ConfigFields
import com.smarthome.control.data.DeviceFields
import com.smarthome.control.data.Live
import com.smarthome.control.data.UsageEventFields
import com.smarthome.control.data.mapDocuments
import com.smarthome.control.data.mapDocumentsLive
import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.ChangeSource
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.Floor
import com.smarthome.control.data.model.deriveMultiSwitchStatus
import com.smarthome.control.data.snapshotFlow
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Devices and their channels -- SCHEMA.md sections 4 and 6.
 *
 * Every method that changes a device's power state also writes the matching usage event
 * in the same [WriteBatch]. That coupling is the whole point of this class: a status
 * change and its usage row are one fact, and Firestore batches are the only way to make
 * them land together or not at all.
 */
class DeviceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val usageEvents: UsageEventRepository = UsageEventRepository(firestore),
) {
    private val devices = firestore.collection(Collections.DEVICES)

    private fun channelsOf(deviceId: String) =
        devices.document(deviceId).collection(Collections.CHANNELS)

    // ---------------------------------------------------------------- reads

    /**
     * Every device the user owns, from a single listener.
     *
     * The floor list's summary counts are grouped out of this one stream rather than
     * queried per floor (section 14). Denormalised per-floor counters were rejected
     * because keeping them accurate needs a Cloud Function trigger, and one collection
     * listener is both simpler and correct at this scale.
     *
     * Needs the `owner_uid ASC, floor_id ASC` composite index.
     */
    fun observeDevices(ownerUid: String): Flow<List<Device>> =
        devices
            .whereEqualTo(DeviceFields.OWNER_UID, ownerUid)
            .orderBy(DeviceFields.FLOOR_ID)
            .snapshotFlow()
            .map { it.mapDocuments(Device::fromSnapshot) }

    /**
     * The devices on one floor, each tagged with whether it arrived from the server.
     *
     * The floor dashboard uses [Live.isFromServer] to decide whether a status change was
     * somebody else's doing and deserves the external-change flash.
     */
    fun observeDevicesOnFloor(ownerUid: String, floorId: String): Flow<List<Live<Device>>> =
        devices
            .whereEqualTo(DeviceFields.OWNER_UID, ownerUid)
            .whereEqualTo(DeviceFields.FLOOR_ID, floorId)
            .snapshotFlow()
            .map { it.mapDocumentsLive(Device::fromSnapshot) }

    /** One device. The control sheets hold this listener open while they are shown. */
    fun observeDevice(deviceId: String): Flow<Live<Device>?> =
        devices.document(deviceId).snapshotFlow().map { snapshot ->
            Device.fromSnapshot(snapshot)?.let {
                Live(it, isFromServer = !snapshot.metadata.hasPendingWrites())
            }
        }

    /**
     * A switch bank's channels, ordered by plate position.
     *
     * Ordered by `index` and never by name: the list has to match the order of the
     * switches on the wall. This is the second of the two listeners the multi-switch sheet
     * holds -- one on the device document, one here.
     */
    fun observeChannels(deviceId: String): Flow<List<Live<Channel>>> =
        channelsOf(deviceId)
            .orderBy(ChannelFields.INDEX)
            .snapshotFlow()
            .map { it.mapDocumentsLive(Channel::fromSnapshot) }

    suspend fun getDevice(deviceId: String): Device? =
        Device.fromSnapshot(devices.document(deviceId).get().await())

    suspend fun getChannels(deviceId: String): List<Channel> =
        channelsOf(deviceId).orderBy(ChannelFields.INDEX).get().await()
            .mapDocuments(Channel::fromSnapshot)

    // --------------------------------------------------------------- writes

    /**
     * Places a new device, creating its channels in the same batch when it is a switch
     * bank.
     *
     * One batch, so a gang unit is never briefly visible with no channels -- a state in
     * which its derived status is undefined and its sheet has nothing to show.
     *
     * Devices are created `OFF` with no `turned_on_at`. Placing a device does not open a
     * usage period, because nothing has been switched on.
     */
    suspend fun placeDevice(
        ownerUid: String,
        floor: Floor,
        type: DeviceType,
        name: String,
        gridX: Int,
        gridY: Int,
        config: DeviceConfig,
        channelNames: List<String> = emptyList(),
    ): String {
        require(config.deviceType == type) {
            "config is for ${config.deviceType}, device is $type"
        }
        require(floor.contains(gridX, gridY)) {
            "($gridX, $gridY) is outside this floor's ${floor.gridCols}x${floor.gridRows} grid"
        }

        val batch = firestore.batch()
        val deviceRef = devices.document()
        batch.set(
            deviceRef,
            mapOf(
                DeviceFields.OWNER_UID to ownerUid,
                DeviceFields.FLOOR_ID to floor.id,
                DeviceFields.TYPE to type.name,
                DeviceFields.NAME to name.trim(),
                DeviceFields.GRID_X to gridX,
                DeviceFields.GRID_Y to gridY,
                DeviceFields.STATUS to DeviceState.OFF.name,
                DeviceFields.TURNED_ON_AT to null,
                DeviceFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name,
                DeviceFields.CONFIG to config.toMap(),
            ),
        )

        (config as? DeviceConfig.MultiSwitch)?.let { multiSwitch ->
            repeat(multiSwitch.channelCount) { index ->
                batch.set(
                    channelsOf(deviceRef.id).document(),
                    mapOf(
                        ChannelFields.INDEX to index,
                        ChannelFields.NAME to channelNames.getOrNull(index).orEmpty().trim(),
                        ChannelFields.STATUS to DeviceState.OFF.name,
                        ChannelFields.TURNED_ON_AT to null,
                        ChannelFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }

        batch.commit().await()
        return deviceRef.id
    }

    suspend fun rename(deviceId: String, name: String) {
        devices.document(deviceId).update(
            mapOf(
                DeviceFields.NAME to name.trim(),
                DeviceFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name,
            ),
        ).await()
    }

    /** Moves a device on its floor's grid, refusing coordinates the grid does not contain. */
    suspend fun move(deviceId: String, floor: Floor, gridX: Int, gridY: Int) {
        require(floor.contains(gridX, gridY)) {
            "($gridX, $gridY) is outside this floor's ${floor.gridCols}x${floor.gridRows} grid"
        }
        devices.document(deviceId).update(
            mapOf(
                DeviceFields.GRID_X to gridX,
                DeviceFields.GRID_Y to gridY,
                DeviceFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name,
            ),
        ).await()
    }

    suspend fun renameChannel(deviceId: String, channelId: String, name: String) {
        channelsOf(deviceId).document(channelId).update(
            mapOf(
                ChannelFields.NAME to name.trim(),
                ChannelFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /**
     * Changes an appliance's cutoff limit.
     *
     * Written as a nested field path rather than replacing `config` wholesale, so a
     * concurrent write to another key in the map is not clobbered.
     *
     * There is deliberately no equivalent for `channel_count`: it is immutable, and
     * changing it would orphan the usage history of any channel that disappeared.
     */
    suspend fun updateApplianceLimit(deviceId: String, maxOnDurationSeconds: Int) {
        require(maxOnDurationSeconds > 0) { "a maximum on-duration must be positive" }
        devices.document(deviceId).update(
            mapOf(
                "${DeviceFields.CONFIG}.${ConfigFields.MAX_ON_DURATION}" to maxOnDurationSeconds,
                DeviceFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name,
            ),
        ).await()
    }

    /**
     * Replaces a light's schedule.
     *
     * All three schedule keys are written together because they are one setting: enabling
     * a window while its edges are still the previous values would have the worker act on
     * a schedule the user never chose.
     */
    suspend fun updateLightSchedule(deviceId: String, schedule: DeviceConfig.Light) {
        devices.document(deviceId).update(
            mapOf(
                DeviceFields.CONFIG to schedule.toMap(),
                DeviceFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name,
            ),
        ).await()
    }

    /**
     * Replaces a camera's URIs.
     *
     * Both are written together for the same reason a light's schedule keys are: a stream
     * URI paired with the previous snapshot URI is a camera pointing at two different
     * places, which is not a state anybody asked for.
     */
    suspend fun updateCameraUris(deviceId: String, config: DeviceConfig.Camera) {
        devices.document(deviceId).update(
            mapOf(
                DeviceFields.CONFIG to config.toMap(),
                DeviceFields.LAST_CHANGED_AT to FieldValue.serverTimestamp(),
                DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name,
            ),
        ).await()
    }

    // ------------------------------------------------------- power switching

    /**
     * Switches a single-channel device on or off, opening or closing its usage period in
     * the same batch.
     *
     * Rejects switch banks, whose status is derived from their channels -- use
     * [setChannelStatus]. Rejects cameras, whose `status` means stream reachability
     * rather than power, so there is nothing for the app to switch.
     *
     * `turned_on_at` is set on the way on and cleared on the way off, in the same write as
     * the status. The safety worker reads only that field to decide cutoffs, so leaving it
     * set on a device that is now off would eventually produce a cutoff and an alert for a
     * device that had not been running at all.
     */
    suspend fun setDeviceStatus(device: Device, turnOn: Boolean) {
        require(device.type != DeviceType.MULTI_SWITCH) {
            "a switch bank's status is derived from its channels; use setChannelStatus"
        }
        require(device.type != DeviceType.CAMERA) {
            "a camera's status is stream reachability, not power"
        }

        val batch = firestore.batch()
        stageStatusChange(
            batch = batch,
            reference = devices.document(device.id),
            fields = DeviceStatusFields,
            turnOn = turnOn,
            extra = mapOf(DeviceFields.LAST_CHANGED_BY to ChangeSource.APP.name),
        )

        if (turnOn) {
            usageEvents.openInBatch(batch, device.ownerUid, device.id)
        } else {
            usageEvents.findOpenEvent(device.id)?.let { usageEvents.closeInBatch(batch, it) }
        }

        batch.commit().await()
    }

    /**
     * Switches one channel of a gang plate, and updates the parent's derived status in the
     * same batch.
     *
     * The contract requires the parent-plus-channel write to be one batch, for both the
     * app and the simulator. Deriving the parent client-side instead was rejected: the
     * floor list's summary counts and the plan marker would then need every gang unit's
     * channel subcollection loaded on every floor, which a screen that must render in
     * under two seconds cannot afford.
     *
     * [currentChannels] is the list the sheet is already listening to, passed in rather
     * than re-read so that switching a channel costs no extra reads. It must be the full
     * set for the device -- deriving a parent status from a partial list would switch the
     * unit off on screen while another channel is still live.
     */
    suspend fun setChannelStatus(
        device: Device,
        currentChannels: List<Channel>,
        channelId: String,
        turnOn: Boolean,
    ) {
        require(device.type == DeviceType.MULTI_SWITCH) { "${device.type} has no channels" }
        require(currentChannels.any { it.id == channelId }) {
            "channel $channelId is not one of this device's channels"
        }

        val target = if (turnOn) DeviceState.ON else DeviceState.OFF
        val projected = currentChannels.map {
            if (it.id == channelId) it.copy(status = target) else it
        }
        // Null means the channel list was empty, which cannot happen after the require
        // above; falling back to the device's current status keeps the write harmless
        // rather than guessing OFF.
        val derived = deriveMultiSwitchStatus(projected) ?: device.status

        val batch = firestore.batch()
        stageStatusChange(
            batch = batch,
            reference = channelsOf(device.id).document(channelId),
            fields = ChannelStatusFields,
            turnOn = turnOn,
        )

        batch.update(
            devices.document(device.id),
            buildMap<String, Any?> {
                put(DeviceFields.STATUS, derived.name)
                put(DeviceFields.LAST_CHANGED_AT, FieldValue.serverTimestamp())
                put(DeviceFields.LAST_CHANGED_BY, ChangeSource.APP.name)
                // Only touched when the unit crosses the on/off line. Rewriting it while
                // the unit stays on would restart the clock every time any channel is
                // switched, and for a unit that is no longer on it must be cleared or the
                // worker will keep measuring against a stale start.
                when {
                    derived == DeviceState.ON && device.status != DeviceState.ON ->
                        put(DeviceFields.TURNED_ON_AT, FieldValue.serverTimestamp())
                    derived != DeviceState.ON ->
                        put(DeviceFields.TURNED_ON_AT, null)
                }
            },
        )

        if (turnOn) {
            usageEvents.openInBatch(batch, device.ownerUid, device.id, channelId)
        } else {
            usageEvents.findOpenEvent(device.id, channelId)
                ?.let { usageEvents.closeInBatch(batch, it) }
        }

        batch.commit().await()
    }

    /**
     * Deletes a device along with its channels and its usage history.
     *
     * Soft-delete is not used, and the cascade is part of the contract: an orphaned
     * channel is unreachable, and an orphaned usage event would keep contributing to
     * reports for a device the user believes is gone.
     *
     * Split across batches because a device with a long history can exceed the 500-write
     * batch limit. Each chunk is atomic; the sequence as a whole is not, so an interrupted
     * delete leaves some history behind. That is the safe direction -- the device document
     * goes last, so the app never shows a device whose channels have already gone.
     */
    suspend fun deleteDevice(deviceId: String) {
        val channelRefs = channelsOf(deviceId).get().await().documents.map { it.reference }
        val usageRefs = firestore.collection(Collections.USAGE_EVENTS)
            .whereEqualTo(UsageEventFields.DEVICE_ID, deviceId)
            .get()
            .await()
            .documents
            .map { it.reference }

        (channelRefs + usageRefs).chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach(batch::delete)
            batch.commit().await()
        }

        devices.document(deviceId).delete().await()
    }

    /**
     * The status/`turned_on_at`/`last_changed_at` triple, which devices and channels write
     * identically under different field names.
     */
    private fun stageStatusChange(
        batch: WriteBatch,
        reference: DocumentReference,
        fields: StatusFieldNames,
        turnOn: Boolean,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        batch.update(
            reference,
            mapOf<String, Any?>(
                fields.status to (if (turnOn) DeviceState.ON else DeviceState.OFF).name,
                fields.turnedOnAt to if (turnOn) FieldValue.serverTimestamp() else null,
                fields.lastChangedAt to FieldValue.serverTimestamp(),
            ) + extra,
        )
    }

    private interface StatusFieldNames {
        val status: String
        val turnedOnAt: String
        val lastChangedAt: String
    }

    private object DeviceStatusFields : StatusFieldNames {
        override val status = DeviceFields.STATUS
        override val turnedOnAt = DeviceFields.TURNED_ON_AT
        override val lastChangedAt = DeviceFields.LAST_CHANGED_AT
    }

    private object ChannelStatusFields : StatusFieldNames {
        override val status = ChannelFields.STATUS
        override val turnedOnAt = ChannelFields.TURNED_ON_AT
        override val lastChangedAt = ChannelFields.LAST_CHANGED_AT
    }

    private companion object {
        /** Firestore's hard limit on writes in a single batch. */
        const val MAX_BATCH_WRITES = 500
    }
}
