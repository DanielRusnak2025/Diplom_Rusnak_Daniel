package org.example.androidapp.ui.admin

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.example.androidapp.R
import org.example.dto.AdminArticleDetailDto
import org.example.dto.AdminUserRefDto
import org.example.dto.ReportReasonDto
import org.example.dto.TagRefDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminArticleDetailScreen(
    viewModel: ArticleDetailViewModel,
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showToggleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.snackbarShown()
    }

    LaunchedEffect(state.hiddenChanged) {
        state.hiddenChanged?.let { newHidden ->
            navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                handle["articleHiddenId"] = viewModel.articleId
                handle["articleHiddenValue"] = newHidden
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_article_screen_title, viewModel.articleId)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.background,
                    titleContentColor          = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                        Button(onClick = { viewModel.loadDetail() }) {
                            Text(stringResource(R.string.admin_btn_retry))
                        }
                    }

                state.article != null ->
                    ArticleDetailContent(
                        article = state.article!!,
                        isHiding = state.isHiding,
                        languageByCode = state.languageByCode,
                        regionByKey = state.regionByKey,
                        onToggleHidden = { showToggleDialog = true },
                    )
            }
        }
    }

    if (showToggleDialog) {
        val article = state.article
        if (article != null) {
            AlertDialog(
                onDismissRequest = { showToggleDialog = false },
                title = { Text(if (article.hidden) stringResource(R.string.admin_article_unhide_dialog_title) else stringResource(R.string.admin_article_hide_dialog_title)) },
                text = {
                    Text(
                        if (article.hidden)
                            stringResource(R.string.admin_article_unhide_dialog_body)
                        else
                            stringResource(R.string.admin_article_hide_dialog_body),
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showToggleDialog = false
                        viewModel.toggleHidden()
                    }) { Text(if (article.hidden) stringResource(R.string.admin_article_btn_unhide) else stringResource(R.string.admin_article_btn_hide)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showToggleDialog = false }) {
                        Text(stringResource(R.string.admin_btn_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ArticleDetailContent(
    article: AdminArticleDetailDto,
    isHiding: Boolean,
    languageByCode: Map<String, String>,
    regionByKey: Map<String, String>,
    onToggleHidden: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title
        DetailSection(label = stringResource(R.string.admin_article_field_title)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = article.title ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                article.title?.let { t ->
                    IconButton(onClick = { clipboard.setText(AnnotatedString(t)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.admin_article_copy_title))
                    }
                }
            }
        }

        // Link
        DetailSection(label = stringResource(R.string.admin_article_field_link)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.link ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                article.link?.let { link ->
                    IconButton(onClick = { clipboard.setText(AnnotatedString(link)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.admin_article_copy_link))
                    }
                    IconButton(onClick = {
                        try {
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(link))
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.admin_article_open_browser))
                    }
                }
            }
        }

        // Publisher
        DetailSection(label = stringResource(R.string.admin_article_field_publisher)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.publisher ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                article.publisher?.let { p ->
                    IconButton(onClick = { clipboard.setText(AnnotatedString(p)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.admin_article_copy_publisher))
                    }
                }
            }
        }

        // Meta row
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            article.language?.let { MetaChip(label = stringResource(R.string.admin_article_field_language), value = languageByCode[it] ?: it) }
            article.region?.let { MetaChip(label = stringResource(R.string.admin_article_field_region), value = regionByKey[it] ?: it) }
            article.publicationDate?.let { MetaChip(label = stringResource(R.string.admin_article_field_date), value = it) }
        }

        if (article.hidden) {
            Text(
                stringResource(R.string.admin_article_field_hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Hide/Unhide button
        Button(
            onClick = onToggleHidden,
            enabled = !isHiding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isHiding) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
            } else {
                Text(if (article.hidden) stringResource(R.string.admin_article_btn_unhide) else stringResource(R.string.admin_article_btn_hide))
            }
        }

        HorizontalDivider()

        // Ratings
        DetailSection(label = stringResource(R.string.admin_article_field_ratings)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RatingChip(label = "👍", value = article.likeCount)
                RatingChip(label = "👎", value = article.dislikeCount)
                RatingChip(label = "⚠", value = article.complaintCount)
            }
        }

        // Tags
        if (article.tags.isNotEmpty()) {
            DetailSection(label = stringResource(R.string.admin_article_field_tags)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    article.tags.forEach { tag ->
                        TagBar(tag = tag)
                    }
                }
            }
        }

        // Complainants
        if (article.complainants.isNotEmpty()) {
            DetailSection(label = stringResource(R.string.admin_article_field_complaints)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    article.complainants.forEach { complainant ->
                        ComplainantRow(complainant = complainant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DetailSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun MetaChip(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RatingChip(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(4.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TagBar(tag: TagRefDto) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = tag.name ?: tag.nameRu ?: "", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${tag.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(tag.percent.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ComplainantRow(complainant: AdminUserRefDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = complainant.nick,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        complainant.reasonCode?.let { reason ->
            Text(
                text = reasonLabel(reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun reasonLabel(reason: ReportReasonDto): String = when (reason) {
    ReportReasonDto.NOT_INTERESTED_TOPIC -> "Not interested in topic"
    ReportReasonDto.UNWANTED_REGION      -> "Unwanted region"
    ReportReasonDto.UNWANTED_PUBLISHER   -> "Unwanted publisher"
    ReportReasonDto.NOT_AN_ARTICLE       -> "Not an article"
    ReportReasonDto.BROKEN_LINK          -> "Broken link"
    ReportReasonDto.MISLEADING           -> "Misleading"
    ReportReasonDto.OTHER                -> "Other"
}
