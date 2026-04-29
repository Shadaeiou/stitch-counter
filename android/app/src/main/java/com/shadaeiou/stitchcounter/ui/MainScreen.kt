package com.shadaeiou.stitchcounter.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadaeiou.stitchcounter.ui.counter.CounterArea
import com.shadaeiou.stitchcounter.ui.counter.HistoryOverlay
import com.shadaeiou.stitchcounter.ui.pdf.PdfViewer
import com.shadaeiou.stitchcounter.ui.pdf.copyPdfToInternal
import com.shadaeiou.stitchcounter.ui.toolbar.BottomToolbar
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    vm: CounterViewModel,
    onOpenSettings: () -> Unit,
) {
    val project by vm.project.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var inverted by remember { mutableStateOf(false) }
    var historyVisible by remember { mutableStateOf(false) }
    var pdfFullscreen by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyPdfToInternal(context, uri)
            if (path != null) vm.setPdfPath(path)
            else scope.launch { snackbar.showSnackbar("Could not import PDF") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!pdfFullscreen) {
                Box(modifier = Modifier.weight(1f)) {
                    CounterArea(
                        count = project?.count ?: 0,
                        label = project?.label.orEmpty(),
                        locked = locked,
                        onIncrement = vm::increment,
                        onDecrement = vm::decrement,
                        onLabelChange = vm::setLabel,
                        onPullDown = { historyVisible = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    HistoryOverlay(
                        visible = historyVisible,
                        history = history,
                        onUndoLast = {
                            vm.undoLast()
                        },
                        onReset = {
                            vm.reset()
                            historyVisible = false
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = "Counter reset",
                                    actionLabel = "Undo",
                                    duration = androidx.compose.material3.SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) vm.undoLast()
                            }
                        },
                        onDismiss = { historyVisible = false },
                    )
                }
            }
            PdfViewer(
                pdfPath = project?.pdfPath,
                page = project?.currentPage ?: 0,
                invertColors = inverted,
                onPageChange = vm::setPage,
                onTapToggleFullscreen = { pdfFullscreen = !pdfFullscreen },
                modifier = Modifier.weight(1f),
            )
            BottomToolbar(
                locked = locked,
                inverted = inverted,
                onUploadPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onPenStub = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                onEraserStub = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                onNotesStub = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                onToggleInvert = { inverted = !inverted },
                onToggleLock = vm::toggleLock,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    LaunchedEffect(Unit) {}
}
