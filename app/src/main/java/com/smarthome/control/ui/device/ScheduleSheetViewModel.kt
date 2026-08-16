package com.smarthome.control.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.Device
import com.smarthome.control.data.model.DeviceConfig
import com.smarthome.control.data.model.TimeOfDay
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
import java.time.ZoneId

/**
 * Backs the schedule editor.
 *
 * Four streams: the device, its floor, its usage history, and — unique to this sheet — the
 * user profile, because every time on screen is interpreted in the home's timezone rather
 * than the phone's. The scheduler job reads the same field, and the one failure this screen
 * must never have is meaning a different `18:30` than the worker does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleSheetViewModel(
    private val deviceId: String,
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
    private val usageEvents: UsageEventRepository? =
        runCatching { SmartHomeData.usageEvents }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)

    private var currentDevice: Device? = null

    val state: StateFlow<ScheduleSheetUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleSheetUiState(),
        )

    private fun subscribe(): Flow<ScheduleSheetUiState> {
        val users = users
        val devices = devices
        val floors = floors
        val usageEvents = usageEvents

        if (users == null || devices == null || floors == null || usageEvents == null) {
            return flowOf(
                ScheduleSheetUiState(
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
            .flatMapLatest { floorId -> if (floorId == null) flowOf(null) else floors.observeFloor(floorId) }

        // The profile is watched rather than read once: a user who corrects their home
        // timezone should see every time on this sheet move, not have to reopen it.
        val zoneStream = users.observeCurrentProfile()
            .map { it?.zoneId ?: ZoneId.systemDefault() }
            .distinctUntilChanged()

        return combine(
            deviceStream,
            floorStream,
            usageEvents.observeForDevice(deviceId),
            zoneStream,
            clock(),
        ) { device, floor, events, zone, nowMillis ->
            currentDevice = device?.value
            buildScheduleSheetState(
                device = device,
                floor = floor,
                events = events,
                nowMillis = nowMillis,
                zone = zone,
            )
        }.combine(actionError) { state, error -> state.copy(actionError = error) }
            .catch { failure ->
                emit(ScheduleSheetUiState(isLoading = false, loadError = failure.userMessage()))
            }
    }

    /**
     * A one-minute tick.
     *
     * Section 5 recomputes the next-event line every 60 seconds, which is also the shortest
     * unit it renders. The now-hand on the ring moves by a quarter of a degree in that time,
     * so a faster clock would spend recompositions on something nobody can see.
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
     * Toggles the light by hand.
     *
     * Deliberately does not touch the schedule. Section 6: the manual state wins until the
     * next edge and then the schedule resumes — turning a light off at 21:00 is not a
     * request to cancel the evening schedule, and treating it as one would quietly discard
     * configuration the user spent twenty seconds setting up.
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

    fun setScheduleEnabled(enabled: Boolean) {
        val device = currentDevice ?: return
        val light = device.config as? DeviceConfig.Light ?: return
        // An enabled schedule needs both edges -- the model refuses to construct otherwise,
        // and a half-written schedule is one the worker cannot evaluate.
        if (enabled && (light.scheduleOn == null || light.scheduleOff == null)) {
            actionError.value = "Set both times before turning the schedule on."
            return
        }
        write(light.copy(scheduleEnabled = enabled))
    }

    fun setOnTime(time: TimeOfDay) = updateWindow(on = time)

    fun setOffTime(time: TimeOfDay) = updateWindow(off = time)

    private fun updateWindow(on: TimeOfDay? = null, off: TimeOfDay? = null) {
        val device = currentDevice ?: return
        val light = device.config as? DeviceConfig.Light ?: return
        val newOn = on ?: light.scheduleOn
        val newOff = off ?: light.scheduleOff

        if (newOn != null && newOn == newOff) {
            actionError.value = "Start and end times must be different."
            return
        }

        write(
            DeviceConfig.Light(
                // Both edges present is the precondition for an enabled schedule, so
                // setting the first time on a fresh light leaves it off until the second
                // arrives rather than writing a state the model would reject.
                scheduleEnabled = light.scheduleEnabled && newOn != null && newOff != null,
                scheduleOn = newOn,
                scheduleOff = newOff,
            ),
        )
    }

    /**
     * Clears the window and switches the schedule off.
     *
     * Confirmed at the call site (section 11): it is easy to hit by accident and annoying to
     * reconstruct, which is the exact profile of an action that deserves a dialog.
     */
    fun clearSchedule() = write(DeviceConfig.Light.OFF)

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

    private fun write(config: DeviceConfig.Light) {
        val repository = devices ?: return
        actionError.value = null

        viewModelScope.launch {
            runCatching { repository.updateLightSchedule(deviceId, config) }
                .onFailure { actionError.value = ScheduleWriteFailed }
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
        const val ScheduleWriteFailed = "Couldn't save the schedule. Try again."

        private const val TICK_MILLIS = 60_000L

        fun factory(deviceId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScheduleSheetViewModel(deviceId) }
        }
    }
}
