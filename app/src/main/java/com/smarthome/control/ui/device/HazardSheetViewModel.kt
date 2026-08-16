package com.smarthome.control.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.repository.AlertRepository
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UsageEventRepository
import com.smarthome.control.data.repository.UserRepository
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
 * Backs the hazard device sheet.
 *
 * ### The clock ticks every second here, and only here
 *
 * Every other screen in the app runs a coarse clock — five seconds on the dashboard, sixty
 * on the other sheets — because their numbers round to the minute. This one shows `12:47`
 * and has to move every second, so it ticks at one hertz while the sheet is open. That is
 * affordable precisely because it is one device's document and one ring, not a canvas of
 * twenty markers.
 *
 * ### The cutoff arrives as somebody else's write
 *
 * When the worker cuts the device off, this ViewModel finds out the same way it finds out
 * about anything else: the device listener re-fires with `status = OFF` and a fresh alert
 * appears. Nothing here polls for it and nothing predicts it — the countdown reaching zero
 * is not the cutoff, it is only the moment the app stops being able to say how long is
 * left. See [HazardSheetUiState.isExpired].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HazardSheetViewModel(
    private val deviceId: String,
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
    private val alerts: AlertRepository? = runCatching { SmartHomeData.alerts }.getOrNull(),
    private val usageEvents: UsageEventRepository? =
        runCatching { SmartHomeData.usageEvents }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)

    private var currentDevice: Device? = null

    val state: StateFlow<HazardSheetUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HazardSheetUiState(),
        )

    private fun subscribe(): Flow<HazardSheetUiState> {
        val users = users
        val devices = devices
        val floors = floors
        val alerts = alerts
        val usageEvents = usageEvents

        if (users == null || devices == null || floors == null || alerts == null || usageEvents == null) {
            return flowOf(
                HazardSheetUiState(
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

        val alertStream = users.observeAuthState().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else alerts.observeAlerts(uid)
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
            alertStream,
            usageStream,
            clock(),
        ) { device, floor, alertList, events, nowMillis ->
            currentDevice = device?.value
            buildHazardSheetState(
                device = device,
                floor = floor,
                alerts = alertList,
                events = events,
                nowMillis = nowMillis,
            )
        }.combine(actionError) { state, error ->
            state.copy(actionError = error)
        }.catch { failure ->
            emit(HazardSheetUiState(isLoading = false, loadError = failure.userMessage()))
        }
    }

    /** One second, because the ring shows seconds. */
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
     * Switches the appliance.
     *
     * Refuses to switch on without a limit. The data layer already requires the field and
     * the UI already disables the control, and this is the third guard: the one that holds
     * when a future caller wires this method to something new.
     */
    fun toggle() {
        val repository = devices ?: return
        val device = currentDevice ?: return
        val turningOn = device.status != DeviceState.ON
        if (turningOn && device.applianceConfig == null) return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.setDeviceStatus(device, turnOn = turningOn) }
                .onFailure { actionError.value = WriteFailed }
        }
    }

    /**
     * Sets the maximum on time.
     *
     * Writing it while the device runs is allowed and takes effect immediately — the worker
     * reads the field on its next tick, so a shortened limit can cut the device off within
     * the minute. The confirmation for that case lives in the sheet, because it is a
     * question for the user rather than a rule for this class.
     */
    fun setLimit(seconds: Long) {
        val repository = devices ?: return
        val clamped = seconds.coerceIn(CustomLimitRange)
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.updateApplianceLimit(deviceId, clamped.toInt()) }
                .onFailure { actionError.value = "Couldn't save that limit. Try again." }
        }
    }

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

    fun delete() {
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.deleteDevice(deviceId) }
                .onFailure { actionError.value = "Couldn't delete that device. Try again." }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to this device."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load this device. Check your connection and try again."
    }

    companion object {
        const val WriteFailed = "Couldn't reach the device. Try again."

        /** One hertz. See the class comment for why this screen alone earns it. */
        private const val TICK_MILLIS = 1_000L

        fun factory(deviceId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { HazardSheetViewModel(deviceId) }
        }
    }
}
