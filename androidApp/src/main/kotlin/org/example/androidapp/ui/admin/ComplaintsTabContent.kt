package org.example.androidapp.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.example.androidapp.R
import org.example.dto.ComplaintItemDto
import org.example.dto.ReportReasonDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintsTabContent(
    viewModel: ComplaintsViewModel,
    onNavigateToArticle: (Int) -> Unit,
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
            ComplaintsFilterSheet(
                state = state,
                onApply = { reason, reporter, language ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showFilters = false }
                    viewModel.applyFilters(reason, reporter, language)
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
                placeholder = { Text(stringResource(R.string.admin_complaints_search_hint)) },
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
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.admin_btn_retry))
                        }
                    }

                state.items.isEmpty() ->
                    Text(
                        stringResource(R.string.admin_complaints_empty),
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
                        items(state.items, key = { it.reportId }) { item ->
                            ComplaintListCard(
                                item = item,
                                onClick = { onNavigateToArticle(item.articleId) },
                                onHide = { viewModel.requestHide(item) },
                                onDismiss = { viewModel.requestDismiss(item) },
                            )
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

    state.confirmHide?.let { item ->
        HideComplaintConfirmDialog(
            articleTitle = item.articleTitle ?: stringResource(R.string.admin_untitled),
            onConfirm = { viewModel.confirmHide() },
            onDismiss = { viewModel.dismissHideConfirm() },
        )
    }

    state.dismissTarget?.let { item ->
        DismissComplaintConfirmDialog(
            articleTitle = item.articleTitle ?: stringResource(R.string.admin_untitled),
            onConfirm = { viewModel.executeDismiss() },
            onDismiss = { viewModel.cancelDismiss() },
        )
    }
}

@Suppress("unused")
private fun reasonLabel(reason: ReportReasonDto): Int = when (reason) {
    ReportReasonDto.NOT_INTERESTED_TOPIC  -> R.string.admin_reason_not_interested_topic
    ReportReasonDto.UNWANTED_REGION       -> R.string.admin_reason_unwanted_region
    ReportReasonDto.UNWANTED_PUBLISHER    -> R.string.admin_reason_unwanted_publisher
    ReportReasonDto.NOT_AN_ARTICLE        -> R.string.admin_reason_not_an_article
    ReportReasonDto.BROKEN_LINK           -> R.string.admin_reason_broken_link
    ReportReasonDto.MISLEADING            -> R.string.admin_reason_misleading
    ReportReasonDto.OTHER                 -> R.string.admin_reason_other
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComplaintsFilterSheet(
    state: ComplaintsUiState,
    onApply: (ReportReasonDto?, String?, String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(state.reasonFilter) }
    var reporter by remember { mutableStateOf(state.reporterFilter ?: "") }
    var language by remember { mutableStateOf(state.languageFilter) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.admin_filter_sheet_title), style = MaterialTheme.typography.titleMedium)

        // Reason chips — wrap across lines
        Text(stringResource(R.string.admin_filter_reason), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChip(selected = reason == null, onClick = { reason = null }, label = { Text(stringResource(R.string.admin_filter_any)) })
            ReportReasonDto.values().forEach { r ->
                FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(stringResource(reasonLabel(r))) })
            }
        }

        // Reporter nick field
        OutlinedTextField(
            value = reporter,
            onValueChange = { reporter = it },
            label = { Text(stringResource(R.string.admin_filter_reporter)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Article language dropdown
        AdminDropdownField(
            label = stringResource(R.string.admin_filter_article_language),
            selectedKey = language,
            options = listOf(null to stringResource(R.string.admin_filter_any)) +
                state.languageByCode.entries.sortedBy { it.value }.map { it.key to it.value },
            onSelect = { language = it },
        )

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.admin_btn_reset))
            }
            Button(
                onClick = { onApply(reason, reporter.takeIf { it.isNotEmpty() }, language) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.admin_btn_apply))
            }
        }
    }
}

@Composable
private fun ComplaintListCard(
    item: ComplaintItemDto,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
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
            Text(
                text = item.articleTitle ?: stringResource(R.string.admin_untitled),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${item.userNick} · ${item.date ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = complaintReasonLabel(item.reasonCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.admin_complaints_dismiss))
                }
                Button(
                    onClick = onHide,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.admin_complaints_hide_article))
                }
            }
        }
    }
}

@Composable
private fun HideComplaintConfirmDialog(
    articleTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.admin_complaints_hide_confirm, articleTitle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.admin_btn_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(stringResource(R.string.admin_complaints_hide_btn))
                    }
                }
            }
        }
    }
}

@Composable
private fun DismissComplaintConfirmDialog(
    articleTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.admin_complaints_dismiss_confirm, articleTitle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.admin_btn_cancel))
                    }
                    Button(onClick = onConfirm) {
                        Text(stringResource(R.string.admin_complaints_dismiss_btn))
                    }
                }
            }
        }
    }
}

@Composable
private fun complaintReasonLabel(reason: ReportReasonDto): String = stringResource(
    when (reason) {
        ReportReasonDto.NOT_INTERESTED_TOPIC -> R.string.admin_reason_not_interested_topic
        ReportReasonDto.UNWANTED_REGION      -> R.string.admin_reason_unwanted_region
        ReportReasonDto.UNWANTED_PUBLISHER   -> R.string.admin_reason_unwanted_publisher
        ReportReasonDto.NOT_AN_ARTICLE       -> R.string.admin_reason_not_an_article
        ReportReasonDto.BROKEN_LINK          -> R.string.admin_reason_broken_link
        ReportReasonDto.MISLEADING           -> R.string.admin_reason_misleading
        ReportReasonDto.OTHER                -> R.string.admin_reason_other
    }
)
