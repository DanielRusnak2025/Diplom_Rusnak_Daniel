package org.example.androidapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.androidapp.network.MeApi
import org.example.androidapp.network.MetaApi
import org.example.dto.CategoryDto
import org.example.dto.LanguageDto
import org.example.dto.RegionDto

data class OnboardingUiState(
    val languages: List<LanguageDto> = emptyList(),
    val selectedLanguages: Set<String> = emptySet(),
    val regions: List<RegionDto> = emptyList(),
    val selectedRegions: Set<String> = emptySet(),
    val categories: List<CategoryDto> = emptyList(),
    val selectedCategories: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false,
)

class OnboardingViewModel(
    private val metaApi: MetaApi,
    private val meApi: MeApi,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        loadLanguages()
    }

    fun loadLanguages() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val langs = metaApi.languages()
                _state.update { it.copy(isLoading = false, languages = langs) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка загрузки языков") }
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

    fun loadRegions() {
        if (_state.value.regions.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val regions = metaApi.regions()
                _state.update { it.copy(isLoading = false, regions = regions) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка загрузки регионов") }
            }
        }
    }

    fun toggleRegion(key: String) {
        _state.update {
            val updated = if (key in it.selectedRegions) it.selectedRegions - key
                          else it.selectedRegions + key
            it.copy(selectedRegions = updated)
        }
    }

    fun loadCategories() {
        if (_state.value.categories.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val cats = metaApi.categories()
                _state.update { it.copy(isLoading = false, categories = cats) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка загрузки категорий") }
            }
        }
    }

    fun toggleCategory(id: Int) {
        _state.update {
            val updated = if (id in it.selectedCategories) it.selectedCategories - id
                          else it.selectedCategories + id
            it.copy(selectedCategories = updated)
        }
    }

    fun complete() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val s = _state.value
                meApi.updateSettings(
                    languages = s.selectedLanguages.toList(),
                    regions = s.selectedRegions.toList(),
                )
                meApi.setInterests(categoryIds = s.selectedCategories.toList())
                _state.update { it.copy(isLoading = false, isDone = true) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка сохранения настроек") }
            }
        }
    }
}
