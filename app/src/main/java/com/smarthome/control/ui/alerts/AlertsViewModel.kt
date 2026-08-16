package com.smarthome.control.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.model.Alert
import com.smarthome.control.data.repository.AlertRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UserRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Backs the alerts screen.
 *
 * The alert listener is bounded at fifty documents — see [AlertRepository.observeAlerts] for
 * why that matters more than the page size suggests.
 *
 * Nothing here acknowledges anything on its own. Opening the screen must not clear the
 * Critical banner, or a user who glances at the tab loses the notice that something in their
 * house switched itself off (section 6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModel(
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val alerts: AlertRepository? = runCatching { SmartHomeData.alerts }.getOrNull(),
    private val floors: FloorRepository? = runCatching { SmartHomeData.floors }.getOrNull(),
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val filter = MutableStateFlow(AlertFilter.All)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Which alerts arrived while the screen was open, and in what order.
     *
     * Mutable state in a ViewModel that otherwise derives everything, for the same reason
     * the dashboard keeps its own: "is this row new" cannot be answered from a single
     * emission. Touched only from inside the flow's own sequential collection.
     */
    private val seenIds = mutableSetOf<String>()
    private val arrivals = mutableMapOf<String, Long>()
    private var arrivalCounter = 0L
    private var firstEmission = true

    private var currentAlerts: List<Alert> = emptyList()

    val state: StateFlow<AlertsUiState> = attempts
        .flatMapLatest { subscribe() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlertsUiState(),
        )

    private fun subscribe(): Flow<AlertsUiState> {
        val users = users
        val alerts = alerts
        val floors = floors

        if (users == null || alerts == null || floors == null) {
            return flowOf(
                AlertsUiState(
                    isLoading = false,
                    loadError = "Firebase isn't set up yet. Add app/google-services.json and rebuild.",
                ),
            )
        }

        return users.observeAuthState()
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) return@flatMapLatest flowOf(AlertsUiState(isLoading = false))

                combine(
                    alerts.observeAlerts(uid),
                    floors.observeFloors(uid),
                    users.observeCurrentProfile(),
                    filter,
                    clock(),
                ) { alertList, floorList, profile, activeFilter, nowMillis ->
                    currentAlerts = alertList
                    buildAlertsState(
                        alerts = alertList,
                        floors = floorList,
                        filter = activeFilter,
                        arrivals = noteArrivals(alertList),
                        nowMillis = nowMillis,
                        zone = profile?.zoneId ?: ZoneId.systemDefault(),
                    )
                }
            }
            .catch { failure -> emit(AlertsUiState(isLoading = false, loadError = failure.userMessage())) }
    }

    /**
     * Marks the alerts that turned up while the screen was open.
     *
     * The first emission is the existing history and never counts as an arrival — otherwise
     * opening the screen would flash every row at once, which is both meaningless and the
     * opposite of drawing attention to anything.
     */
    private fun noteArrivals(alerts: List<Alert>): Map<String, Long> {
        alerts.forEach { alert ->
            val isNew = seenIds.add(alert.id)
            if (isNew && !firstEmission) arrivals[alert.id] = ++arrivalCounter
        }
        firstEmission = false
        return arrivals.toMap()
    }

    /** A minute is the shortest unit the rows' `14 min ago` renders. */
    private fun clock(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    fun setFilter(next: AlertFilter) = filter.update { next }

    fun retry() = attempts.update { it + 1 }

    fun acknowledge(alertId: String) {
        val repository = alerts ?: return
        viewModelScope.launch {
            runCatching { repository.acknowledge(alertId) }
                .onFailure { _messages.tryEmit(AckFailed) }
        }
    }

    /**
     * Acknowledges everything outstanding, in one batched write.
     *
     * No confirmation: acknowledging deletes nothing and turns nothing on. It says "I have
     * seen this", and asking somebody whether they are sure they have seen something is
     * friction with no decision behind it.
     */
    fun acknowledgeAll() {
        val repository = alerts ?: return
        val outstanding = currentAlerts.filter { !it.acknowledged }.map { it.id }
        if (outstanding.isEmpty()) return

        viewModelScope.launch {
            runCatching { repository.acknowledgeAll(outstanding) }
                .onFailure { _messages.tryEmit(AckFailed) }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to these alerts."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load alerts. Check your connection and try again."
    }

    companion object {
        const val AckFailed = "Couldn't acknowledge that. It'll retry when you're back online."

        private const val TICK_MILLIS = 60_000L

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { AlertsViewModel() }
        }
    }
}
