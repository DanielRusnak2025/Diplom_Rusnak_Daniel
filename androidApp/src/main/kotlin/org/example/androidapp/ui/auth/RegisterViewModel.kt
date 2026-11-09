package org.example.androidapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.androidapp.data.TokenPair
import org.example.androidapp.data.TokenRepository
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.AuthApi
import org.example.dto.RegisterRequest

data class RegisterUiState(
    val login: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class RegisterViewModel(
    private val authApi: AuthApi,
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onLoginChange(v: String) { _state.update { it.copy(login = v, error = null) } }

    fun onPasswordChange(v: String) { _state.update { it.copy(password = v, error = null) } }

    fun register() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val dto = authApi.register(
                    RegisterRequest(
                        nick = _state.value.login,
                        password = _state.value.password,
                    )
                )
                tokenRepository.save(TokenPair(dto.accessToken, dto.refreshToken))
                _state.update { it.copy(isLoading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(isLoading = false, error = mapError(e.code)) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка сети. Проверьте подключение") }
            }
        }
    }

    private fun mapError(code: String): String = when (code) {
        "NICK_TAKEN"    -> "Этот логин уже занят"
        "INVALID_INPUT" -> "Проверьте введённые данные"
        else            -> "Ошибка сервера"
    }
}
