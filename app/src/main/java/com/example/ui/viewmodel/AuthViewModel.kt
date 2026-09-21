package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserEntity
import com.example.data.repository.FinderKitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD,
    VERIFY_RESET_OTP,
    RESET_NEW_PASSWORD,
    VERIFY_OTP
}

data class AuthUiState(
    val currentUser: UserEntity? = null,
    val pendingUser: UserEntity? = null,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val adminQuickFillNotice: Boolean = false,
    val emailInput: String = "",
    val passwordInput: String = "",
    val fullNameInput: String = "",
    val phoneInput: String = "",
    val newPasswordInput: String = "",
    val otpCodeInput: String = "",
    val dispatchedCode: String? = null,
    val otpTarget: String = "",
    val resendCountdown: Int = 30,
    val pendingSignUpFullName: String = "",
    val pendingSignUpEmail: String = "",
    val pendingSignUpPhone: String = "",
    val pendingSignUpPassword: String = ""
)

class AuthViewModel(private val repository: FinderKitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var logoTapCount = 0
    private var firstTapTime = 0L

    init {
        viewModelScope.launch {
            repository.checkAndSeed()
            val persistedUser = repository.getPersistedSessionUser()
            if (persistedUser != null) {
                _uiState.update { it.copy(currentUser = persistedUser) }
            }
        }
    }

    /**
     * Hidden Admin Access:
     * On the Sign in screen, tap the Vina Prelist logo 5 times quickly (within ~2.5 seconds).
     * Quick-fills admin@finderkit.app into the Sign in email field and shows confirmation.
     * Password is never filled in.
     */
    fun onLogoTapped() {
        val now = System.currentTimeMillis()
        if (now - firstTapTime > 2500L) {
            logoTapCount = 1
            firstTapTime = now
        } else {
            logoTapCount++
        }

        if (logoTapCount >= 5) {
            logoTapCount = 0
            firstTapTime = 0L
            _uiState.update {
                it.copy(
                    emailInput = "admin@finderkit.app",
                    adminQuickFillNotice = true,
                    successMessage = "Admin access detected. Enter credentials to continue."
                )
            }
        }
    }

    fun setAuthMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                authMode = mode,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(emailInput = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(passwordInput = value, errorMessage = null) }
    }

    fun updateFullName(value: String) {
        _uiState.update { it.copy(fullNameInput = value, errorMessage = null) }
    }

    fun updatePhone(value: String) {
        _uiState.update { it.copy(phoneInput = value, errorMessage = null) }
    }

    fun updateNewPassword(value: String) {
        _uiState.update { it.copy(newPasswordInput = value, errorMessage = null) }
    }

    fun updateOtpCode(value: String) {
        val filtered = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpCodeInput = filtered, errorMessage = null) }
        if (filtered.length == 6) {
            if (_uiState.value.authMode == AuthMode.VERIFY_RESET_OTP) {
                verifyResetOtp()
            } else if (_uiState.value.authMode == AuthMode.VERIFY_OTP) {
                verifyOtp()
            }
        }
    }

    fun autoFillDispatchedCode() {
        val code = _uiState.value.dispatchedCode ?: return
        _uiState.update { it.copy(otpCodeInput = code, errorMessage = null) }
        verifyOtp()
    }

    fun dismissAdminNotice() {
        _uiState.update { it.copy(adminQuickFillNotice = false, successMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun signIn() {
        val state = _uiState.value
        if (state.emailInput.isBlank() || state.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both email and password.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.signIn(state.emailInput, state.passwordInput)
            result.fold(
                onSuccess = { user ->
                    repository.saveSessionUser(user.id)
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            pendingUser = null,
                            authMode = AuthMode.SIGN_IN,
                            passwordInput = "",
                            isLoading = false,
                            errorMessage = null,
                            successMessage = "Signed in successfully! Welcome back, ${user.fullName}."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to sign in. Please check your credentials."
                        )
                    }
                }
            )
        }
    }

    fun signInWithBiometricEmail(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No saved account found for Fingerprint sign in.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if (user != null) {
                repository.saveSessionUser(user.id)
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        pendingUser = null,
                        authMode = AuthMode.SIGN_IN,
                        passwordInput = "",
                        isLoading = false,
                        errorMessage = null,
                        successMessage = "Authenticated with Fingerprint! Welcome back, ${user.fullName}."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Account for fingerprint login not found."
                    )
                }
            }
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (state.fullNameInput.isBlank() || state.emailInput.isBlank() || state.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please complete all required fields.") }
            return
        }

        if (state.passwordInput.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val existing = repository.getUserByEmail(state.emailInput.trim().lowercase())
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "An account with email '${state.emailInput}' already exists. Please sign in instead."
                    )
                }
                return@launch
            }

            val targetEmail = state.emailInput.trim().lowercase()
            val code = repository.sendVerificationCode(targetEmail, "SIGN_UP")
            _uiState.update {
                it.copy(
                    authMode = AuthMode.VERIFY_OTP,
                    otpTarget = targetEmail,
                    dispatchedCode = code,
                    otpCodeInput = "",
                    pendingSignUpFullName = state.fullNameInput.trim(),
                    pendingSignUpEmail = targetEmail,
                    pendingSignUpPhone = state.phoneInput.trim(),
                    pendingSignUpPassword = state.passwordInput,
                    isLoading = false,
                    errorMessage = null,
                    successMessage = "Verification code dispatched to $targetEmail"
                )
            }
        }
    }

    fun verifyOtp() {
        val state = _uiState.value
        val target = state.otpTarget
        val code = state.otpCodeInput

        if (code.isBlank() || code.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter the 6-digit verification code.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.verifyCode(target, code)
            result.fold(
                onSuccess = {
                    val signUpResult = repository.signUp(
                        fullName = state.pendingSignUpFullName,
                        email = state.pendingSignUpEmail,
                        phone = state.pendingSignUpPhone,
                        password = state.pendingSignUpPassword
                    )
                    signUpResult.fold(
                        onSuccess = { newUser ->
                            repository.saveSessionUser(newUser.id)
                            _uiState.update {
                                it.copy(
                                    currentUser = newUser,
                                    pendingUser = null,
                                    dispatchedCode = null,
                                    otpCodeInput = "",
                                    isLoading = false,
                                    errorMessage = null,
                                    successMessage = "Account verified & created! Welcome to Vina Prelist, ${newUser.fullName}."
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = err.message ?: "Failed to create account after verification."
                                )
                            }
                        }
                    )
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Invalid verification code."
                        )
                    }
                }
            )
        }
    }

    fun requestPasswordResetCode() {
        val state = _uiState.value
        val email = state.emailInput.trim().lowercase()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your registered email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if (user == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No registered account found with email: $email"
                    )
                }
                return@launch
            }

            val code = repository.sendVerificationCode(email, "FORGOT_PASSWORD")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    authMode = AuthMode.VERIFY_RESET_OTP,
                    otpTarget = email,
                    dispatchedCode = code,
                    otpCodeInput = "",
                    errorMessage = null,
                    successMessage = "A 6-digit verification code was sent to $email."
                )
            }
        }
    }

    fun verifyResetOtp() {
        val state = _uiState.value
        val target = state.otpTarget
        val code = state.otpCodeInput

        if (code.isBlank() || code.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter the 6-digit verification code.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.verifyCode(target, code)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authMode = AuthMode.RESET_NEW_PASSWORD,
                            otpCodeInput = "",
                            dispatchedCode = null,
                            errorMessage = null,
                            successMessage = "Verification successful! Enter your new password below."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Invalid verification code."
                        )
                    }
                }
            )
        }
    }

    fun resendVerificationCode() {
        val state = _uiState.value
        val target = state.otpTarget
        if (target.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val purpose = if (state.authMode == AuthMode.VERIFY_RESET_OTP) "FORGOT_PASSWORD" else "RESEND"
            val code = repository.sendVerificationCode(target, purpose)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    dispatchedCode = code,
                    otpCodeInput = "",
                    successMessage = "A fresh 6-digit security code has been sent."
                )
            }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        val targetEmail = state.otpTarget.ifBlank { state.emailInput }
        if (state.newPasswordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a new password.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.resetPassword(
                email = targetEmail,
                phone = state.phoneInput,
                newPassword = state.newPasswordInput
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authMode = AuthMode.SIGN_IN,
                            newPasswordInput = "",
                            passwordInput = "",
                            otpTarget = "",
                            emailInput = targetEmail,
                            successMessage = "Password reset successfully! Please sign in with your new password."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Could not reset password."
                        )
                    }
                }
            )
        }
    }

    fun signOut() {
        repository.clearSessionUser()
        _uiState.update {
            AuthUiState(
                currentUser = null,
                authMode = AuthMode.SIGN_IN
            )
        }
    }
}
