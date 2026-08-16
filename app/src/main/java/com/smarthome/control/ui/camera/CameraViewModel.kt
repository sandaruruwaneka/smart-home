package com.smarthome.control.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UserRepository
import com.smarthome.control.ui.model.DeviceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the camera view and the camera wall.
 *
 * Cameras are the one device type with nothing to switch, so this class is unusually thin:
 * three listeners, a clock for the snapshot's age, and the two writes the overflow offers.
 * The interesting behaviour all lives in the player, which the screen owns because a
 * decoder is not state a ViewModel can meaningfully hold.
 *
 * The account-wide device listener is filtered to cameras here rather than queried
 * separately: the app already holds that stream for the floor list's summary counts, and a
 * second query for "every camera" would pay twice for the same documents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(
    private val deviceId: String?,
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)

    val state: StateFlow<CameraUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CameraUiState(),
        )

    /** Every camera in the home, grouped by floor — the wall. */
    val wall: StateFlow<List<CameraWallSection>> = attempts
        .flatMapLatest { subscribeWall() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private fun subscribe(): Flow<CameraUiState> {
        val users = users
        val devices = devices
        val floors = floors
        val deviceId = deviceId

        if (users == null || devices == null || floors == null) {
            return flowOf(CameraUiState(isLoading = false, loadError = FirebaseMissing))
        }
        if (deviceId == null) return flowOf(CameraUiState(isLoading = false))

        return users.observeAuthState().distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) return@flatMapLatest flowOf(CameraUiState(isLoading = false))

            combine(
                devices.observeDevice(deviceId),
                floors.observeFloors(uid),
                devices.observeDevices(uid).map { list -> list.filter { it.type == DeviceType.CAMERA } },
                clock(),
                actionError,
            ) { device, floorList, cameras, nowMillis, error ->
                buildCameraState(
                    device = device,
                    floors = floorList,
                    cameras = cameras,
                    nowMillis = nowMillis,
                ).copy(actionError = error)
            }
        }.catch { failure -> emit(CameraUiState(isLoading = false, loadError = failure.userMessage())) }
    }

    private fun subscribeWall(): Flow<List<CameraWallSection>> {
        val users = users ?: return flowOf(emptyList())
        val devices = devices ?: return flowOf(emptyList())
        val floors = floors ?: return flowOf(emptyList())

        return users.observeAuthState().distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) return@flatMapLatest flowOf(emptyList())
            combine(
                devices.observeDevices(uid).map { list -> list.filter { it.type == DeviceType.CAMERA } },
                floors.observeFloors(uid),
            ) { cameras, floorList -> buildCameraWall(cameras, floorList) }
        }.catch { emit(emptyList()) }
    }

    /**
     * A one-second tick, which is faster than anywhere else in the app.
     *
     * `Updated 8 seconds ago` is the one caption in the project that counts in seconds, and
     * it only matters while a snapshot is on screen — the moment it stops advancing, the
     * user has no way to tell a fresh still from a frozen one.
     */
    private fun clock(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    fun retry() {
        actionError.value = null
        attempts.update { it + 1 }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val repository = devices ?: return
        val id = deviceId ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.rename(id, trimmed) }
                .onFailure { actionError.value = "Couldn't rename that camera. Try again." }
        }
    }

    /**
     * Replaces the camera's URIs.
     *
     * Both optional, at least one required — a camera with neither has nothing to show, and
     * writing that state would turn a working camera into a permanently offline one by
     * accident.
     */
    fun updateUris(streamUri: String, snapshotUri: String) {
        val repository = devices ?: return
        val id = deviceId ?: return
        if (streamUri.isBlank() && snapshotUri.isBlank()) {
            actionError.value = "Give at least one URL."
            return
        }
        actionError.value = null

        viewModelScope.launch {
            runCatching {
                repository.updateCameraUris(
                    id,
                    DeviceConfig.Camera(streamUri = streamUri.trim(), snapshotUri = snapshotUri.trim()),
                )
            }.onFailure { actionError.value = "Couldn't save those URLs. Try again." }
        }
    }

    fun delete() {
        val repository = devices ?: return
        val id = deviceId ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.deleteDevice(id) }
                .onFailure { actionError.value = "Couldn't delete that camera. Try again." }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to this camera."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load this camera. Check your connection and try again."
    }

    companion object {
        const val FirebaseMissing =
            "Firebase isn't set up yet. Add app/google-services.json and rebuild."

        private const val TICK_MILLIS = 1_000L

        /** Null [deviceId] backs the wall, which belongs to no single camera. */
        fun factory(deviceId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { CameraViewModel(deviceId) }
        }
    }
}
