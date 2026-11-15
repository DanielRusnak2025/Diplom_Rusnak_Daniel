package org.example.androidapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.androidapp.data.TokenRepository
import org.example.androidapp.network.MeApi
import org.example.dto.RoleDto
import org.example.dto.UserProfileDto

class AppViewModel(
    private val tokenRepository: TokenRepository,
    private val meApi: MeApi,
) : ViewModel() {

    sealed interface NavTarget {
        object Loading : NavTarget
        object Auth : NavTarget
        object Onboarding : NavTarget
        object Home : NavTarget
        object AdminPanel : NavTarget
    }

    private val _navTarget = MutableStateFlow<NavTarget>(NavTarget.Loading)
    val navTarget: StateFlow<NavTarget> = _navTarget.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileDto?>(null)
    val profile: StateFlow<UserProfileDto?> = _profile.asStateFlow()

    init {
        viewModelScope.launch {
            var prevWasNull = true
            tokenRepository.tokensFlow().collect { tokens ->
                if (tokens == null) {
                    prevWasNull = true
                    _profile.value = null
                    _navTarget.value = NavTarget.Auth
                } else if (prevWasNull) {
                    prevWasNull = false
                    checkOnboarding()
                }
                // silent token rotation (non-null → non-null): ignore, do not re-navigate
            }
        }
    }

    private suspend fun checkOnboarding() {
        _navTarget.value = NavTarget.Loading
        try {
            val me = meApi.me()
            _profile.value = me
            _navTarget.value = when {
                me.role == RoleDto.ADMIN || me.role == RoleDto.MODERATOR -> NavTarget.AdminPanel
                me.languages.isNotEmpty() -> NavTarget.Home
                else -> NavTarget.Onboarding
            }
        } catch (_: Exception) {
            _navTarget.value = NavTarget.Home
        }
    }
}
