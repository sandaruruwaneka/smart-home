package com.smarthome.control.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.smarthome.control.data.SmartHomeData
import com.smarthome.control.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sign in, or create an account — the same screen with a different field set (section 7). */
enum class AuthMode { SignIn, CreateAccount }

/**
 * Everything that can go wrong on this screen, with the exact copy for each.
 *
 * Screen prompt 01 section 6 fixes the first three strings. The rest are conditions the
 * prompt does not enumerate but that Firebase genuinely returns; they are written in the
 * same voice — what happened, then what to do, no apology.
 *
 * The point of the enum is that "wrong password" and "no connection" must never collapse
 * into one message. A user who cannot tell those apart retypes a password that was right.
 */
enum class AuthError(
    val message: String,
    /**
     * Which field borders turn red.
     *
     * Section 5 turns "the field borders" red on failure, which is right when the fault is
     * the pair — a wrong password is indistinguishable from a wrong email. It is wrong
     * when the fault is one field: reddening the password because the email box is empty
     * sends the user to check something that was already fine.
     */
    val marksEmail: Boolean = true,
    val marksPassword: Boolean = true,
) {
    Credentials("That email and password don't match. Check both and try again."),
    Network("No connection. Check your network and try again."),
    EmptyEmail("Enter your email address.", marksPassword = false),
    InvalidEmail("Enter a valid email address.", marksPassword = false),
    EmailTaken("An account already exists for that email. Sign in instead.", marksPassword = false),
    WeakPassword("Use at least 8 characters.", marksEmail = false),

    /**
     * Only reachable before `google-services.json` is added, so only a developer can
     * see it. Naming the missing file beats a generic failure that sends someone
     * looking at their password. Marks nothing — the input was never the problem.
     */
    NotConfigured(
        "Firebase isn't set up yet. Add app/google-services.json and rebuild.",
        marksEmail = false,
        marksPassword = false,
    ),
    Unknown("That didn't work. Try again."),
}

data class LoginUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    val signedIn: Boolean = false,
) {
    /**
     * Whether the fields are filled in enough to try.
     *
     * Deliberately still true while a submission is in flight. Section 5 wants the button
     * to keep its primary fill and swap its label for a spinner during validation, and a
     * disabled button would render `surfaceVariant` instead. The screen ignores taps while
     * [isSubmitting] rather than greying the button out.
     */
    val canSubmit: Boolean
        get() = email.isNotEmpty() &&
            password.isNotEmpty() &&
            (mode == AuthMode.SignIn || name.isNotEmpty())

    /** The password rule is shown from first focus in create mode, not after a failure. */
    val showPasswordRule: Boolean get() = mode == AuthMode.CreateAccount
}

/**
 * Backs the login screen.
 *
 * Holds a nullable [UserRepository] because constructing one calls `FirebaseAuth
 * .getInstance()`, which throws while `google-services.json` is absent. Letting that
 * propagate would crash the app on launch for anyone who cloned the repo and pressed run;
 * instead the screen renders normally and says what is missing when they try to sign in.
 */
class LoginViewModel(
    private val users: UserRepository? = runCatching { SmartHomeData.users }.getOrNull(),
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // Firebase restores the previous session itself, so a returning user is signed in
        // before this screen finishes composing. Section 1's five-second budget is mostly
        // this listener firing rather than anything the user does.
        users?.let { repository ->
            viewModelScope.launch {
                repository.observeAuthState().collect { uid ->
                    _state.update { it.copy(signedIn = uid != null) }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun togglePasswordVisibility() =
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    /**
     * Swaps the field set. The typed email and password survive the switch — someone who
     * tried to sign in, failed, and decided to register should not have to retype them.
     */
    fun toggleMode() = _state.update {
        it.copy(
            mode = if (it.mode == AuthMode.SignIn) AuthMode.CreateAccount else AuthMode.SignIn,
            error = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        // The button unlocks on a non-empty field, but a field holding only spaces is
        // empty as far as Firebase is concerned — this is the case section 6's
        // "Enter your email address." copy exists for.
        val email = current.email.trim()
        if (email.isEmpty()) return fail(AuthError.EmptyEmail)

        if (current.mode == AuthMode.CreateAccount && current.password.length < MIN_PASSWORD) {
            return fail(AuthError.WeakPassword)
        }

        // Checked after the input rules, so a misconfigured build still reports the user's
        // own mistakes first rather than hiding them behind a setup message.
        val repository = users ?: return fail(AuthError.NotConfigured)

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val outcome = runCatching {
                when (current.mode) {
                    AuthMode.SignIn -> repository.signIn(email, current.password)
                    AuthMode.CreateAccount ->
                        repository.register(email, current.password, current.name.trim())
                }
            }
            _state.update {
                it.copy(
                    isSubmitting = false,
                    error = outcome.exceptionOrNull()?.let(::toAuthError),
                )
            }
        }
    }

    private fun fail(error: AuthError) = _state.update { it.copy(error = error) }

    /**
     * Maps a Firebase failure onto the copy the user sees.
     *
     * A missing account maps to [AuthError.Credentials] rather than to anything naming the
     * email: answering "no account with that address" turns the sign-in form into a way to
     * test which addresses are registered.
     */
    private fun toAuthError(throwable: Throwable): AuthError = when (throwable) {
        is FirebaseNetworkException -> AuthError.Network
        is FirebaseAuthUserCollisionException -> AuthError.EmailTaken
        // Weak-password extends invalid-credentials, so it has to be matched first.
        is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
        is FirebaseAuthInvalidCredentialsException ->
            if (throwable.errorCode == INVALID_EMAIL) AuthError.InvalidEmail
            else AuthError.Credentials
        is FirebaseAuthInvalidUserException -> AuthError.Credentials
        else -> AuthError.Unknown
    }

    private companion object {
        const val MIN_PASSWORD = 8
        const val INVALID_EMAIL = "ERROR_INVALID_EMAIL"
    }
}
