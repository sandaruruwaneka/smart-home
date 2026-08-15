package com.smarthome.control.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the login screen's rules that run before Firebase is reached.
 *
 * The ViewModel takes its repository as a constructor parameter, so passing null here
 * gives a ViewModel that never touches `FirebaseAuth` — which is also the state a
 * developer is in before `google-services.json` exists.
 */
class LoginViewModelTest {

    private fun viewModel() = LoginViewModel(users = null)

    // -------------------------------------------------------- submit gating

    @Test
    fun `sign in needs both fields before the button unlocks`() {
        val state = LoginUiState()
        assertFalse(state.canSubmit)
        assertFalse(state.copy(email = "a@b.com").canSubmit)
        assertFalse(state.copy(password = "secret123").canSubmit)
        assertTrue(state.copy(email = "a@b.com", password = "secret123").canSubmit)
    }

    @Test
    fun `create account also needs a name`() {
        val filled = LoginUiState(
            mode = AuthMode.CreateAccount,
            email = "a@b.com",
            password = "secret123",
        )
        assertFalse(filled.canSubmit)
        assertTrue(filled.copy(name = "Hasindu").canSubmit)
    }

    @Test
    fun `the button stays live while validating so it keeps its primary fill`() {
        val submitting = LoginUiState(
            email = "a@b.com",
            password = "secret123",
            isSubmitting = true,
        )
        assertTrue(submitting.canSubmit)
    }

    // ------------------------------------------------------- input validation

    @Test
    fun `an email of only spaces is empty, not valid`() {
        val model = viewModel()
        model.onEmailChange("   ")
        model.onPasswordChange("secret123")
        model.submit()
        assertEquals(AuthError.EmptyEmail, model.state.value.error)
    }

    @Test
    fun `a short password is rejected before the network is touched`() {
        val model = viewModel()
        model.toggleMode()
        model.onNameChange("Hasindu")
        model.onEmailChange("a@b.com")
        model.onPasswordChange("short")
        model.submit()
        assertEquals(AuthError.WeakPassword, model.state.value.error)
    }

    @Test
    fun `the password rule is not applied when signing in to an existing account`() {
        // An account created before the rule existed may have a shorter password, and
        // refusing to even attempt the sign-in would lock the user out of their own home.
        val model = viewModel()
        model.onEmailChange("a@b.com")
        model.onPasswordChange("short")
        model.submit()
        assertEquals(AuthError.NotConfigured, model.state.value.error)
    }

    @Test
    fun `valid input with no Firebase configured names the missing file`() {
        val model = viewModel()
        model.onEmailChange("a@b.com")
        model.onPasswordChange("secret123")
        model.submit()
        assertEquals(AuthError.NotConfigured, model.state.value.error)
    }

    // ------------------------------------------------------------ interaction

    @Test
    fun `switching mode keeps what was already typed`() {
        val model = viewModel()
        model.onEmailChange("a@b.com")
        model.onPasswordChange("secret123")
        model.submit()
        assertEquals(AuthError.NotConfigured, model.state.value.error)

        model.toggleMode()

        val state = model.state.value
        assertEquals(AuthMode.CreateAccount, state.mode)
        assertEquals("a@b.com", state.email)
        assertEquals("secret123", state.password)
        // The previous failure belonged to the previous attempt.
        assertNull(state.error)
    }

    @Test
    fun `typing clears the previous error`() {
        val model = viewModel()
        model.onEmailChange("   ")
        model.submit()
        assertEquals(AuthError.EmptyEmail, model.state.value.error)

        model.onEmailChange("a@b.com")
        assertNull(model.state.value.error)
    }

    @Test
    fun `password visibility toggles both ways`() {
        val model = viewModel()
        assertFalse(model.state.value.passwordVisible)
        model.togglePasswordVisibility()
        assertTrue(model.state.value.passwordVisible)
        model.togglePasswordVisibility()
        assertFalse(model.state.value.passwordVisible)
    }

    @Test
    fun `the password rule shows only while creating an account`() {
        assertFalse(LoginUiState().showPasswordRule)
        assertTrue(LoginUiState(mode = AuthMode.CreateAccount).showPasswordRule)
    }

    // ----------------------------------------------------------- error marking

    @Test
    fun `a single-field problem reddens only that field`() {
        assertTrue(AuthError.EmptyEmail.marksEmail)
        assertFalse(AuthError.EmptyEmail.marksPassword)

        assertFalse(AuthError.WeakPassword.marksEmail)
        assertTrue(AuthError.WeakPassword.marksPassword)
    }

    @Test
    fun `a mismatched pair reddens both, since either one could be wrong`() {
        assertTrue(AuthError.Credentials.marksEmail)
        assertTrue(AuthError.Credentials.marksPassword)
    }

    @Test
    fun `a setup failure blames no field`() {
        assertFalse(AuthError.NotConfigured.marksEmail)
        assertFalse(AuthError.NotConfigured.marksPassword)
    }

    @Test
    fun `wrong password and no connection never share copy`() {
        assertTrue(AuthError.Credentials.message != AuthError.Network.message)
    }
}
