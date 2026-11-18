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
import org.example.dto.AdminUserDetailDto
import org.example.dto.RoleDto

data class UserDetailUiState(
    val isLoading: Boolean = false,
    val user: AdminUserDetailDto? = null,
    val error: String? = null,
    val isChangingRole: Boolean = false,
    val snackbar: String? = null,
    val roleChanged: Boolean = false,
    val languageByCode: Map<String, String> = emptyMap(),
    val regionByKey: Map<String, String> = emptyMap(),
)

class UserDetailViewModel(
    private val adminApi: AdminApi,
    private val metaApi: MetaApi,
    val userId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(UserDetailUiState())
    val state: StateFlow<UserDetailUiState> = _state.asStateFlow()

    init {
        loadMeta()
        loadDetail()
    }

    private fun loadMeta() {
        viewModelScope.launch {
            try {
                val names = metaApi.languageNames("en")
                val regions = metaApi.regions()
                _state.update { it.copy(
                    languageByCode = names,
                    regionByKey = regions.mapNotNull { r -> r.name?.let { n -> r.key to n } }.toMap(),
                ) }
            } catch (_: Exception) {}
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val dto = adminApi.getUserDetail(userId)
                _state.update { it.copy(isLoading = false, user = dto) }
            } catch (e: ApiException) {
                val msg = if (e.httpStatus == 403) "Access denied" else "Failed to load"
                _state.update { it.copy(isLoading = false, error = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load") }
            }
        }
    }

    fun setRole(newRole: RoleDto) {
        viewModelScope.launch {
            _state.update { it.copy(isChangingRole = true) }
            try {
                adminApi.setUserRole(userId, newRole)
                _state.update { it.copy(
                    isChangingRole = false,
                    user = _state.value.user?.copy(role = newRole),
                    snackbar = "Role updated",
                    roleChanged = true,
                ) }
            } catch (e: ApiException) {
                val msg = when (e.httpStatus) {
                    409  -> "Cannot change your own role"
                    403  -> "Access denied"
                    else -> "Error"
                }
                _state.update { it.copy(isChangingRole = false, snackbar = msg) }
            } catch (_: Exception) {
                _state.update { it.copy(isChangingRole = false, snackbar = "Error") }
            }
        }
    }

    fun snackbarShown() { _state.update { it.copy(snackbar = null) } }
    fun clearRoleChanged() { _state.update { it.copy(roleChanged = false) } }
}
