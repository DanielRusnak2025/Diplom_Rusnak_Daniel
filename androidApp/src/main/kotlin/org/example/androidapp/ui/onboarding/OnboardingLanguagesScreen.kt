package org.example.androidapp.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.androidapp.R

@Composable
fun OnboardingLanguagesScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.screen_onboarding_languages_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.subtitle_onboarding_languages),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            state.error != null -> {
                Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = viewModel::loadLanguages) { Text(stringResource(R.string.btn_retry)) }
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.languages) { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleLanguage(lang.code) }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = lang.code in state.selectedLanguages,
                                onCheckedChange = { viewModel.toggleLanguage(lang.code) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lang.code.uppercase(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedLanguages.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.btn_next))
                }
            }
        }
    }
}
