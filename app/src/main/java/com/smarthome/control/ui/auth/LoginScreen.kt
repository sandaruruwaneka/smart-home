package com.smarthome.control.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthome.control.R
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion

/**
 * Screen prompt 01 — Login.
 *
 * ### This screen is designed to be skipped
 *
 * It is the only screen in the app the user wants to spend no time on. A returning user
 * should be looking at their floor list within five seconds, and most of that budget is
 * Firebase restoring the session on its own — this composable is never even reached in
 * that case, because [onSignedIn] fires first.
 *
 * That is also why there is no "remember me" checkbox, no social login, and no hero
 * illustration. Section 2's list of what belongs here ends at the second button, and
 * section 10 lists what to delete if it turns up anyway.
 *
 * ### One composable, two field sets
 *
 * Sign in and create-account are the same screen with a name field added, two swapped
 * labels, and a 200 ms height animation between them (section 7) — not a second
 * destination. Navigating away and back would throw away what the user had typed, which
 * for somebody who has just discovered they need an account is the entire form.
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    LoginContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onToggleMode = viewModel::toggleMode,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

/**
 * The screen without its ViewModel, so the states in the section 9 deliverable can be
 * previewed directly rather than reconstructed by driving a real sign-in.
 */
@Composable
internal fun LoginContent(
    state: LoginUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmartHomeTheme.colors
    val duration = if (rememberReducedMotion()) 0 else 200

    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Section 2: the email field is focused on entry. The user came here to type.
    //
    // Waiting one frame first: a LaunchedEffect body can run before the field's modifier
    // node is attached, and requesting focus on an unattached FocusRequester throws.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { emailFocus.requestFocus() }
    }

    val submit = {
        keyboard?.hide()
        onSubmit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // Section 8: the whole column scrolls once the keyboard is up, so the
                // primary button stays reachable on a short screen.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .animateContentSize(tween(duration)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IdentityBlock()

                // The gap is what establishes that the fields are the working part of the
                // screen, so it is much larger than any other gap here (section 3).
                Spacer(Modifier.height(48.dp))

                FieldGroup(
                    state = state,
                    duration = duration,
                    emailFocus = emailFocus,
                    passwordFocus = passwordFocus,
                    onNameChange = onNameChange,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    onSubmit = submit,
                )

                ErrorMessage(error = state.error, duration = duration)

                Spacer(Modifier.height(Spacing.lg))

                SubmitButton(state = state, onSubmit = submit)

                Spacer(Modifier.height(Spacing.xl))

                TextButton(onClick = onToggleMode, enabled = !state.isSubmitting) {
                    Text(
                        text = when (state.mode) {
                            AuthMode.SignIn -> "Create account"
                            AuthMode.CreateAccount -> "Sign in instead"
                        },
                        style = AppType.body,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/**
 * Wordmark, app name, and one line of positioning copy.
 *
 * Section 4 allows this block the only character on the screen: the icon carries `primary`
 * at full saturation. Everything below it stays quiet, because everything below it is
 * work.
 */
@Composable
private fun IdentityBlock() {
    val colors = SmartHomeTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.primary, AppShapes.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = AppType.display,
                color = colors.textPrimary,
            )
            Text(
                text = "Every device in the house, on one screen.",
                style = AppType.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FieldGroup(
    state: LoginUiState,
    duration: Int,
    emailFocus: FocusRequester,
    passwordFocus: FocusRequester,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    // Which borders redden depends on the error: a wrong password implicates both fields,
    // an empty email box implicates only itself. See AuthError.marksEmail.
    val emailError = state.error?.marksEmail == true
    val passwordError = state.error?.marksPassword == true
    val errorText = state.error?.message

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {

        AnimatedVisibility(
            visible = state.mode == AuthMode.CreateAccount,
            enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
        ) {
            LabeledTextField(
                label = "Name",
                value = state.name,
                onValueChange = onNameChange,
                readOnly = state.isSubmitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
            )
        }

        LabeledTextField(
            label = "Email",
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier.focusRequester(emailFocus),
            // Read-only rather than disabled while validating, so the text the user typed
            // stays at full contrast and they can check it against the error (section 5).
            readOnly = state.isSubmitting,
            isError = emailError,
            errorMessage = errorText.takeIf { emailError },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
        )

        LabeledTextField(
            label = "Password",
            value = state.password,
            onValueChange = onPasswordChange,
            modifier = Modifier.focusRequester(passwordFocus),
            readOnly = state.isSubmitting,
            isError = passwordError,
            errorMessage = errorText.takeIf { passwordError },
            // Section 7: the rule appears from the moment the field is focused, not after
            // a rejected attempt. Being told the requirement afterwards is being told too
            // late.
            helperText = if (state.showPasswordRule) "At least 8 characters." else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation = if (state.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (state.passwordVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        // Names the action the tap performs, and changes with the state so
                        // it never describes what is already on screen.
                        contentDescription = if (state.passwordVisible) {
                            "Hide password"
                        } else {
                            "Show password"
                        },
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
    }
}

/**
 * The inline failure message, directly beneath the field group.
 *
 * Never a toast or a snackbar. Both appear away from the input that caused them and both
 * time out, so the user is left looking at a form with no indication of what was wrong
 * with it (section 5).
 */
@Composable
private fun ErrorMessage(error: AuthError?, duration: Int) {
    val colors = SmartHomeTheme.colors

    AnimatedVisibility(
        visible = error != null,
        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md)
                // Announced when it appears, without stealing focus from the field the
                // user is still sitting in.
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = colors.stateError,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = error?.message.orEmpty(),
                style = AppType.label,
                color = colors.stateError,
            )
        }
    }
}

@Composable
private fun SubmitButton(state: LoginUiState, onSubmit: () -> Unit) {
    val colors = SmartHomeTheme.colors

    Button(
        // Taps are swallowed while a request is in flight rather than the button being
        // disabled, which would drop it to `surfaceVariant` mid-spinner.
        onClick = { if (!state.isSubmitting) onSubmit() },
        enabled = state.canSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = AppShapes.buttonPill,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            // Visibly inert, not merely dimmed (section 5): a disabled button that is just
            // a faded primary still reads as something worth tapping.
            disabledContainerColor = colors.surfaceVariant,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = when (state.mode) {
                    AuthMode.SignIn -> "Sign in"
                    AuthMode.CreateAccount -> "Create account"
                },
                style = AppType.label.copy(fontSize = 14.sp),
                color = colors.onPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews — the section 9 deliverable
// ---------------------------------------------------------------------------

@Composable
private fun PreviewHost(state: LoginUiState, dark: Boolean = true) {
    SmartHomeTheme(darkTheme = dark) {
        LoginContent(
            state = state,
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleMode = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Login · sign in, idle disabled", widthDp = 412, heightDp = 915)
@Composable
private fun LoginIdleDisabled() = PreviewHost(LoginUiState())

@Preview(name = "Login · sign in, idle enabled", widthDp = 412, heightDp = 915)
@Composable
private fun LoginIdleEnabled() = PreviewHost(
    LoginUiState(email = "hasindu@example.com", password = "correcthorse"),
)

@Preview(name = "Login · validating", widthDp = 412, heightDp = 915)
@Composable
private fun LoginValidating() = PreviewHost(
    LoginUiState(email = "hasindu@example.com", password = "correcthorse", isSubmitting = true),
)

@Preview(name = "Login · auth error", widthDp = 412, heightDp = 915)
@Composable
private fun LoginAuthError() = PreviewHost(
    LoginUiState(
        email = "hasindu@example.com",
        password = "wrongpass",
        error = AuthError.Credentials,
    ),
)

@Preview(name = "Login · network error", widthDp = 412, heightDp = 915)
@Composable
private fun LoginNetworkError() = PreviewHost(
    LoginUiState(
        email = "hasindu@example.com",
        password = "correcthorse",
        error = AuthError.Network,
    ),
)

@Preview(name = "Login · create account", widthDp = 412, heightDp = 915)
@Composable
private fun LoginCreateAccount() = PreviewHost(
    LoginUiState(mode = AuthMode.CreateAccount, name = "Hasindu", email = "hasindu@example.com"),
)

@Preview(name = "Login · create account, light", widthDp = 412, heightDp = 915)
@Composable
private fun LoginCreateAccountLight() = PreviewHost(
    state = LoginUiState(mode = AuthMode.CreateAccount, name = "Hasindu"),
    dark = false,
)
