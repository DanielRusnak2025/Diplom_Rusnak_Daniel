package org.example.androidapp.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.admin_complaints_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

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
        ReportReasonDto.NOT_INTERESTED_TOPIC -> R.string.reason_not_interested_topic
        ReportReasonDto.UNWANTED_REGION      -> R.string.reason_unwanted_region
        ReportReasonDto.UNWANTED_PUBLISHER   -> R.string.reason_unwanted_publisher
        ReportReasonDto.NOT_AN_ARTICLE       -> R.string.reason_not_an_article
        ReportReasonDto.BROKEN_LINK          -> R.string.reason_broken_link
        ReportReasonDto.MISLEADING           -> R.string.reason_misleading
        ReportReasonDto.OTHER                -> R.string.reason_other
    }
)
