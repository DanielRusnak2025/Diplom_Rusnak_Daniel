package org.example.androidapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.androidapp.data.TokenRepository
import org.example.androidapp.network.MeApi

class AppViewModel(
    private val tokenRepository: TokenRepository,
    private val meApi: MeApi,
) : ViewModel() {

    sealed interface NavTarget {
        object Loading : NavTarget
        object Auth : NavTarget
        object Onboarding : NavTarget
        object Home : NavTarget
    }

    private val _navTarget = MutableStateFlow<NavTarget>(NavTarget.Loading)
    val navTarget: StateFlow<NavTarget> = _navTarget.asStateFlow()

    init {
        viewModelScope.launch {
            tokenRepository.tokensFlow().collect { tokens ->
                if (tokens == null) {
                    _navTarget.value = NavTarget.Auth
                } else {
                    checkOnboarding()
                }
            }
        }
    }

    private suspend fun checkOnboarding() {
        _navTarget.value = NavTarget.Loading
        try {
            val interests = meApi.interests()
            _navTarget.value = if (interests.any { it.percent > 0 }) NavTarget.Home else NavTarget.Onboarding
        } catch (_: Exception) {
            _navTarget.value = NavTarget.Home
        }
    }
}
