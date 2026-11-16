package org.example.androidapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.androidapp.network.AdminApi
import org.example.androidapp.network.ApiException
import org.example.dto.AdminArticleDto

enum class ArticleHiddenFilter { ALL, VISIBLE, HIDDEN }

private fun ArticleHiddenFilter.toParam(): Boolean? = when (this) {
    ArticleHiddenFilter.ALL     -> null
    ArticleHiddenFilter.VISIBLE -> false
    ArticleHiddenFilter.HIDDEN  -> true
}

data class ArticlesUiState(
    val isLoading: Boolean = false,
    val items: List<AdminArticleDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val isEndOfList: Boolean = false,
    val error: String? = null,
    val nextCursor: Int? = null,
    val query: String = "",
    val hiddenFilter: ArticleHiddenFilter = ArticleHiddenFilter.ALL,
    val fromDate: String? = null,
    val toDate: String? = null,
)

class ArticlesViewModel(private val adminApi: AdminApi) : ViewModel() {

    private val _state = MutableStateFlow(ArticlesUiState())
    val state: StateFlow<ArticlesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init { scheduleLoad() }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(400)
            doLoadFirst()
        }
    }

    fun setHiddenFilter(filter: ArticleHiddenFilter) {
        _state.update { it.copy(hiddenFilter = filter) }
        scheduleLoad()
    }

    fun setDateRange(from: String?, to: String?) {
        _state.update { it.copy(fromDate = from, toDate = to) }
        scheduleLoad()
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || s.isEndOfList || s.nextCursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val page = adminApi.searchArticles(
                    q = s.query.takeIf { it.isNotEmpty() },
                    fromDate = s.fromDate,
                    toDate = s.toDate,
                    hidden = s.hiddenFilter.toParam(),
                    cursor = s.nextCursor,
                )
                _state.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        items = state.items + page.items,
                        nextCursor = page.nextCursor?.toIntOrNull(),
                        isEndOfList = page.nextCursor == null,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun updateArticle(id: Int, hidden: Boolean) {
        _state.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(hidden = hidden) else it })
        }
    }

    fun refresh() { scheduleLoad() }

    private fun scheduleLoad() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { doLoadFirst() }
    }

    private suspend fun doLoadFirst() {
        val s = _state.value
        _state.update { it.copy(
            isLoading = true, error = null, items = emptyList(),
            nextCursor = null, isEndOfList = false,
        ) }
        try {
            val page = adminApi.searchArticles(
                q = s.query.takeIf { it.isNotEmpty() },
                fromDate = s.fromDate,
                toDate = s.toDate,
                hidden = s.hiddenFilter.toParam(),
                cursor = null,
            )
            _state.update { it.copy(
                isLoading = false,
                items = page.items,
                nextCursor = page.nextCursor?.toIntOrNull(),
                isEndOfList = page.nextCursor == null,
            ) }
        } catch (e: ApiException) {
            val msg = if (e.httpStatus == 403) "Недостаточно прав" else "Ошибка загрузки"
            _state.update { it.copy(isLoading = false, error = msg) }
        } catch (_: Exception) {
            _state.update { it.copy(isLoading = false, error = "Ошибка загрузки") }
        }
    }
}
