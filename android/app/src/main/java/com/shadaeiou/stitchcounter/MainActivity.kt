package com.shadaeiou.stitchcounter

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.shadaeiou.stitchcounter.ui.MainScreen
import com.shadaeiou.stitchcounter.ui.settings.SettingsScreen
import com.shadaeiou.stitchcounter.ui.theme.StitchCounterTheme
import com.shadaeiou.stitchcounter.update.DownloadResult
import com.shadaeiou.stitchcounter.update.UpdateCheckResult
import com.shadaeiou.stitchcounter.update.UpdateInfo
import com.shadaeiou.stitchcounter.update.Updater
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import kotlinx.coroutines.launch

private const val REPO_OWNER = "shadaeiou"
private const val REPO_NAME = "stitch-counter"

class MainActivity : ComponentActivity() {

    val counterVm: CounterViewModel by viewModels { CounterViewModel.Factory() }

    @Volatile private var volumeKeysEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            StitchCounterApp.instance.prefs.volumeKeysFlow.collect { volumeKeysEnabled = it }
        }

        setContent {
            StitchCounterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(activityVm = counterVm)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volumeKeysEnabled) {
            val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            if (isVolumeKey) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val haptics = StitchCounterApp.instance.haptics
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            counterVm.increment(); haptics.light()
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            counterVm.decrement(); haptics.medium()
                        }
                    }
                }
                return true  // always swallow so system volume doesn't change
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun AppRoot(activityVm: CounterViewModel) {
    val app = StitchCounterApp.instance
    val prefs = app.prefs

    val autoUpdate by prefs.autoUpdateFlow.collectAsState(initial = true)
    val volumeKeys by prefs.volumeKeysFlow.collectAsState(initial = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }
    var screen by remember { mutableStateOf(Screen.Main) }

    LaunchedEffect(autoUpdate) {
        if (!autoUpdate) return@LaunchedEffect
        val updater = Updater(context.applicationContext, REPO_OWNER, REPO_NAME)
        val result = updater.checkForUpdate(BuildConfig.VERSION_CODE)
        if (result is UpdateCheckResult.Available) pending = result.info
    }

    when (screen) {
        Screen.Main -> MainScreen(
            vm = activityVm,
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Settings -> {
            BackHandler { screen = Screen.Main }
            SettingsScreen(
                autoUpdate = autoUpdate,
                volumeKeys = volumeKeys,
                onAutoUpdateChange = { v -> scope.launch { prefs.setAutoUpdate(v) } },
                onVolumeKeysChange = { v -> scope.launch { prefs.setVolumeKeys(v) } },
                onBack = { screen = Screen.Main },
                repoOwner = REPO_OWNER,
                repoName = REPO_NAME,
            )
        }
    }

    pending?.let { info ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Update available") },
            text = {
                Text(
                    "Version ${info.versionName} (build ${info.versionCode})"
                        + (info.notes?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
                )
            },
            confirmButton = {
                Button(onClick = {
                    val updater = Updater(context.applicationContext, REPO_OWNER, REPO_NAME)
                    val id = updater.startDownload(info)
                    pending = null
                    scope.launch {
                        val r = updater.awaitDownload(id)
                        if (r is DownloadResult.Success) updater.launchInstall(id)
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Later") }
            },
        )
    }
}

private enum class Screen { Main, Settings }
