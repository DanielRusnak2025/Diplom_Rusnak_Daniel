package org.example.androidapp.ui.admin

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.example.androidapp.R
import org.example.dto.AdminBlockDto
import org.example.dto.AdminInterestDto
import org.example.dto.AdminUserDetailDto
import org.example.dto.AdminUserRatingsDto
import org.example.dto.BlockTypeDto
import org.example.dto.RoleDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserDetailScreen(
    viewModel: UserDetailViewModel,
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRole by remember { mutableStateOf<RoleDto?>(null) }

    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.snackbarShown()
    }

    LaunchedEffect(state.roleChanged) {
        if (state.roleChanged) {
            navController.previousBackStackEntry?.savedStateHandle?.set(
                "userRoleChangedId", viewModel.userId,
            )
            viewModel.clearRoleChanged()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Пользователь #${viewModel.userId}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                            Text(stringResource(R.string.btn_retry))
                        }
                    }

                state.user != null ->
                    UserDetailContent(
                        user = state.user!!,
                        isChangingRole = state.isChangingRole,
                        languageByCode = state.languageByCode,
                        regionByKey = state.regionByKey,
                        onRequestRoleChange = { role -> pendingRole = role },
                    )
            }
        }
    }

    pendingRole?.let { role ->
        val user = state.user
        AlertDialog(
            onDismissRequest = { pendingRole = null },
            title = { Text("Изменить роль?") },
            text = {
                Text(
                    if (user != null)
                        "Пользователь ${user.nick}: новая роль — ${roleLabel(role)}."
                    else
                        "Новая роль — ${roleLabel(role)}.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setRole(role)
                    pendingRole = null
                }) { Text("Изменить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRole = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun UserDetailContent(
    user: AdminUserDetailDto,
    isChangingRole: Boolean,
    languageByCode: Map<String, String>,
    regionByKey: Map<String, String>,
    onRequestRoleChange: (RoleDto) -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Nick
        UDetailSection(label = "Ник") {
            Text(user.nick, style = MaterialTheme.typography.titleMedium)
        }

        // Email
        UDetailSection(label = "Email") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.email ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                user.email?.let { email ->
                    IconButton(onClick = { clipboard.setText(AnnotatedString(email)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать email")
                    }
                }
            }
        }

        // Registration date
        UDetailSection(label = "Дата регистрации") {
            Text(user.registrationDate ?: "—", style = MaterialTheme.typography.bodyMedium)
        }

        // Role
        UDetailSection(label = "Роль") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserRoleBadge(role = user.role)
                if (isChangingRole) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleDto.entries.forEach { role ->
                    OutlinedButton(
                        onClick = { onRequestRoleChange(role) },
                        enabled = !isChangingRole && role != user.role,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(roleLabel(role), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        HorizontalDivider()

        // Languages
        if (user.languages.isNotEmpty()) {
            UDetailSection(label = "Языки") {
                Text(
                    text = user.languages.joinToString(", ") { languageByCode[it] ?: it },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Regions
        if (user.regions.isNotEmpty()) {
            UDetailSection(label = "Регионы") {
                Text(
                    text = user.regions.joinToString(", ") { regionByKey[it] ?: it },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HorizontalDivider()

        // Interests
        if (user.interests.isNotEmpty()) {
            UDetailSection(label = "Интересы") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    user.interests.sortedByDescending { kotlin.math.abs(it.percent) }.forEach { interest ->
                        InterestBar(interest = interest)
                    }
                }
            }
        }

        HorizontalDivider()

        // Blocks
        if (user.blocks.isNotEmpty()) {
            UDetailSection(label = "Блокировки") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    user.blocks.forEach { block ->
                        BlockRow(block = block, regionByKey = regionByKey)
                    }
                }
            }
        }

        HorizontalDivider()

        // Ratings
        UDetailSection(label = "Оценки") {
            RatingsSection(ratings = user.ratings)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UDetailSection(label: String, content: @Composable () -> Unit) {
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
private fun InterestBar(interest: AdminInterestDto) {
    val percent = interest.percent
    val displayName = interest.nameRu ?: interest.nameEn ?: interest.tagKey
    val isPositive = percent >= 0
    val fillFraction = kotlin.math.abs(percent).coerceIn(0, 100) / 100f
    val fillColor = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                text = "${if (isPositive) "+" else ""}$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = fillColor,
                fontWeight = FontWeight.Medium,
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
                    .fillMaxWidth(fillFraction)
                    .height(6.dp)
                    .background(fillColor),
            )
        }
    }
}

@Composable
private fun BlockRow(block: AdminBlockDto, regionByKey: Map<String, String>) {
    val label = when (block.blockType) {
        BlockTypeDto.TAG       -> "Тег: ${block.tagKey ?: "—"}"
        BlockTypeDto.REGION    -> "Регион: ${block.region?.let { regionByKey[it] ?: it } ?: "—"}"
        BlockTypeDto.PUBLISHER -> "Издатель: ${block.publisher ?: "—"}"
    }
    Text(label, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun RatingsSection(ratings: AdminUserRatingsDto) {
    val total = ratings.likeCount + ratings.dislikeCount + ratings.complaintCount
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingBar(label = "Лайки", value = ratings.likeCount, total = total, positive = true)
        RatingBar(label = "Дизлайки", value = ratings.dislikeCount, total = total, positive = false)
        RatingBar(label = "Жалобы", value = ratings.complaintCount, total = total, positive = false)
    }
}

@Composable
private fun RatingBar(label: String, value: Int, total: Int, positive: Boolean) {
    val fraction = if (total > 0) value.toFloat() / total else 0f
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(color),
            )
        }
    }
}

private fun roleLabel(role: RoleDto): String = when (role) {
    RoleDto.USER      -> "Пользователь"
    RoleDto.MODERATOR -> "Модератор"
    RoleDto.ADMIN     -> "Администратор"
}
