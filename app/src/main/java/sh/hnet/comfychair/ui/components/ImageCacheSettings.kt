package sh.hnet.comfychair.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.cache.ImageCachePolicy
import sh.hnet.comfychair.viewmodel.SettingsViewModel

@Composable
fun ImageCacheSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val policy by viewModel.imageCachePolicy.collectAsState()
    val busy by viewModel.imageCacheBusy.collectAsState()
    val feedback by viewModel.imageCacheFeedback.collectAsState()
    val stats by viewModel.imageCacheStats.collectAsState()
    val statsBusy by viewModel.imageCacheStatsBusy.collectAsState()
    val statsError by viewModel.imageCacheStatsError.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshImageCacheStats(context) }
    var mb by remember(policy) { mutableStateOf(policy.maxMb.toString()) }
    var days by remember(policy) { mutableStateOf(policy.maxDays.toString()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { kotlinx.coroutines.delay(3000); confirm = false } }
    val parsed = ImageCachePolicy.parse(policy.automatic, mb, days)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.image_cache_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.image_cache_scope), style = MaterialTheme.typography.bodySmall)
            stats?.let {
                Text(stringResource(R.string.image_cache_size, android.text.format.Formatter.formatFileSize(context, it.bytes), it.bytes))
                Text(stringResource(R.string.image_cache_count, it.entries))
            }
            if (statsBusy) Text(stringResource(R.string.image_cache_stats_loading))
            if (statsError) Text(stringResource(R.string.image_cache_stats_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.refreshImageCacheStats(context) }, enabled = !busy && !statsBusy) {
                Text(stringResource(R.string.image_cache_stats_refresh))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.image_cache_auto), Modifier.weight(1f))
                Switch(checked = policy.automatic, enabled = !busy && !statsBusy,
                    onCheckedChange = { viewModel.saveImageCachePolicy(context, policy.copy(automatic = it)) })
            }
            OutlinedTextField(value = mb, onValueChange = { mb = it }, singleLine = true,
                enabled = !busy && !statsBusy, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.image_cache_mb)) },
                isError = mb.toIntOrNull() !in 1..1024)
            OutlinedTextField(value = days, onValueChange = { days = it }, singleLine = true,
                enabled = !busy && !statsBusy, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.image_cache_days)) },
                isError = days.toIntOrNull() !in 1..365)
            Text(stringResource(R.string.image_cache_rules), style = MaterialTheme.typography.bodySmall)
            if (parsed == null) Text(stringResource(R.string.image_cache_validation), color = MaterialTheme.colorScheme.error)
            Button(onClick = { parsed?.let { viewModel.saveImageCachePolicy(context, it) } },
                enabled = !busy && !statsBusy && parsed != null && parsed != policy) { Text(stringResource(R.string.image_cache_apply)) }
            OutlinedButton(onClick = {
                if (confirm) { confirm = false; viewModel.clearPersistentImageCache(context) } else confirm = true
            }, enabled = !busy && !statsBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (confirm) R.string.image_cache_confirm else R.string.image_cache_clear))
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.image_cache_working)) }
            feedback?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
