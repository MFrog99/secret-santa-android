package io.github.mfrog99.secretsanta.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mfrog99.secretsanta.data.AppContainer
import io.github.mfrog99.secretsanta.domain.emailError
import io.github.mfrog99.secretsanta.domain.nameError
import io.github.mfrog99.secretsanta.domain.passwordError
import io.github.mfrog99.secretsanta.domain.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isRegister: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
)

class AuthViewModel : ViewModel() {
    private val repository = AppContainer.authRepository

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = null, error = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun toggleMode() = _state.update {
        it.copy(isRegister = !it.isRegister, nameError = null, emailError = null, passwordError = null, error = null)
    }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        val checkedName = if (s.isRegister) nameError(s.name) else null
        val checkedEmail = emailError(s.email)
        // On sign-in the server decides; an old password rule must not lock anyone out.
        val checkedPassword = if (s.isRegister) passwordError(s.password)
        else if (s.password.isEmpty()) "Введите пароль" else null

        if (checkedName != null || checkedEmail != null || checkedPassword != null) {
            _state.update {
                it.copy(nameError = checkedName, emailError = checkedEmail, passwordError = checkedPassword)
            }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val email = s.email.trim()
                if (s.isRegister) repository.signUp(email, s.password, s.name.trim())
                else repository.signIn(email, s.password)
                // On success the session status changes and the app leaves this screen.
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUserMessage()) }
            }
        }
    }
}
