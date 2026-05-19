package com.shadaeiou.stitchcounter

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.shadaeiou.stitchcounter.pip.ACTION_PIP_DECREMENT
import com.shadaeiou.stitchcounter.pip.ACTION_PIP_INCREMENT
import com.shadaeiou.stitchcounter.pip.PipActionReceiver
import com.shadaeiou.stitchcounter.ui.MainScreen
import com.shadaeiou.stitchcounter.ui.notes.NotesScreen
import com.shadaeiou.stitchcounter.ui.pattern.PatternScreen
import com.shadaeiou.stitchcounter.ui.projects.ProjectsScreen
import com.shadaeiou.stitchcounter.ui.settings.SettingsScreen
import com.shadaeiou.stitchcounter.ui.theme.StitchCounterTheme
import com.shadaeiou.stitchcounter.update.DownloadResult
import com.shadaeiou.stitchcounter.update.UpdateCheckResult
import com.shadaeiou.stitchcounter.update.UpdateInfo
import com.shadaeiou.stitchcounter.update.Updater
import com.shadaeiou.stitchcounter.update.postUpdateNotification
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import com.shadaeiou.stitchcounter.viewmodel.KnitProjectViewModel
import kotlinx.coroutines.launch

private const val REPO_OWNER = "shadaeiou"
private const val REPO_NAME = "stitch-counter"

class MainActivity : ComponentActivity() {

    val counterVm: CounterViewModel by viewModels { CounterViewModel.Factory() }
    val knitProjectVm: KnitProjectViewModel by viewModels { KnitProjectViewModel.Factory() }

    private val isInPip = mutableStateOf(false)

    @Volatile private var volumeKeysEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        addOnPictureInPictureModeChangedListener { info ->
            isInPip.value = info.isInPictureInPictureMode
        }

        lifecycleScope.launch {
            StitchCounterApp.instance.prefs.volumeKeysFlow.collect { volumeKeysEnabled = it }
        }

        setContent {
            StitchCounterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        activityVm = counterVm,
                        knitProjectVm = knitProjectVm,
                        isInPip = isInPip.value,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(buildPipParams())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishing && !isInPictureInPictureMode) {
            enterPictureInPictureMode(buildPipParams())
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val incIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, PipActionReceiver::class.java).apply { action = ACTION_PIP_INCREMENT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val decIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, PipActionReceiver::class.java).apply { action = ACTION_PIP_DECREMENT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val incAction = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_pip_increment),
            "+", "Increment counter", incIntent,
        )
        val decAction = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_pip_decrement),
            "-", "Decrement counter", decIntent,
        )
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(3, 2))
            .setActions(listOf(decAction, incAction))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        return builder.build()
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
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun AppRoot(
    activityVm: CounterViewModel,
    knitProjectVm: KnitProjectViewModel,
    isInPip: Boolean,
) {
    val app = StitchCounterApp.instance
    val prefs = app.prefs

    val autoUpdate by prefs.autoUpdateFlow.collectAsState(initial = true)
    val volumeKeys by prefs.volumeKeysFlow.collectAsState(initial = true)
    val counterBg by prefs.counterBackgroundFlow.collectAsState(
        initial = com.shadaeiou.stitchcounter.data.prefs.UserPrefs.DEFAULT_COUNTER_BG
    )
    val counterView by prefs.counterViewFlow.collectAsState(initial = 0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }
    var screen by remember { mutableStateOf(Screen.Main) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently */ }

    LaunchedEffect(autoUpdate) {
        if (!autoUpdate) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, perm,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(perm)
        }
        val updater = Updater(context.applicationContext, REPO_OWNER, REPO_NAME)
        val result = updater.checkForUpdate(BuildConfig.VERSION_CODE)
        if (result is UpdateCheckResult.Available) {
            pending = result.info
            postUpdateNotification(context.applicationContext, result.info)
        }
    }

    val counterProject by activityVm.project.collectAsState()
    val notes by activityVm.notes.collectAsState(initial = emptyList())
    val counterCount = counterProject?.count ?: 0
    val currentRowLabel by activityVm.currentRowLabel.collectAsState()

    if (isInPip) {
        PipCounterView(count = counterCount + 1)
        return
    }

    when (screen) {
        Screen.Main -> MainScreen(
            vm = activityVm,
            counterBackgroundArgb = counterBg,
            counterView = counterView,
            onCounterViewChange = { v -> scope.launch { prefs.setCounterView(v) } },
            onOpenSettings = { screen = Screen.Settings },
            onOpenNotes = { screen = Screen.Notes },
            onOpenPattern = { screen = Screen.Pattern },
            onOpenProjects = { screen = Screen.Projects },
        )
        Screen.Notes -> {
            BackHandler { screen = Screen.Main }
            NotesScreen(
                notes = notes,
                onBack = { screen = Screen.Main },
                onAdd = activityVm::addNote,
                onTogglePin = activityVm::togglePin,
                onDelete = activityVm::deleteNote,
                onUpdate = activityVm::updateNote,
            )
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Main }
            SettingsScreen(
                autoUpdate = autoUpdate,
                volumeKeys = volumeKeys,
                counterBackgroundArgb = counterBg,
                onAutoUpdateChange = { v -> scope.launch { prefs.setAutoUpdate(v) } },
                onVolumeKeysChange = { v -> scope.launch { prefs.setVolumeKeys(v) } },
                onCounterBackgroundChange = { v -> scope.launch { prefs.setCounterBackground(v) } },
                onClearPattern = { activityVm.clearPattern() },
                onBack = { screen = Screen.Main },
                repoOwner = REPO_OWNER,
                repoName = REPO_NAME,
            )
        }
        Screen.Pattern -> {
            BackHandler { screen = Screen.Main }
            PatternScreen(
                vm = activityVm,
                onBack = { screen = Screen.Main },
            )
        }
        Screen.Projects -> {
            BackHandler { screen = Screen.Main }
            ProjectsScreen(
                vm = knitProjectVm,
                count = counterCount,
                currentRowLabel = currentRowLabel,
                counterBackgroundArgb = counterBg,
                onGoToCounter = { screen = Screen.Main },
                onOpenSettings = { screen = Screen.Settings },
                onBack = { screen = Screen.Main },
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

@Composable
private fun PipCounterView(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Current Row",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private enum class Screen { Main, Notes, Settings, Pattern, Projects }
