package com.shadaeiou.stitchcounter

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    private val counterVm: CounterViewModel by viewModels { CounterViewModel.Factory() }

    @Volatile private var volumeKeysEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            StitchCounterApp.instance.prefs.volumeKeysFlow.collect { volumeKeysEnabled = it }
        }

        setContent {
            StitchCounterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volumeKeysEnabled && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { counterVm.increment(); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { counterVm.decrement(); return true }
            }
        }
        // Also swallow ACTION_UP so the system doesn't change media volume
        if (volumeKeysEnabled && event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val app = StitchCounterApp.instance
    val prefs = app.prefs

    val autoUpdate by prefs.autoUpdateFlow.collectAsState(initial = true)
    val volumeKeys by prefs.volumeKeysFlow.collectAsState(initial = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(autoUpdate) {
        if (!autoUpdate) return@LaunchedEffect
        val updater = Updater(context.applicationContext, REPO_OWNER, REPO_NAME)
        val result = updater.checkForUpdate(BuildConfig.VERSION_CODE)
        if (result is UpdateCheckResult.Available) pending = result.info
    }

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScreen(onOpenSettings = { nav.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(
                autoUpdate = autoUpdate,
                volumeKeys = volumeKeys,
                onAutoUpdateChange = { v -> scope.launch { prefs.setAutoUpdate(v) } },
                onVolumeKeysChange = { v -> scope.launch { prefs.setVolumeKeys(v) } },
                onBack = { nav.popBackStack() },
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
