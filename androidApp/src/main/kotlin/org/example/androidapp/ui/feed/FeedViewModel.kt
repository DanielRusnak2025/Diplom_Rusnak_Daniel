package org.example.androidapp.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.AuthApi
import org.example.androidapp.network.FeedApi
import org.example.androidapp.data.TokenRepository
import org.example.dto.FeedArticleDto
import org.example.dto.ReportReasonDto

data class FeedUiState(
    val items: List<FeedArticleDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val isEndOfList: Boolean = false,
    val snackbar: String? = null,
    val complaintTarget: FeedArticleDto? = null,
)

class FeedViewModel(
    private val feedApi: FeedApi,
    private val authApi: AuthApi,
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var nextCursor: String? = null

    init { loadFirstPage() }

    fun refresh() {
        nextCursor = null
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, isEndOfList = false) }
            try {
                val page = feedApi.feed(cursor = null)
                nextCursor = page.nextCursor
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = page.items,
                        isEndOfList = page.nextCursor == null,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Не удалось загрузить ленту. Проверьте подключение") }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || s.isEndOfList) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val page = feedApi.feed(cursor = nextCursor)
                nextCursor = page.nextCursor
                val existingIds = _state.value.items.map { it.id }.toSet()
                val newItems = page.items.filter { it.id !in existingIds }
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + newItems,
                        isEndOfList = page.nextCursor == null,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun like(article: FeedArticleDto) {
        val previousRating = article.myRating
        val isUndo = previousRating == 1
        val newRating: Int? = if (isUndo) null else 1
        updateItemRating(article.id, newRating)

        viewModelScope.launch {
            try {
                if (isUndo) feedApi.deleteRate(article.id)
                else feedApi.rate(article.id, 1)
            } catch (_: Exception) {
                updateItemRating(article.id, previousRating)
                _state.update { it.copy(snackbar = "Не удалось оценить статью") }
            }
        }
    }

    fun dislike(article: FeedArticleDto) {
        val idx = _state.value.items.indexOfFirst { it.id == article.id }
        if (idx < 0) return
        removeItemAt(idx)

        viewModelScope.launch {
            try {
                feedApi.rate(article.id, -1)
            } catch (_: Exception) {
                reinsertItem(article, idx)
                _state.update { it.copy(snackbar = "Не удалось оценить статью") }
            }
        }
    }

    fun openComplaintSheet(article: FeedArticleDto) {
        _state.update { it.copy(complaintTarget = article) }
    }

    fun dismissComplaintSheet() {
        _state.update { it.copy(complaintTarget = null) }
    }

    fun submitComplaint(article: FeedArticleDto, reason: ReportReasonDto) {
        val idx = _state.value.items.indexOfFirst { it.id == article.id }
        _state.update { it.copy(complaintTarget = null) }
        if (idx >= 0) removeItemAt(idx)

        viewModelScope.launch {
            try {
                feedApi.complain(article.id, reason)
            } catch (e: ApiException) {
                if (idx >= 0) reinsertItem(article, idx)
                val msg = when (e.code) {
                    "COMPLAINT_CANNOT_BE_REMOVED" -> "Жалоба не может быть удалена"
                    "NOT_FOUND" -> "Статья не найдена"
                    else -> "Не удалось отправить жалобу"
                }
                _state.update { it.copy(snackbar = msg) }
            } catch (_: Exception) {
                if (idx >= 0) reinsertItem(article, idx)
                _state.update { it.copy(snackbar = "Не удалось отправить жалобу") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val tokens = tokenRepository.loadOnce()
                if (tokens != null) authApi.logout(tokens.refresh)
            } catch (_: Exception) { }
            tokenRepository.clear()
        }
    }

    fun snackbarShown() {
        _state.update { it.copy(snackbar = null) }
    }

    private fun updateItemRating(id: Int, newRating: Int?) {
        _state.update {
            it.copy(items = it.items.map { item ->
                if (item.id == id) item.copy(myRating = newRating) else item
            })
        }
    }

    private fun removeItemAt(idx: Int) {
        _state.update { it.copy(items = it.items.toMutableList().also { l -> l.removeAt(idx) }) }
    }

    private fun reinsertItem(item: FeedArticleDto, idx: Int) {
        _state.update {
            val list = it.items.toMutableList()
            val safeIdx = idx.coerceAtMost(list.size)
            list.add(safeIdx, item)
            it.copy(items = list)
        }
    }
}
