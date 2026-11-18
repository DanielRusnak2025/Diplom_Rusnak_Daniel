package org.example.androidapp.ui.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.androidapp.R
import org.example.dto.AuditLogItemDto
import org.example.dto.RoleDto
import org.example.dto.StatsDto

private enum class AdminTab { ARTICLES, USERS, COMPLAINTS, AUDIT, STATS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    role: RoleDto,
    panelVm: AdminPanelViewModel,
    complaintsVm: ComplaintsViewModel,
    articlesVm: ArticlesViewModel? = null,
    usersVm: UsersViewModel? = null,
    onNavigateToFeed: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToArticle: (Int) -> Unit = {},
    onNavigateToUser: (Int) -> Unit = {},
) {
    val tabs = if (role == RoleDto.ADMIN) {
        listOf(AdminTab.ARTICLES, AdminTab.USERS, AdminTab.COMPLAINTS, AdminTab.AUDIT, AdminTab.STATS)
    } else {
        listOf(AdminTab.ARTICLES, AdminTab.COMPLAINTS)
    }
    val tabLabels = tabs.map { tab ->
        when (tab) {
            AdminTab.ARTICLES   -> R.string.admin_tab_articles
            AdminTab.USERS      -> R.string.admin_tab_users
            AdminTab.COMPLAINTS -> R.string.admin_tab_complaints
            AdminTab.AUDIT      -> R.string.admin_tab_audit
            AdminTab.STATS      -> R.string.admin_tab_stats
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val statsState by panelVm.stats.collectAsStateWithLifecycle()
    val auditState by panelVm.audit.collectAsStateWithLifecycle()
    val complaintsState by complaintsVm.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(complaintsState.snackbar) {
        val msg = complaintsState.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        complaintsVm.snackbarShown()
    }

    LaunchedEffect(tabs[selectedTab]) {
        when (tabs[selectedTab]) {
            AdminTab.STATS -> panelVm.loadStats()
            AdminTab.AUDIT -> panelVm.refreshAudit()
            else -> Unit
        }
    }

    val auditListState = rememberLazyListState()
    val shouldLoadMoreAudit by remember {
        derivedStateOf {
            val lastVisible = auditListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = auditListState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMoreAudit) {
        if (shouldLoadMoreAudit) panelVm.loadMoreAudit()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_screen_title)) },
                actions = {
                    TextButton(onClick = onNavigateToFeed) {
                        Text(stringResource(R.string.admin_btn_feed))
                    }
                    TextButton(onClick = { showLogoutDialog = true }) {
                        Text(stringResource(R.string.admin_btn_logout))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.background,
                    titleContentColor      = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabLabels.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }

            when (tabs[selectedTab]) {
                AdminTab.ARTICLES ->
                    if (articlesVm != null) {
                        ArticlesTabContent(
                            viewModel = articlesVm,
                            onNavigateToArticle = onNavigateToArticle,
                        )
                    } else {
                        PlaceholderContent()
                    }

                AdminTab.USERS ->
                    if (usersVm != null) {
                        UsersTabContent(
                            viewModel = usersVm,
                            onNavigateToUser = onNavigateToUser,
                        )
                    } else {
                        PlaceholderContent()
                    }

                AdminTab.COMPLAINTS ->
                    ComplaintsTabContent(
                        viewModel = complaintsVm,
                        onNavigateToArticle = onNavigateToArticle,
                    )

                AdminTab.AUDIT ->
                    AuditTabContent(
                        state = auditState,
                        listState = auditListState,
                        onRetry = panelVm::refreshAudit,
                        onFilterChange = panelVm::setAuditFilter,
                        onRefresh = panelVm::refreshAudit,
                    )

                AdminTab.STATS ->
                    StatsTabContent(
                        state = statsState,
                        onRetry = panelVm::loadStats,
                    )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.admin_btn_logout)) },
            text = { Text(stringResource(R.string.admin_dialog_logout_message)) },
            confirmButton = {
                Button(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.admin_btn_logout))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.admin_btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaceholderContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.admin_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsTabContent(
    state: StatsUiState,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            state.error != null ->
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.admin_btn_retry)) }
                }

            state.stats != null ->
                StatsContent(stats = state.stats)
        }
    }
}

@Composable
private fun StatsContent(stats: StatsDto) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { StatCard(label = stringResource(R.string.admin_stats_total_articles), value = stats.totalArticles.toString()) }
        item { StatCard(label = stringResource(R.string.admin_stats_classified), value = stats.classifiedArticles.toString()) }
        item { StatCard(label = stringResource(R.string.admin_stats_pending), value = stats.pendingQueue.toString()) }
        item { StatCard(label = stringResource(R.string.admin_stats_total_users), value = stats.totalUsers.toString()) }
        item { StatCard(label = stringResource(R.string.admin_stats_open_complaints), value = stats.openComplaints.toString()) }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditTabContent(
    state: AuditUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRetry: () -> Unit,
    onFilterChange: (AuditFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filter == AuditFilter.MODERATION,
                onClick = { onFilterChange(AuditFilter.MODERATION) },
                label = { Text(stringResource(R.string.admin_audit_filter_moderation)) },
            )
            FilterChip(
                selected = state.filter == AuditFilter.AUTH,
                onClick = { onFilterChange(AuditFilter.AUTH) },
                label = { Text(stringResource(R.string.admin_audit_filter_auth)) },
            )
            FilterChip(
                selected = state.filter == AuditFilter.ALL,
                onClick = { onFilterChange(AuditFilter.ALL) },
                label = { Text(stringResource(R.string.admin_audit_filter_all)) },
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.error != null ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRetry) { Text(stringResource(R.string.admin_btn_retry)) }
                    }

                state.items.isEmpty() ->
                    Text(
                        stringResource(R.string.admin_audit_empty),
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
                        items(state.items, key = { it.id }) { item ->
                            AuditCard(item = item)
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        if (state.isEndOfList && state.items.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.admin_list_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun AuditCard(item: AuditLogItemDto) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = auditActionLabel(item.action),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            val actor = item.actorNick ?: "—"
            val time = formatAuditDate(item.createdAt)
            Text(
                text = "$actor · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            formatAuditTarget(item.targetType, item.targetId, item.targetNick)?.let { target ->
                Text(
                    text = target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.details?.takeIf { it.isNotBlank() }?.let { details ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatAuditDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val t = iso.split("T")
        if (t.size != 2) return iso
        val d = t[0].split("-")
        if (d.size != 3) return iso
        "${d[2]}.${d[1]}.${d[0]} ${t[1].take(5)}"
    } catch (_: Exception) { iso }
}

private fun formatAuditTarget(targetType: String?, targetId: String?, targetNick: String?): String? {
    if (targetType == null || targetId == null) return null
    return when (targetType.lowercase()) {
        "user"    -> if (targetNick != null) "User $targetNick" else "User #$targetId"
        "article" -> "Article #$targetId"
        else      -> "${targetType.replaceFirstChar { it.uppercase() }} #$targetId"
    }
}
