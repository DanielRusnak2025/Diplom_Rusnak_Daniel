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
import org.example.dto.ComplaintItemDto

data class ComplaintsUiState(
    val items: List<ComplaintItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEndOfList: Boolean = false,
    val error: String? = null,
    val confirmHide: ComplaintItemDto? = null,
    val dismissTarget: ComplaintItemDto? = null,
    val snackbar: String? = null,
    val hidCount: Int = 0,
    val query: String = "",
    val fromDate: String? = null,
    val toDate: String? = null,
)

class ComplaintsViewModel(private val adminApi: AdminApi) : ViewModel() {

    private val _state = MutableStateFlow(ComplaintsUiState())
    val state: StateFlow<ComplaintsUiState> = _state.asStateFlow()

    private var nextCursor: String? = null
    private var loadJob: Job? = null

    init { scheduleLoad() }

    fun refresh() {
        nextCursor = null
        scheduleLoad()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(400)
            doLoadFirst()
        }
    }

    fun setDateRange(from: String?, to: String?) {
        _state.update { it.copy(fromDate = from, toDate = to) }
        scheduleLoad()
    }

    private fun scheduleLoad() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { doLoadFirst() }
    }

    private suspend fun doLoadFirst() {
        val s = _state.value
        nextCursor = null
        _state.update { it.copy(isLoading = true, error = null, items = emptyList(), isEndOfList = false) }
        try {
            val page = adminApi.complaints(
                cursor = null,
                q = s.query.takeIf { it.isNotEmpty() },
                fromDate = s.fromDate,
                toDate = s.toDate,
            )
            nextCursor = page.nextCursor
            _state.update { it.copy(
                isLoading = false,
                items = page.items,
                isEndOfList = page.nextCursor == null,
            ) }
        } catch (e: ApiException) {
            val msg = if (e.httpStatus == 403) "Недостаточно прав для просмотра жалоб"
                      else "Ошибка загрузки жалоб"
            _state.update { it.copy(isLoading = false, error = msg) }
        } catch (_: Exception) {
            _state.update { it.copy(isLoading = false, error = "Ошибка загрузки жалоб") }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || s.isEndOfList) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val page = adminApi.complaints(
                    cursor = nextCursor,
                    q = s.query.takeIf { it.isNotEmpty() },
                    fromDate = s.fromDate,
                    toDate = s.toDate,
                )
                nextCursor = page.nextCursor
                val existingIds = _state.value.items.map { it.reportId }.toSet()
                val newItems = page.items.filter { it.reportId !in existingIds }
                _state.update { it.copy(
                    isLoadingMore = false,
                    items = it.items + newItems,
                    isEndOfList = page.nextCursor == null,
                ) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun requestHide(item: ComplaintItemDto) {
        _state.update { it.copy(confirmHide = item) }
    }

    fun dismissHideConfirm() {
        _state.update { it.copy(confirmHide = null) }
    }

    fun confirmHide() {
        val item = _state.value.confirmHide ?: return
        _state.update { it.copy(confirmHide = null) }
        viewModelScope.launch {
            try {
                adminApi.hideArticle(item.articleId, true)
                _state.update { s ->
                    s.copy(
                        items = s.items.filter { it.articleId != item.articleId },
                        snackbar = "Статья скрыта",
                        hidCount = s.hidCount + 1,
                    )
                }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Недостаточно прав" else "Не удалось скрыть статью"
                _state.update { it.copy(snackbar = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(snackbar = "Не удалось скрыть статью") }
            }
        }
    }

    fun requestDismiss(item: ComplaintItemDto) {
        _state.update { it.copy(dismissTarget = item) }
    }

    fun cancelDismiss() {
        _state.update { it.copy(dismissTarget = null) }
    }

    fun executeDismiss() {
        val item = _state.value.dismissTarget ?: return
        _state.update { it.copy(dismissTarget = null) }
        viewModelScope.launch {
            try {
                adminApi.dismissComplaint(item.reportId)
                _state.update { s ->
                    s.copy(
                        items = s.items.filter { it.reportId != item.reportId },
                        snackbar = "Жалоба отклонена",
                    )
                }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Недостаточно прав" else "Не удалось отклонить жалобу"
                _state.update { it.copy(snackbar = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(snackbar = "Не удалось отклонить жалобу") }
            }
        }
    }

    fun snackbarShown() {
        _state.update { it.copy(snackbar = null) }
    }
}
