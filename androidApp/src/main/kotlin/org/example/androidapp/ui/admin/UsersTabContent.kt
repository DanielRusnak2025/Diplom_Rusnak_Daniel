package org.example.androidapp.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.example.androidapp.R
import org.example.dto.AdminUserDto
import org.example.dto.RoleDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersTabContent(
    viewModel: UsersViewModel,
    onNavigateToUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            UsersFilterSheet(
                state = state,
                onApply = { role, language, region, hasComplaints ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showFilters = false }
                    viewModel.applyFilters(role, language, region, hasComplaints)
                },
                onReset = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showFilters = false }
                    viewModel.resetFilters()
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showFilters = false }
                },
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search + Filters button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.admin_users_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            OutlinedButton(onClick = { showFilters = true }) {
                val count = state.activeFilterCount
                Text(if (count > 0) stringResource(R.string.admin_filter_btn_active, count) else stringResource(R.string.admin_filter_btn))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DatePickerField(
                label = stringResource(R.string.admin_date_from),
                value = state.fromDate,
                onValueChange = { viewModel.setDateRange(it, state.toDate) },
                modifier = Modifier.weight(1f),
            )
            DatePickerField(
                label = stringResource(R.string.admin_date_to),
                value = state.toDate,
                onValueChange = { viewModel.setDateRange(state.fromDate, it) },
                modifier = Modifier.weight(1f),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.error != null ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.admin_btn_retry))
                        }
                    }

                state.items.isEmpty() ->
                    Text(
                        stringResource(R.string.admin_users_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )

                else ->
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { user ->
                            UserListItem(
                                user = user,
                                onClick = { onNavigateToUser(user.id) },
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                            }
                        }
                        if (state.isEndOfList && state.items.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.admin_list_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                )
                            }
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersFilterSheet(
    state: UsersUiState,
    onApply: (RoleDto?, String?, String?, Boolean?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var role by remember { mutableStateOf(state.roleFilter) }
    var language by remember { mutableStateOf(state.languageFilter) }
    var region by remember { mutableStateOf(state.regionFilter) }
    var hasComplaints by remember { mutableStateOf(state.hasComplaintsFilter) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.admin_filter_sheet_title), style = MaterialTheme.typography.titleMedium)

        // Role chips
        Text(stringResource(R.string.admin_filter_role), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = role == null, onClick = { role = null }, label = { Text(stringResource(R.string.admin_filter_any)) })
            FilterChip(selected = role == RoleDto.USER, onClick = { role = RoleDto.USER }, label = { Text("User") })
            FilterChip(selected = role == RoleDto.MODERATOR, onClick = { role = RoleDto.MODERATOR }, label = { Text("Mod") })
            FilterChip(selected = role == RoleDto.ADMIN, onClick = { role = RoleDto.ADMIN }, label = { Text("Admin") })
        }

        // Language dropdown
        AdminDropdownField(
            label = stringResource(R.string.admin_filter_language),
            selectedKey = language,
            options = listOf(null to stringResource(R.string.admin_filter_any)) +
                state.languageByCode.entries.sortedBy { it.value }.map { it.key to it.value },
            onSelect = { language = it },
        )

        // Region dropdown
        AdminDropdownField(
            label = stringResource(R.string.admin_filter_region),
            selectedKey = region,
            options = listOf(null to stringResource(R.string.admin_filter_any)) +
                state.regionByKey.entries.sortedBy { it.value }.map { it.key to it.value },
            onSelect = { region = it },
        )

        // Has complaints chips
        Text(stringResource(R.string.admin_filter_has_complaints), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = hasComplaints == null, onClick = { hasComplaints = null }, label = { Text(stringResource(R.string.admin_filter_any)) })
            FilterChip(selected = hasComplaints == true, onClick = { hasComplaints = true }, label = { Text(stringResource(R.string.admin_filter_yes)) })
            FilterChip(selected = hasComplaints == false, onClick = { hasComplaints = false }, label = { Text(stringResource(R.string.admin_filter_no)) })
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.admin_btn_reset))
            }
            Button(
                onClick = { onApply(role, language, region, hasComplaints) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.admin_btn_apply))
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: AdminUserDto,
    onClick: () -> Unit,
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = user.nick,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                UserRoleBadge(role = user.role)
            }
            user.registrationDate?.let { date ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.admin_users_registered, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun UserRoleBadge(role: RoleDto) {
    val (bg, fg, label) = when (role) {
        RoleDto.ADMIN     -> Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, "ADMIN")
        RoleDto.MODERATOR -> Triple(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, "MOD")
        RoleDto.USER      -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "USER")
    }
    Badge(containerColor = bg, contentColor = fg) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
