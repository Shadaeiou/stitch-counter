package com.shadaeiou.stitchcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

private const val REPO_OWNER = "shadaeiou"
private const val REPO_NAME = "stitch-counter"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StitchCounterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { androidx.compose.material3.Text("Update available") },
            text = {
                androidx.compose.material3.Text(
                    "Version ${info.versionName} (build ${info.versionCode})"
                        + (info.notes?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    val updater = Updater(context.applicationContext, REPO_OWNER, REPO_NAME)
                    val id = updater.startDownload(info)
                    pending = null
                    scope.launch {
                        val r = updater.awaitDownload(id)
                        if (r is DownloadResult.Success) updater.launchInstall(id)
                    }
                }) { androidx.compose.material3.Text("Update") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pending = null }) {
                    androidx.compose.material3.Text("Later")
                }
            },
        )
    }
}
