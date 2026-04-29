package com.shadaeiou.stitchcounter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.shadaeiou.stitchcounter.ui.notes.NotesSheet
import com.shadaeiou.stitchcounter.ui.pdf.PdfViewer
import com.shadaeiou.stitchcounter.ui.pdf.copyPdfToInternal
import com.shadaeiou.stitchcounter.ui.toolbar.BottomToolbar
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import com.shadaeiou.stitchcounter.viewmodel.Tool
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    vm: CounterViewModel,
    counterBackgroundArgb: Long,
    onOpenSettings: () -> Unit,
) {
    val project by vm.project.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val tool by vm.tool.collectAsStateWithLifecycle()
    val strokes by vm.strokes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var inverted by remember { mutableStateOf(false) }
    var historyVisible by remember { mutableStateOf(false) }
    var pdfFullscreen by remember { mutableStateOf(false) }
    var notesVisible by remember { mutableStateOf(false) }
    var confirmRemovePdf by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyPdfToInternal(context, uri)
            if (path != null) vm.setPdfPath(path)
            else scope.launch { snackbar.showSnackbar("Could not import PDF") }
        }
    }

    val hasPdf = !project?.pdfPath.isNullOrBlank()
    val showPdf = hasPdf
    val showCounter = !showPdf || !pdfFullscreen

    fun resetWithUndo() {
        vm.reset()
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "Counter reset",
                actionLabel = "Undo",
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undoLast()
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
            if (showCounter) {
                Box(modifier = Modifier.weight(1f)) {
                    CounterArea(
                        count = project?.count ?: 0,
                        label = project?.label.orEmpty(),
                        locked = locked,
                        interactionsEnabled = tool == Tool.None,
                        backgroundArgb = counterBackgroundArgb,
                        onIncrement = vm::increment,
                        onDecrement = vm::decrement,
                        onLabelChange = vm::setLabel,
                        onPullDown = { historyVisible = true },
                        onReset = ::resetWithUndo,
                        modifier = Modifier.fillMaxSize(),
                    )
                    HistoryOverlay(
                        visible = historyVisible,
                        history = history,
                        onUndoLast = { vm.undoLast() },
                        onReset = {
                            historyVisible = false
                            resetWithUndo()
                        },
                        onDismiss = { historyVisible = false },
                    )
                }
            }
            if (showPdf) {
                PdfViewer(
                    pdfPath = project?.pdfPath,
                    page = project?.currentPage ?: 0,
                    invertColors = inverted,
                    tool = tool,
                    strokes = strokes,
                    onPageChange = vm::setPage,
                    onAddStroke = { points -> vm.addStroke(points, colorArgb = 0xFFEF4444L, widthPx = 6f) },
                    onEraseAt = { x, y -> vm.eraseAt(x, y, toleranceNorm = 0.025f) },
                    onTapToggleFullscreen = { pdfFullscreen = !pdfFullscreen },
                    onRemovePdf = { confirmRemovePdf = true },
                    modifier = Modifier.weight(1f),
                )
            }
            BottomToolbar(
                locked = locked,
                inverted = inverted,
                activeTool = tool,
                onUploadPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onSelectPen = { vm.selectTool(Tool.Pen) },
                onSelectEraser = { vm.selectTool(Tool.Eraser) },
                onOpenNotes = { notesVisible = true },
                onToggleInvert = { inverted = !inverted },
                onToggleLock = vm::toggleLock,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    if (notesVisible) {
        NotesSheet(
            initialText = project?.notes.orEmpty(),
            onDismiss = { notesVisible = false },
            onSave = { vm.setNotes(it) },
        )
    }

    if (confirmRemovePdf) {
        AlertDialog(
            onDismissRequest = { confirmRemovePdf = false },
            title = { Text("Remove PDF?") },
            text = { Text("This will remove the loaded PDF and clear its annotations from the screen.") },
            confirmButton = {
                Button(onClick = {
                    confirmRemovePdf = false
                    pdfFullscreen = false
                    vm.setPdfPath(null)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePdf = false }) { Text("Cancel") }
            },
        )
    }
}
