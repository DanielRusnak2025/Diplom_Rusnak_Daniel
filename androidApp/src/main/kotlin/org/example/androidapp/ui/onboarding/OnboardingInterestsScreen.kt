package org.example.androidapp.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.androidapp.R

@Composable
fun OnboardingInterestsScreen(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadCategories() }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.screen_onboarding_interests_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.subtitle_onboarding_interests),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            state.isLoading && state.categories.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            state.error != null && state.categories.isEmpty() -> {
                Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = viewModel::loadCategories) { Text(stringResource(R.string.btn_retry)) }
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.categories) { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleCategory(cat.id) }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = cat.id in state.selectedCategories,
                                onCheckedChange = { viewModel.toggleCategory(cat.id) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = viewModel::complete,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedCategories.isNotEmpty() && !state.isLoading,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.btn_done))
                    }
                }
            }
        }
    }
}
