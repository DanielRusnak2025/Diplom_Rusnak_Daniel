package org.example.androidapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.androidapp.network.AdminApi
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.MetaApi
import org.example.dto.AdminArticleDetailDto

data class ArticleDetailUiState(
    val isLoading: Boolean = false,
    val article: AdminArticleDetailDto? = null,
    val error: String? = null,
    val isHiding: Boolean = false,
    val snackbar: String? = null,
    val hiddenChanged: Boolean? = null,
    val languageByCode: Map<String, String> = emptyMap(),
    val regionByKey: Map<String, String> = emptyMap(),
)

class ArticleDetailViewModel(
    private val adminApi: AdminApi,
    private val metaApi: MetaApi,
    val articleId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(ArticleDetailUiState())
    val state: StateFlow<ArticleDetailUiState> = _state.asStateFlow()

    init {
        loadMeta()
        loadDetail()
    }

    private fun loadMeta() {
        viewModelScope.launch {
            try {
                val langs = metaApi.languages()
                val regions = metaApi.regions()
                _state.update { it.copy(
                    languageByCode = langs.mapNotNull { l -> l.name?.let { n -> l.code to n } }.toMap(),
                    regionByKey = regions.mapNotNull { r -> r.nameRu?.let { n -> r.key to n } }.toMap(),
                ) }
            } catch (_: Exception) {}
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val dto = adminApi.getArticleDetail(articleId)
                _state.update { it.copy(isLoading = false, article = dto) }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Недостаточно прав" else "Ошибка загрузки"
                _state.update { it.copy(isLoading = false, error = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Ошибка загрузки") }
            }
        }
    }

    fun toggleHidden() {
        val article = _state.value.article ?: return
        val newHidden = !article.hidden
        viewModelScope.launch {
            _state.update { it.copy(isHiding = true) }
            try {
                adminApi.hideArticle(articleId, newHidden)
                _state.update { state ->
                    state.copy(
                        isHiding = false,
                        article = state.article?.copy(hidden = newHidden),
                        snackbar = if (newHidden) "Статья скрыта" else "Статья показана",
                        hiddenChanged = newHidden,
                    )
                }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Недостаточно прав" else "Ошибка"
                _state.update { it.copy(isHiding = false, snackbar = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(isHiding = false, snackbar = "Ошибка") }
            }
        }
    }

    fun snackbarShown() { _state.update { it.copy(snackbar = null) } }
}
