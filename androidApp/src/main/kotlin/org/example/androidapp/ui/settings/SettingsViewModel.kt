package org.example.androidapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.androidapp.data.TokenRepository
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.AuthApi
import org.example.androidapp.network.MeApi
import org.example.androidapp.network.MetaApi
import org.example.dto.CategoryDto
import org.example.dto.LanguageDto
import org.example.dto.RegionDto
import org.example.dto.UserProfileDto

data class SettingsUiState(
    val profile: UserProfileDto? = null,
    val languages: List<LanguageDto> = emptyList(),
    val selectedLanguages: Set<String> = emptySet(),
    val regions: List<RegionDto> = emptyList(),
    val selectedRegions: Set<String> = emptySet(),
    val categories: List<CategoryDto> = emptyList(),
    val selectedCategories: Set<Int> = emptySet(),
    val currentPassword: String = "",
    val newPassword: String = "",
    val isLoading: Boolean = false,
    val isSavingSettings: Boolean = false,
    val isSavingInterests: Boolean = false,
    val isChangingPassword: Boolean = false,
    val error: String? = null,
    val snackbar: String? = null,
    val showLogoutDialog: Boolean = false,
)

class SettingsViewModel(
    private val metaApi: MetaApi,
    private val meApi: MeApi,
    private val authApi: AuthApi,
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val profile   = meApi.me()
                val langs     = metaApi.languages()
                val regions   = metaApi.regions()
                val cats      = metaApi.categories()
                val interests = meApi.interests()

                _state.update { it.copy(
                    isLoading          = false,
                    profile            = profile,
                    languages          = langs,
                    selectedLanguages  = profile.languages.toSet(),
                    regions            = regions,
                    selectedRegions    = profile.regions.toSet(),
                    categories         = cats,
                    selectedCategories = interests.map { i -> i.tagId }.toSet(),
                ) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка загрузки настроек") }
            }
        }
    }

    fun toggleLanguage(code: String) {
        _state.update {
            val updated = if (code in it.selectedLanguages) it.selectedLanguages - code
                          else it.selectedLanguages + code
            it.copy(selectedLanguages = updated)
        }
    }

    fun toggleRegion(key: String) {
        _state.update {
            val updated = if (key in it.selectedRegions) it.selectedRegions - key
                          else it.selectedRegions + key
            it.copy(selectedRegions = updated)
        }
    }

    fun toggleCategory(id: Int) {
        _state.update {
            val updated = if (id in it.selectedCategories) it.selectedCategories - id
                          else it.selectedCategories + id
            it.copy(selectedCategories = updated)
        }
    }

    fun onCurrentPasswordChange(value: String) {
        _state.update { it.copy(currentPassword = value, error = null) }
    }

    fun onNewPasswordChange(value: String) {
        _state.update { it.copy(newPassword = value, error = null) }
    }

    fun snackbarShown() {
        _state.update { it.copy(snackbar = null) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isSavingSettings = true, error = null) }
            try {
                val s = _state.value
                meApi.updateSettings(
                    languages = s.selectedLanguages.toList(),
                    regions = s.selectedRegions.toList(),
                )
                _state.update { it.copy(isSavingSettings = false, snackbar = "Настройки сохранены") }
            } catch (_: Exception) {
                _state.update { it.copy(isSavingSettings = false, error = "Ошибка сохранения. Попробуйте снова.") }
            }
        }
    }

    fun saveInterests() {
        viewModelScope.launch {
            _state.update { it.copy(isSavingInterests = true, error = null) }
            try {
                meApi.setInterests(categoryIds = _state.value.selectedCategories.toList())
                _state.update { it.copy(isSavingInterests = false, snackbar = "Интересы сохранены") }
            } catch (_: Exception) {
                _state.update { it.copy(isSavingInterests = false, error = "Ошибка сохранения. Попробуйте снова.") }
            }
        }
    }

    fun changePassword() {
        viewModelScope.launch {
            _state.update { it.copy(isChangingPassword = true, error = null) }
            try {
                val s = _state.value
                meApi.changePassword(
                    currentPassword = s.currentPassword,
                    newPassword = s.newPassword,
                )
                _state.update { it.copy(
                    isChangingPassword = false,
                    currentPassword = "",
                    newPassword = "",
                    snackbar = "Пароль успешно изменён. Пожалуйста, войдите снова.",
                ) }
            } catch (e: ApiException) {
                val msg = when (e.code) {
                    "INVALID_PASSWORD"  -> "Неверный текущий пароль"
                    "WEAK_PASSWORD"     -> "Слишком простой пароль"
                    "REUSED_PASSWORD"   -> "Нельзя повторять один из последних 3 паролей"
                    "TOO_MANY_ATTEMPTS" -> "Слишком много попыток. Попробуйте позже."
                    else                -> "Ошибка сохранения. Попробуйте снова."
                }
                _state.update { it.copy(isChangingPassword = false, error = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(isChangingPassword = false, error = "Ошибка сохранения. Попробуйте снова.") }
            }
        }
    }

    fun showLogoutDialog() { _state.update { it.copy(showLogoutDialog = true) } }
    fun dismissLogoutDialog() { _state.update { it.copy(showLogoutDialog = false) } }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(showLogoutDialog = false) }
            try {
                val tokens = tokenRepository.loadOnce()
                if (tokens != null) authApi.logout(tokens.refresh)
            } catch (_: Exception) { /* ignore — clear tokens regardless */ }
            tokenRepository.clear()
        }
    }
}
