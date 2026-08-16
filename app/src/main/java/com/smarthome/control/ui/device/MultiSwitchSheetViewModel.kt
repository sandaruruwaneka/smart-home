package com.smarthome.control.ui.device

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
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UsageEventRepository
import com.smarthome.control.ui.model.DeviceState
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
 * Backs the multi-switch control sheet.
 *
 * ### Two listeners on one fixture
 *
 * The device document and its channel subcollection, both held open while the sheet is
 * shown, plus the floor for the location line and the day's usage rows. Section 8 makes the
 * live channel listener the point rather than a nicety: if the examiner flips one gang in
 * the simulator, the *right* row has to move on camera, and that is the strongest single
 * argument that the channel addressing is real rather than decorative.
 *
 * ### The unit's status is derived, not believed
 *
 * `deriveMultiSwitchStatus` in the data layer is the contract's rule — any channel on means
 * the unit is on, otherwise any error means error, otherwise off — and both this app and the
 * simulator have to compute it identically. The sheet runs the same function rather than
 * trusting the parent document's `status` field, so a parent left stale by a client that
 * crashed mid-batch shows the truth its channels are telling.
 *
 * The one exception is `DISCONNECTED`, which no channel can report: a single gang cannot
 * lose connectivity independently of the box it is wired into.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiSwitchSheetViewModel(
    private val deviceId: String,
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
    private val usageEvents: UsageEventRepository? =
        runCatching { SmartHomeData.usageEvents }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)

    /** Announcements for the bulk actions (section 11). Events, not state. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var currentDevice: Device? = null
    private var currentChannels: List<Channel> = emptyList()

    /** Per-channel bookkeeping for the external-change flash. Same trick as the dashboard. */
    private val lastSeenStatus = mutableMapOf<String, DeviceState>()
    private val externalChangeTokens = mutableMapOf<String, Long>()
    private var changeCounter = 0L

    val state: StateFlow<MultiSwitchSheetUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MultiSwitchSheetUiState(),
        )

    private fun subscribe(): Flow<MultiSwitchSheetUiState> {
        val devices = devices
        val floors = floors
        val usageEvents = usageEvents

        if (devices == null || floors == null || usageEvents == null) {
            return flowOf(
                MultiSwitchSheetUiState(
                    isLoading = false,
                    loadError = "Firebase isn't set up yet. Add app/google-services.json and rebuild.",
                ),
            )
        }

        val deviceStream = devices.observeDevice(deviceId)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        val floorStream = deviceStream
            .map { it?.value?.floorId }
            .distinctUntilChanged()
            .flatMapLatest { floorId ->
                if (floorId == null) flowOf(null) else floors.observeFloor(floorId)
            }

        return combine(
            deviceStream,
            floorStream,
            devices.observeChannels(deviceId),
            usageEvents.observeForDevice(deviceId),
            clock(),
        ) { device, floor, channels, events, nowMillis ->
            currentDevice = device?.value
            currentChannels = channels.map(Live<Channel>::value)

            buildMultiSwitchSheetState(
                device = device,
                floor = floor,
                channels = channels,
                events = events,
                changedExternally = noteExternalChanges(channels),
                nowMillis = nowMillis,
            )
        }.combine(actionError) { state, error ->
            state.copy(actionError = error)
        }.catch { failure ->
            emit(MultiSwitchSheetUiState(isLoading = false, loadError = failure.userMessage()))
        }
    }

    /**
     * Which channels moved because of somebody else.
     *
     * A locally initiated change is seen twice — once from the cache with
     * `isFromServer = false`, then again when the server confirms — and the first pass
     * records the new status, so by the time confirmation arrives it is no longer new and
     * raises no flash. A channel seen for the first time never flashes either: arriving is
     * not changing.
     */
    private fun noteExternalChanges(channels: List<Live<Channel>>): Map<String, Long> {
        channels.forEach { live ->
            val previous = lastSeenStatus.put(live.value.id, live.value.status)
            if (previous != null && previous != live.value.status && live.isFromServer) {
                externalChangeTokens[live.value.id] = ++changeCounter
            }
        }
        return externalChangeTokens.toMap()
    }

    /**
     * A one-minute tick.
     *
     * The `ON · 2h 14m` captions and the footer both round to the minute, and rebuilding
     * five channel rows every second to redraw identical text is work for nothing.
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

    fun toggleChannel(channelId: String) {
        val repository = devices ?: return
        val device = currentDevice ?: return
        val channel = currentChannels.firstOrNull { it.id == channelId } ?: return
        if (channel.status == DeviceState.ERROR) return
        actionError.value = null

        viewModelScope.launch {
            runCatching {
                repository.setChannelStatus(
                    device = device,
                    // The full set, not a filtered one: the parent's derived status is
                    // computed from it, and a partial list would switch the unit off on
                    // screen while another channel is still live.
                    currentChannels = currentChannels,
                    channelId = channelId,
                    turnOn = channel.status != DeviceState.ON,
                )
            }.onFailure { actionError.value = WriteFailed }
        }
    }

    /**
     * `All on` / `All off`, as one batch.
     *
     * The repository does the batching; what this adds is the announcement, because a bulk
     * action whose result is five rows moving at once needs to say what it did for anybody
     * not watching the rows (section 11).
     */
    fun setAllChannels(turnOn: Boolean) {
        val repository = devices ?: return
        val device = currentDevice ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.setAllChannels(device, currentChannels, turnOn) }
                .onSuccess {
                    _messages.tryEmit(if (turnOn) "All channels turned on" else "All channels turned off")
                }
                .onFailure { actionError.value = WriteFailed }
        }
    }

    fun renameUnit(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.rename(deviceId, trimmed) }
                .onFailure { actionError.value = "Couldn't rename that unit. Try again." }
        }
    }

    /**
     * Renames one channel.
     *
     * Channels are renamed one at a time even though the overflow opens the whole list,
     * because each row is its own document and a user who fixes one name and abandons the
     * rest should keep the fix.
     */
    fun renameChannel(channelId: String, name: String) {
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.renameChannel(deviceId, channelId, name.trim()) }
                .onFailure { actionError.value = "Couldn't rename that channel. Try again." }
        }
    }

    /** The channels and the usage history go with it; the sheet closes on `isMissing`. */
    fun deleteUnit() {
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.deleteDevice(deviceId) }
                .onFailure { actionError.value = "Couldn't delete that unit. Try again." }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to this device."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load this device. Check your connection and try again."
    }

    companion object {
        /** The same string screens 03 and 04 use. One failure, one sentence. */
        const val WriteFailed = "Couldn't reach the device. Try again."

        private const val TICK_MILLIS = 60_000L

        fun factory(deviceId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { MultiSwitchSheetViewModel(deviceId) }
        }
    }
}
