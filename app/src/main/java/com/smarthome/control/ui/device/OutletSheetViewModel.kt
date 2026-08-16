package com.smarthome.control.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UsageEventRepository
import com.smarthome.control.ui.model.DeviceState
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the outlet control sheet.
 *
 * ### Three listeners, and why the sheet keeps them open
 *
 * The device document, the floor it stands on (for the `Ground Floor · R2 C5` line), and
 * this device's usage history. Section 7 of the screen prompt is explicit that the sheet
 * must reflect a change made in the simulator *while it is on screen*, so nothing here is
 * a one-shot read and nothing waits for dismissal.
 *
 * The floor listener hangs off the device's own `floor_id` rather than being passed in.
 * The sheet is opened from a marker that already knows its floor, but it is also the
 * destination for `Move to another cell`, and a sheet that keeps showing the old floor
 * after the device has moved is showing a fact that is no longer true.
 *
 * ### The pending state is Firestore's, not this class's
 *
 * A toggle is written straight through. Firestore applies it to the local cache first, the
 * listener re-fires with `hasPendingWrites = true`, and that is the 50 % card the prompt
 * describes — no optimistic copy of the state is kept here. If the write ultimately fails
 * Firestore rolls the cache back on its own; all this class adds is the line of copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutletSheetViewModel(
    private val deviceId: String,
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
    private val usageEvents: UsageEventRepository? =
        runCatching { SmartHomeData.usageEvents }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)

    /** The document as last seen, so an action does not re-read what is already on screen. */
    private var currentDevice: Device? = null

    val state: StateFlow<OutletSheetUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OutletSheetUiState(),
        )

    private fun subscribe(): Flow<OutletSheetUiState> {
        val devices = devices
        val floors = floors
        val usageEvents = usageEvents

        if (devices == null || floors == null || usageEvents == null) {
            return flowOf(
                OutletSheetUiState(
                    isLoading = false,
                    loadError = "Firebase isn't set up yet. Add app/google-services.json and rebuild.",
                ),
            )
        }

        // Shared so the floor lookup rides on the device listener rather than opening a
        // second one on the same document.
        val deviceStream = devices.observeDevice(deviceId)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        val floorStream = deviceStream
            .map { it?.value?.floorId }
            .distinctUntilChanged()
            .flatMapLatest { floorId ->
                if (floorId == null) flowOf(null) else floors.observeFloor(floorId)
            }

        // The owner comes off the device document rather than from a second auth lookup.
        // The filter is not belt-and-braces: Firestore rules are not filters, so a query
        // without it is refused outright and the whole sheet fails to load.
        val usageStream = deviceStream
            .map { it?.value?.ownerUid }
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) flowOf(emptyList()) else usageEvents.observeForDevice(uid, deviceId)
            }

        return combine(
            deviceStream,
            floorStream,
            usageStream,
            clock(),
            actionError,
        ) { device, floor, events, nowMillis, error ->
            currentDevice = device?.value
            buildOutletSheetState(
                device = device,
                floor = floor,
                events = events,
                nowMillis = nowMillis,
            ).copy(actionError = error)
        }.catch { failure ->
            emit(OutletSheetUiState(isLoading = false, loadError = failure.userMessage()))
        }
    }

    /**
     * A one-minute tick.
     *
     * `Time on` has to advance while the device runs (section 7) and the footer's
     * `14 min ago` has to age, and a minute is the shortest unit either of them renders.
     * A faster tick would rebuild the whole state to produce identical text.
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

    /**
     * Switches the outlet.
     *
     * The previous failure line is cleared on the way in: leaving it up next to a control
     * the user has just used again would attach an old failure to a new attempt.
     */
    fun toggle() {
        val repository = devices ?: return
        val device = currentDevice ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.setDeviceStatus(device, turnOn = device.status != DeviceState.ON) }
                .onFailure { actionError.value = WriteFailed }
        }
    }

    /**
     * Clears a reported fault by writing the device off.
     *
     * There is no `clear_error` field in the contract — `ERROR` is a value of `status`, so
     * the only way out of it is another status. `OFF` is the right one: the app cannot know
     * whether a faulty device is actually powered, and claiming it is on would be a guess
     * the user then has to disprove.
     */
    fun clearError() {
        val repository = devices ?: return
        val device = currentDevice ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.setDeviceStatus(device, turnOn = false) }
                .onFailure { actionError.value = WriteFailed }
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.rename(deviceId, trimmed) }
                .onFailure { actionError.value = "Couldn't rename that device. Try again." }
        }
    }

    /**
     * Deletes the device, its channels and its history.
     *
     * The sheet is not dismissed from here. The device listener sees the document go and
     * the state comes back [OutletSheetUiState.isMissing], which is the same path a delete
     * made from another client takes — one way out, whoever pulled the trigger.
     */
    fun delete() {
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.deleteDevice(deviceId) }
                .onFailure { actionError.value = "Couldn't delete that device. Try again." }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have access to this device."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load this device. Check your connection and try again."
    }

    companion object {
        /** Screen prompt 04 section 9 fixes this string, and screen 03 uses the same one. */
        const val WriteFailed = "Couldn't reach the device. Try again."

        private const val TICK_MILLIS = 60_000L

        /** The device id is a navigation argument, so every caller goes through here. */
        fun factory(deviceId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { OutletSheetViewModel(deviceId) }
        }
    }
}
