package com.yaadein.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.yaadein.app.R
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.backup.AutoBackup
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupNudgeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    memoryRepository: MemoryRepository,
) : ViewModel() {
    private val count = MutableStateFlow(0)
    init { viewModelScope.launch { count.value = runCatching { memoryRepository.count() }.getOrDefault(0) } }

    val show: StateFlow<Boolean> = combine(settingsRepository.settings, count) { s, n -> AutoBackup.shouldNudge(s, n, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        viewModelScope.launch { settingsRepository.update { it.copy(backupNudgeDismissedAtMillis = System.currentTimeMillis()) } }
    }
}

/** Home banner once there are a few memories and no backup for a month (memories otherwise live only on this phone). */
@Composable
fun BackupNudgeBanner(onSetUp: () -> Unit, modifier: Modifier = Modifier, viewModel: BackupNudgeViewModel = hiltViewModel()) {
    val show by viewModel.show.collectAsStateWithLifecycle()
    if (!show) return
    val spacing = Yaadein.spacing
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.gutter, vertical = spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(stringResource(R.string.backup_nudge_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_nudge_text), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.md)) {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.backup_nudge_later)) }
                Spacer(Modifier.width(spacing.sm))
                Button(onClick = onSetUp) { Text(stringResource(R.string.backup_nudge_action)) }
            }
        }
    }
}
