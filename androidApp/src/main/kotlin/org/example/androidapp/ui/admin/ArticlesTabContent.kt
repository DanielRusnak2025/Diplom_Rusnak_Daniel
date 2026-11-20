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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import org.example.androidapp.R
import org.example.dto.AdminArticleDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesTabContent(
    viewModel: ArticlesViewModel,
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
        ) {
            ArticlesFilterSheet(
                state = state,
                onApply = { lang, region, publisher, category, classified, hasComplaints ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showFilters = false }
                    viewModel.applyFilters(lang, region, publisher, category, classified, hasComplaints)
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
                placeholder = { Text(stringResource(R.string.admin_articles_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            OutlinedButton(onClick = { showFilters = true }) {
                val count = state.activeFilterCount
                Text(if (count > 0) stringResource(R.string.admin_filter_btn_active, count) else stringResource(R.string.admin_filter_btn))
            }
        }

        // Date range
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

        // Hidden filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.hiddenFilter == ArticleHiddenFilter.ALL,
                onClick = { viewModel.setHiddenFilter(ArticleHiddenFilter.ALL) },
                label = { Text(stringResource(R.string.admin_articles_filter_all)) },
            )
            FilterChip(
                selected = state.hiddenFilter == ArticleHiddenFilter.VISIBLE,
                onClick = { viewModel.setHiddenFilter(ArticleHiddenFilter.VISIBLE) },
                label = { Text(stringResource(R.string.admin_articles_filter_visible)) },
            )
            FilterChip(
                selected = state.hiddenFilter == ArticleHiddenFilter.HIDDEN,
                onClick = { viewModel.setHiddenFilter(ArticleHiddenFilter.HIDDEN) },
                label = { Text(stringResource(R.string.admin_articles_filter_hidden)) },
            )
        }

        // List
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
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.admin_btn_retry))
                        }
                    }

                state.items.isEmpty() ->
                    Text(
                        stringResource(R.string.admin_articles_empty),
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
                        items(state.items, key = { it.id }) { article ->
                            ArticleListItem(
                                article = article,
                                languageName = article.language?.let { state.languageByCode[it] ?: it },
                                regionName = article.region?.let { state.regionByKey[it] ?: it },
                                onClick = { onNavigateToArticle(article.id) },
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
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
private fun ArticleListItem(
    article: AdminArticleDto,
    languageName: String?,
    regionName: String?,
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
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = article.title ?: stringResource(R.string.admin_untitled),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (article.hidden) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.admin_articles_hidden_badge), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val publisherDate = listOfNotNull(article.publisher, article.publicationDate).joinToString(" · ")
            if (publisherDate.isNotBlank()) {
                Text(
                    text = publisherDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val langRegion = listOfNotNull(languageName, regionName).joinToString(" / ")
            if (langRegion.isNotBlank()) {
                Text(
                    text = langRegion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticlesFilterSheet(
    state: ArticlesUiState,
    onApply: (String?, String?, String?, String?, Boolean?, Boolean?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var language by remember { mutableStateOf(state.languageFilter) }
    var region by remember { mutableStateOf(state.regionFilter) }
    var publisher by remember { mutableStateOf(state.publisherFilter ?: "") }
    var category by remember { mutableStateOf(state.categoryFilter) }
    var classified by remember { mutableStateOf(state.classifiedFilter) }
    var hasComplaints by remember { mutableStateOf(state.hasComplaintsFilter) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.admin_filter_sheet_title), style = MaterialTheme.typography.titleMedium)

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

        // Publisher text field
        OutlinedTextField(
            value = publisher,
            onValueChange = { publisher = it },
            label = { Text(stringResource(R.string.admin_filter_publisher)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Category dropdown
        AdminDropdownField(
            label = stringResource(R.string.admin_filter_category),
            selectedKey = category,
            options = listOf(null to stringResource(R.string.admin_filter_any)) +
                state.categories.map { it.key to it.name },
            onSelect = { category = it },
        )

        // Classified chips
        Text(stringResource(R.string.admin_filter_classified), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = classified == null, onClick = { classified = null }, label = { Text(stringResource(R.string.admin_filter_any)) })
            FilterChip(selected = classified == true, onClick = { classified = true }, label = { Text(stringResource(R.string.admin_filter_yes)) })
            FilterChip(selected = classified == false, onClick = { classified = false }, label = { Text(stringResource(R.string.admin_filter_no)) })
        }

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
                onClick = { onApply(language, region, publisher.takeIf { it.isNotEmpty() }, category, classified, hasComplaints) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.admin_btn_apply))
            }
        }
    }
}

@Composable
internal fun AdminDropdownField(
    label: String,
    selectedKey: String?,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: options.firstOrNull()?.second ?: ""
    Box {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                }
            },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(key); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    label: String,
    value: String?,
    onValueChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = {
                if (value != null) {
                    IconButton(onClick = { onValueChange(null) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.admin_date_clear))
                    }
                } else {
                    IconButton(onClick = { showDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                }
            },
        )
        if (value == null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showDialog = true },
            )
        }
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    datePickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate()
                        onValueChange(d.toString())
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.admin_btn_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
