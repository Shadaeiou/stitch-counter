package com.shadaeiou.stitchcounter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shadaeiou.stitchcounter.BuildConfig
import com.shadaeiou.stitchcounter.update.DownloadResult
import com.shadaeiou.stitchcounter.update.UpdateCheckResult
import com.shadaeiou.stitchcounter.update.UpdateInfo
import com.shadaeiou.stitchcounter.update.Updater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    autoUpdate: Boolean,
    volumeKeys: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    repoOwner: String,
    repoName: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingRow(
                title = "Auto-check for updates",
                description = "Check GitHub on app launch",
                checked = autoUpdate,
                onCheckedChange = onAutoUpdateChange,
            )
            SettingRow(
                title = "Volume keys count",
                description = "Up = +1, Down = -1",
                checked = volumeKeys,
                onCheckedChange = onVolumeKeysChange,
            )

            Spacer(Modifier.height(16.dp))

            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Text("Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

            Button(
                onClick = {
                    checkState = CheckState.Checking
                    scope.launch {
                        when (val result = Updater(context.applicationContext, repoOwner, repoName)
                            .checkForUpdate(BuildConfig.VERSION_CODE)) {
                            UpdateCheckResult.UpToDate -> checkState = CheckState.UpToDate
                            is UpdateCheckResult.Available -> {
                                checkState = CheckState.Idle
                                pending = result.info
                            }
                            is UpdateCheckResult.Error -> checkState = CheckState.Error(result.reason)
                        }
                    }
                },
                enabled = checkState !is CheckState.Checking,
            ) {
                Text(when (checkState) {
                    CheckState.Checking -> "Checking…"
                    else -> "Check for updates"
                })
            }

            when (val s = checkState) {
                CheckState.UpToDate -> Text("Up to date.",
                    color = MaterialTheme.colorScheme.primary)
                is CheckState.Error -> Text("Error: ${s.reason}",
                    color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
    }

    pending?.let { info ->
        UpdateDialog(
            info = info,
            onConfirm = {
                val updater = Updater(context.applicationContext, repoOwner, repoName)
                val id = updater.startDownload(info)
                pending = null
                scope.launch {
                    val result = updater.awaitDownload(id)
                    if (result is DownloadResult.Success) updater.launchInstall(id)
                }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column {
                Text("Version ${info.versionName} (build ${info.versionCode})")
                info.notes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Update") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

private sealed class CheckState {
    data object Idle : CheckState()
    data object Checking : CheckState()
    data object UpToDate : CheckState()
    data class Error(val reason: String) : CheckState()
}
