package com.smarthome.control.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the settings screen.
 *
 * One listener — the user profile — because that is the only remote state on the screen.
 * The appearance choice is local to the device and never comes through here; see
 * [AppearancePreference] for why.
 */
class SettingsViewModel(
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    /**
     * Signing out is an event, not state.
     *
     * As a boolean on the state it latched: this ViewModel is scoped to the Activity, which
     * survives a sign-out, so signing back in and reopening Settings handed back the same
     * instance with the flag still true. Its `LaunchedEffect` fired on arrival and bounced
     * the user straight back to Login -- which looked like the Settings tab being broken.
     * An event has no such memory.
     */
    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedOut: SharedFlow<Unit> = _signedOut.asSharedFlow()

    val state: StateFlow<SettingsUiState> = run {
        val users = users
        if (users == null) {
            flowOf(SettingsUiState(isLoading = false, loadError = FirebaseMissing))
        } else {
            combine(users.observeCurrentProfile(), transient) { profile, extra ->
                SettingsUiState(
                    isLoading = false,
                    email = profile?.email.orEmpty(),
                    timezone = profile?.timezone.orEmpty(),
                    isSavingTimezone = extra.isSaving,
                    saveError = extra.saveError,
                )
            }.catch { emit(SettingsUiState(isLoading = false, loadError = LoadFailed)) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /**
     * Writes the home timezone.
     *
     * Existing schedules are deliberately untouched: the stored `"HH:mm"` values simply
     * reinterpret against the new zone. Shifting them would silently rewrite configuration
     * the user set deliberately, and the confirmation dialog says as much before this runs.
     */
    fun setTimezone(zoneId: String) {
        val users = users ?: return
        transient.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            runCatching {
                val uid = users.observeAuthState().first() ?: error("signed out")
                users.updateTimezone(uid, zoneId)
            }.onSuccess {
                transient.update { it.copy(isSaving = false) }
            }.onFailure {
                transient.update { it.copy(isSaving = false, saveError = SaveFailed) }
            }
        }
    }

    /**
     * Ends the session.
     *
     * Firestore's local cache is deliberately *not* cleared. `clearPersistence()` requires
     * terminating the client first and refuses while any listener is live, so calling it
     * mid-session is a reliable way to crash on the way out. The cache is already useless
     * to anyone else: the security rules scope every document to `owner_uid`, so a second
     * account signing in on this phone can read none of it.
     */
    fun signOut() {
        val users = users ?: return
        users.signOut()

        // Announce it only once the auth listener has actually reported null.
        //
        // `signOut()` returns before Firebase has told its listeners, and the login screen's
        // ViewModel is scoped to the Activity, so it still believed the user was signed in.
        // Emitting straight away sent the host to Login, whose `LaunchedEffect` read that
        // stale flag and signed the user back in before the screen had finished composing --
        // which looked like sign-out doing nothing, and left the Settings tab unreachable.
        //
        // Waiting for the null closes the race for every listener at once rather than
        // patching the one screen that noticed.
        viewModelScope.launch {
            users.observeAuthState().first { it == null }
            _signedOut.tryEmit(Unit)
        }
    }

    fun dismissError() = transient.update { it.copy(saveError = null) }

    private data class TransientState(
        val isSaving: Boolean = false,
        val saveError: String? = null,
    )

    companion object {
        const val FirebaseMissing =
            "Firebase isn't set up yet. Add app/google-services.json and rebuild."
        const val SaveFailed = "Couldn't save. Check your connection and try again."
        const val LoadFailed = "Couldn't load your settings."

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel() }
        }
    }
}
