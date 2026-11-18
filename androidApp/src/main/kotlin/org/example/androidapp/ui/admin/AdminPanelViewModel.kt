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
import org.example.dto.AuditLogItemDto
import org.example.dto.StatsDto

enum class AuditFilter { MODERATION, AUTH, ALL }

val MODERATION_ACTIONS = listOf(
    "ARTICLE_HIDDEN", "ARTICLE_UNHIDDEN", "COMPLAINTS_DISMISSED", "COMPLAINT_DISMISSED", "USER_ROLE_CHANGED",
)
val AUTH_ACTIONS = listOf(
    "LOGIN", "LOGOUT", "LOGIN_FAILED", "REGISTER", "TOKEN_REUSE", "TOKEN_REFRESH", "PASSWORD_CHANGED",
)

fun AuditFilter.toActions(): List<String>? = when (this) {
    AuditFilter.MODERATION -> MODERATION_ACTIONS
    AuditFilter.AUTH       -> AUTH_ACTIONS
    AuditFilter.ALL        -> null
}

data class StatsUiState(
    val isLoading: Boolean = false,
    val stats: StatsDto? = null,
    val error: String? = null,
)

data class AuditUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<AuditLogItemDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val isEndOfList: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val filter: AuditFilter = AuditFilter.MODERATION,
)

class AdminPanelViewModel(private val adminApi: AdminApi) : ViewModel() {

    private val _stats = MutableStateFlow(StatsUiState())
    val stats: StateFlow<StatsUiState> = _stats.asStateFlow()

    private val _audit = MutableStateFlow(AuditUiState())
    val audit: StateFlow<AuditUiState> = _audit.asStateFlow()

    fun loadStats() {
        if (_stats.value.stats != null || _stats.value.isLoading) return
        viewModelScope.launch {
            _stats.update { it.copy(isLoading = true, error = null) }
            try {
                val dto = adminApi.stats()
                _stats.update { it.copy(isLoading = false, stats = dto) }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Access denied" else "Failed to load"
                _stats.update { it.copy(isLoading = false, error = msg) }
            } catch (_: Exception) {
                _stats.update { it.copy(isLoading = false, error = "Failed to load") }
            }
        }
    }

    fun loadAudit() {
        if (_audit.value.items.isNotEmpty() || _audit.value.isLoading) return
        loadAuditInternal()
    }

    fun setAuditFilter(filter: AuditFilter) {
        _audit.value = AuditUiState(filter = filter)
        loadAuditInternal()
    }

    private fun loadAuditInternal() {
        val filter = _audit.value.filter
        viewModelScope.launch {
            _audit.update { it.copy(isLoading = true, error = null) }
            try {
                val page = adminApi.audit(cursor = null, actions = filter.toActions())
                _audit.update {
                    it.copy(
                        isLoading = false,
                        items = page.items,
                        nextCursor = page.nextCursor,
                        isEndOfList = page.nextCursor == null,
                    )
                }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Access denied" else "Failed to load audit log"
                _audit.update { it.copy(isLoading = false, error = msg) }
            } catch (_: Exception) {
                _audit.update { it.copy(isLoading = false, error = "Failed to load audit log") }
            }
        }
    }

    fun loadMoreAudit() {
        val s = _audit.value
        if (s.isLoadingMore || s.isEndOfList || s.nextCursor == null) return
        viewModelScope.launch {
            _audit.update { it.copy(isLoadingMore = true) }
            try {
                val page = adminApi.audit(cursor = s.nextCursor, actions = s.filter.toActions())
                _audit.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + page.items,
                        nextCursor = page.nextCursor,
                        isEndOfList = page.nextCursor == null,
                    )
                }
            } catch (_: Exception) {
                _audit.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refreshAudit() {
        val current = _audit.value
        if (current.isLoading || current.isRefreshing) return
        val filter = current.filter
        if (current.items.isEmpty()) {
            _audit.value = AuditUiState(filter = filter)
            loadAuditInternal()
        } else {
            _audit.update { it.copy(isRefreshing = true, error = null) }
            viewModelScope.launch {
                try {
                    val page = adminApi.audit(cursor = null, actions = filter.toActions())
                    _audit.update {
                        it.copy(
                            isRefreshing = false,
                            items = page.items,
                            nextCursor = page.nextCursor,
                            isEndOfList = page.nextCursor == null,
                        )
                    }
                } catch (e: ApiException) {
                    val msg = if (e.httpStatus == 403) "Access denied" else "Failed to load audit log"
                    _audit.update { it.copy(isRefreshing = false, error = msg) }
                } catch (_: Exception) {
                    _audit.update { it.copy(isRefreshing = false, error = "Failed to load audit log") }
                }
            }
        }
    }
}
