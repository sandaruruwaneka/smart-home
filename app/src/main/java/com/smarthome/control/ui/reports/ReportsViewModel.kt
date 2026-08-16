package com.smarthome.control.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.repository.AlertRepository
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.UsageEventRepository
import com.smarthome.control.data.repository.UserRepository
import com.smarthome.control.ui.device.startOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Date

/**
 * Backs the reports screen.
 *
 * ### The one screen with no listener
 *
 * Every other screen in the app holds Firestore open. This one reads once, on entry and on
 * range change, and offers a refresh button instead. Reports are a retrospective view: a
 * live listener would re-aggregate a month of usage events every time somebody toggled a
 * lamp, burning reads to redraw a chart nobody is watching change.
 *
 * The refresh action exists precisely because nothing here claims to be live. It is the
 * only manual refresh in the app and it is honest in a way an auto-updating chart would
 * not be.
 *
 * ### And no server-side aggregation
 *
 * Thirty days at this project's scale is a few hundred documents, which is one query and a
 * few milliseconds of arithmetic. Aggregation pipelines, scheduled rollups and a
 * `reports` collection would all be infrastructure built for a problem nobody has.
 */
class ReportsViewModel(
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
    private val devices: DeviceRepository? = runCatching { SmartHomeData.devices }.getOrNull(),
    private val usageEvents: UsageEventRepository? =
        runCatching { SmartHomeData.usageEvents }.getOrNull(),
    private val alerts: AlertRepository? = runCatching { SmartHomeData.alerts }.getOrNull(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    /**
     * Per-range results, so switching tabs back and forth does not refetch.
     *
     * A user comparing `7 days` against `30 days` will bounce between them several times in
     * a few seconds, and paying for the same query on each bounce is the easiest read to
     * not spend.
     */
    private val cache = mutableMapOf<ReportRange, ReportsUiState>()

    init {
        load(ReportRange.Today)
    }

    fun setRange(range: ReportRange) {
        cache[range]?.let { cached ->
            _state.value = cached
            return
        }
        load(range)
    }

    /** Drops the cache and re-reads the current range. */
    fun refresh() {
        cache.clear()
        load(_state.value.range)
    }

    private fun load(range: ReportRange) {
        val users = users
        val devices = devices
        val usageEvents = usageEvents
        val alerts = alerts

        if (users == null || devices == null || usageEvents == null || alerts == null) {
            _state.value = ReportsUiState(isLoading = false, range = range, loadError = FirebaseMissing)
            return
        }

        _state.update { it.copy(isLoading = true, range = range, loadError = null) }

        viewModelScope.launch {
            runCatching {
                val uid = users.observeAuthState().first() ?: error("signed out")
                val zone = users.observeCurrentProfile().first()?.zoneId ?: ZoneId.systemDefault()
                val nowMillis = System.currentTimeMillis()
                val rangeStart = startOfDay(nowMillis, zone) -
                    (range.days - 1).toLong() * MillisPerDay
                val since = Timestamp(Date(rangeStart))

                // The device list comes from the listener the app already holds -- names and
                // types only, so one emission is enough.
                val deviceList = devices.observeDevices(uid).first()

                buildReportsState(
                    range = range,
                    events = usageEvents.getSince(uid, since),
                    devices = deviceList,
                    alerts = alerts.getSince(uid, since),
                    nowMillis = nowMillis,
                    zone = zone,
                    // "Has this account ever recorded anything" is a different question from
                    // "did anything happen in this range", and the empty states depend on
                    // telling them apart. One extra bounded read answers it.
                    hasAnyUsage = usageEvents.getSince(uid, EpochStart).isNotEmpty(),
                )
            }.onSuccess { built ->
                cache[range] = built
                _state.value = built
            }.onFailure { failure ->
                _state.value = ReportsUiState(
                    isLoading = false,
                    range = range,
                    loadError = failure.userMessage(),
                )
            }
        }
    }

    private fun Throwable.userMessage(): String = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to this usage data."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "A Firestore index is missing. Check Logcat for the link that creates it."
        else -> "Couldn't load usage data."
    }

    companion object {
        const val FirebaseMissing =
            "Firebase isn't set up yet. Add app/google-services.json and rebuild."

        private const val MillisPerDay = 24L * 60L * 60L * 1000L

        /** Everything ever, for the "has this account any history at all" question. */
        private val EpochStart = Timestamp(Date(0L))

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportsViewModel() }
        }
    }
}
