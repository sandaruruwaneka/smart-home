package com.smarthome.control.ui.floor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.Live
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.Channel
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.repository.AlertRepository
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UserRepository
import com.smarthome.control.ui.model.DeviceState
import com.smarthome.control.ui.model.DeviceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the floor dashboard.
 *
 * ### What is live, and why it is not one listener
 *
 * Four streams are held open while the screen is shown: the account's floors (for geometry
 * and the floor switcher), this floor's devices, the channels of any switch bank on it, and
 * the account's alerts. The channel listeners are keyed on the set of switch-bank ids
 * rather than on the device list itself — otherwise toggling any light on the floor would
 * tear down and rebuild every channel listener, which is a lot of work to produce the same
 * `2/3` badge.
 *
 * ### Optimistic writes come free, and correctly
 *
 * Firestore applies a write to its local cache before it reaches the server, so the
 * listener re-fires immediately with `hasPendingWrites = true`. That is exactly the
 * optimistic update the screen prompt asks for, and [Live.isFromServer] carries it into
 * the state as [MarkerUiState.pendingWrite]. If the write ultimately fails, Firestore
 * rolls the local value back on its own and the marker reverts — all this class adds is
 * the snackbar.
 *
 * The same flag is what makes the external-change flash honest. Comparing
 * `last_changed_by` would race: the app writes the field, the field comes back, and there
 * is no way to tell a reflection of your own write from somebody else's by reading it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FloorDashboardViewModel(
    private val floorId: String,
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val alerts: AlertRepository? = runCatching { SmartHomeData.alerts }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Write failures, as events. See the floor list's equivalent for why these are not state. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * The status each device was last seen in, and the token that marks the ones somebody
     * else changed.
     *
     * Mutable state in a ViewModel that otherwise derives everything, because the question
     * "did this change since last time" cannot be answered from a single emission. It is
     * touched only from inside the flow's own sequential collection.
     */
    private val lastSeenStatus = mutableMapOf<String, DeviceState>()
    private val externalChangeTokens = mutableMapOf<String, Long>()
    private var changeCounter = 0L

    /** The most recent documents, so an action does not have to re-read what is on screen. */
    private var currentDevices: List<Device> = emptyList()
    private var currentChannels: Map<String, List<Channel>> = emptyMap()

    val state: StateFlow<FloorDashboardUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FloorDashboardUiState(),
        )

    private fun subscribe(): Flow<FloorDashboardUiState> {
        val users = users
        val floors = floors
        val devices = devices
        val alerts = alerts

        if (users == null || floors == null || devices == null || alerts == null) {
            return flowOf(
                FloorDashboardUiState(
                    isLoading = false,
                    error = "Firebase isn't set up yet. Add app/google-services.json and rebuild.",
                ),
            )
        }

        return users.observeAuthState()
            .flatMapLatest { uid ->
                if (uid == null) return@flatMapLatest flowOf(FloorDashboardUiState())

                // Shared so the device query carries one Firestore listener even though
                // two derived streams read it.
                val deviceStream = devices.observeDevicesOnFloor(uid, floorId)
                    .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

                val channelStream = deviceStream
                    .map { live ->
                        live.filter { it.value.type == DeviceType.MULTI_SWITCH }
                            .map { it.value.id }
                            .sorted()
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { bankIds -> observeChannelsOf(devices, bankIds) }

                combine(
                    floors.observeFloors(uid),
                    deviceStream,
                    channelStream,
                    alerts.observeAlerts(uid).map { list -> list.filter { it.floorId == floorId } },
                    clock(),
                ) { floorList, deviceList, channelMap, alertList, nowMillis ->
                    currentDevices = deviceList.map { it.value }
                    currentChannels = channelMap

                    buildFloorDashboardState(
                        floor = floorList.firstOrNull { it.id == floorId },
                        devices = deviceList,
                        channelsByDevice = channelMap,
                        alerts = alertList,
                        floors = floorList,
                        changedExternally = noteExternalChanges(deviceList),
                        nowMillis = nowMillis,
                    )
                }
            }
            .catch { failure ->
                emit(FloorDashboardUiState(isLoading = false, error = failure.userMessage()))
            }
    }

    private fun observeChannelsOf(
        devices: DeviceRepository,
        bankIds: List<String>,
    ): Flow<Map<String, List<Channel>>> {
        if (bankIds.isEmpty()) return flowOf(emptyMap())
        return combine(
            bankIds.map { id ->
                devices.observeChannels(id).map { channels -> id to channels.map(Live<Channel>::value) }
            },
        ) { pairs -> pairs.toMap() }
    }

    /**
     * Works out which devices changed because of somebody else, and hands back the tokens
     * that make their markers flash.
     *
     * A locally initiated change is seen twice: once from the cache with
     * `isFromServer = false`, then again when the server confirms. The first pass records
     * the new status, so by the time the confirmation arrives the status is no longer new
     * and no flash is raised. That is the whole trick — the user does not get flashed at
     * for their own tap.
     *
     * A device seen for the first time never flashes either: arriving is not changing.
     */
    private fun noteExternalChanges(devices: List<Live<Device>>): Map<String, Long> {
        devices.forEach { live ->
            val previous = lastSeenStatus.put(live.value.id, live.value.status)
            if (previous != null && previous != live.value.status && live.isFromServer) {
                externalChangeTokens[live.value.id] = ++changeCounter
            }
        }
        return externalChangeTokens.toMap()
    }

    /**
     * A coarse tick, only for deciding what counts as running against its limit.
     *
     * The hazard chips run their own one-second clock for the digits they show. Rebuilding
     * the whole screen state every second to advance a countdown would recompose the
     * canvas and every marker on it once a second, which is a lot of work to move two
     * characters.
     */
    private fun clock(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    fun retry() = attempts.update { it + 1 }

    /**
     * Switches one device, from a marker's quick actions.
     *
     * The device document is taken from the last emission rather than re-read: it is
     * already on screen, and a read here would put a network round trip in front of a
     * gesture whose whole point is that it feels immediate.
     */
    fun toggle(deviceId: String) {
        val repository = devices ?: return
        val device = currentDevices.firstOrNull { it.id == deviceId } ?: return
        if (device.type == DeviceType.CAMERA || device.type == DeviceType.MULTI_SWITCH) return

        viewModelScope.launch {
            runCatching { repository.setDeviceStatus(device, turnOn = device.status != DeviceState.ON) }
                .onFailure { _messages.tryEmit(WriteFailed) }
        }
    }

    /**
     * Switches off everything on the floor that is on.
     *
     * Sequential rather than parallel, and switch banks go channel by channel because that
     * is the only way their parent's derived status stays correct. Cameras are skipped:
     * their `status` is stream reachability, not power, so there is nothing to switch.
     *
     * One failure does not abandon the rest. A bulk action that stops halfway leaves the
     * floor in a state the user has to work out for themselves.
     */
    fun turnAllOff() {
        val repository = devices ?: return
        val targets = currentDevices.filter { it.status == DeviceState.ON }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            var failed = false

            targets.forEach { device ->
                val outcome = runCatching {
                    when (device.type) {
                        DeviceType.CAMERA -> Unit

                        DeviceType.MULTI_SWITCH -> {
                            val channels = currentChannels[device.id].orEmpty()
                            channels.filter { it.status == DeviceState.ON }.forEach { channel ->
                                repository.setChannelStatus(device, channels, channel.id, turnOn = false)
                            }
                        }

                        else -> repository.setDeviceStatus(device, turnOn = false)
                    }
                }
                if (outcome.isFailure) failed = true
            }

            if (failed) _messages.tryEmit(WriteFailed)
        }
    }

    fun renameFloor(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val repository = floors ?: return

        viewModelScope.launch {
            runCatching { repository.rename(floorId, trimmed) }
                .onFailure { _messages.tryEmit("Couldn't rename that floor. Try again.") }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have access to this floor."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load this floor. Check your connection and try again."
    }

    companion object {
        /** Screen prompt 03 section 10 fixes this string. */
        const val WriteFailed = "Couldn't reach the device. Try again."

        private const val TICK_MILLIS = 5_000L

        /**
         * The floor id is a navigation argument, so the default factory cannot construct
         * this. Every caller goes through here.
         */
        fun factory(floorId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { FloorDashboardViewModel(floorId) }
        }
    }
}
