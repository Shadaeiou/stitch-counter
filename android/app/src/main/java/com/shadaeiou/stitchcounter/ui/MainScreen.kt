package com.shadaeiou.stitchcounter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadaeiou.stitchcounter.ui.counter.CounterArea
import com.shadaeiou.stitchcounter.ui.counter.HistoryOverlay
import com.shadaeiou.stitchcounter.ui.pdf.PdfViewer
import com.shadaeiou.stitchcounter.ui.pdf.PenSettingsPanel
import com.shadaeiou.stitchcounter.ui.pdf.copyPdfToInternal
import com.shadaeiou.stitchcounter.ui.toolbar.BottomToolbar
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import com.shadaeiou.stitchcounter.viewmodel.Tool
import kotlinx.coroutines.launch

private const val DIVIDER_HEIGHT_DP = 14
private const val MIN_PANE_FRACTION = 0.15f

@Composable
fun MainScreen(
    vm: CounterViewModel,
    counterBackgroundArgb: Long,
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenPattern: () -> Unit,
) {
    val project by vm.project.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val tool by vm.tool.collectAsStateWithLifecycle()
    val strokes by vm.strokes.collectAsStateWithLifecycle()
    val penColor by vm.penColorArgb.collectAsStateWithLifecycle()
    val penWidth by vm.penWidthPx.collectAsStateWithLifecycle()
    val canRedoStroke by vm.canRedo.collectAsStateWithLifecycle()
    val pinnedNotes by vm.pinnedNotes.collectAsStateWithLifecycle()
    val knitPattern by vm.knitPattern.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var inverted by remember { mutableStateOf(false) }
    var historyVisible by remember { mutableStateOf(false) }
    var pdfHidden by remember { mutableStateOf(false) }
    var confirmRemovePdf by remember { mutableStateOf(false) }
    var penPanelVisible by remember { mutableStateOf(false) }
    var patternEditorVisible by remember { mutableStateOf(false) }
    // Pane split as a fraction of the splittable region (counter + pdf area).
    var counterFraction by remember { mutableStateOf(0.5f) }

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
    val showPdf = hasPdf && !pdfHidden

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
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val totalHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val dividerHeightPx = with(density) { DIVIDER_HEIGHT_DP.dp.toPx() }
                val splittableHeightPx = (totalHeightPx - dividerHeightPx).coerceAtLeast(1f)
                Column(modifier = Modifier.fillMaxSize()) {
                    val counterWeight = if (showPdf) counterFraction else 1f
                    Box(modifier = Modifier.weight(counterWeight).fillMaxWidth()) {
                        CounterArea(
                            count = project?.count ?: 0,
                            locked = locked,
                            interactionsEnabled = tool == Tool.None,
                            backgroundArgb = counterBackgroundArgb,
                            knitPattern = knitPattern,
                            pinnedNotes = pinnedNotes,
                            onIncrement = vm::increment,
                            onDecrement = vm::decrement,
                            onPullDown = { historyVisible = true },
                            onReset = ::resetWithUndo,
                            onEditPattern = { patternEditorVisible = true },
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
                    if (showPdf) {
                        PaneDivider(
                            heightDp = DIVIDER_HEIGHT_DP,
                            onDragDelta = { dyPx ->
                                val deltaFraction = dyPx / splittableHeightPx
                                counterFraction = (counterFraction + deltaFraction)
                                    .coerceIn(MIN_PANE_FRACTION, 1f - MIN_PANE_FRACTION)
                            },
                        )
                        Box(modifier = Modifier.weight(1f - counterFraction).fillMaxWidth()) {
                            PdfViewer(
                                pdfPath = project?.pdfPath,
                                page = project?.currentPage ?: 0,
                                invertColors = inverted,
                                tool = tool,
                                strokes = strokes,
                                penColorArgb = penColor,
                                penWidthPx = penWidth,
                                onPageChange = vm::setPage,
                                onAddStroke = { points -> vm.addStroke(points, colorArgb = penColor, widthPx = penWidth) },
                                onEraseAt = { x, y -> vm.eraseAt(x, y, toleranceNorm = 0.025f) },
                                onTapToggleFullscreen = { /* fullscreen now via Hide PDF in toolbar */ },
                                onRemovePdf = { confirmRemovePdf = true },
                                canUndoStroke = strokes.isNotEmpty(),
                                canRedoStroke = canRedoStroke,
                                onUndoStroke = vm::undoLastStroke,
                                onRedoStroke = vm::redoLastStroke,
                                onPenDrawStart = { penPanelVisible = false },
                                onUploadPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                                onSelectPen = {
                                    penPanelVisible = false
                                    vm.selectTool(Tool.Pen)
                                },
                                onLongPressPen = {
                                    if (tool != Tool.Pen) vm.selectTool(Tool.Pen)
                                    penPanelVisible = true
                                },
                                onSelectEraser = {
                                    penPanelVisible = false
                                    vm.selectTool(Tool.Eraser)
                                },
                                onToggleInvert = { inverted = !inverted },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            BottomToolbar(
                locked = locked,
                hasPdf = hasPdf,
                pdfHidden = pdfHidden,
                onUploadPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onTogglePdfHidden = { pdfHidden = !pdfHidden },
                onOpenNotes = onOpenNotes,
                onOpenPattern = onOpenPattern,
                onToggleLock = vm::toggleLock,
                onOpenSettings = onOpenSettings,
            )
        }

        if (penPanelVisible && tool == Tool.Pen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter,
            ) {
                PenSettingsPanel(
                    selectedColorArgb = penColor,
                    widthPx = penWidth,
                    onColorChange = vm::setPenColor,
                    onWidthChange = vm::setPenWidth,
                    onDarkBackground = true,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 64.dp),
                )
            }
        }
    }

    if (confirmRemovePdf) {
        AlertDialog(
            onDismissRequest = { confirmRemovePdf = false },
            title = { Text("Remove PDF?") },
            text = { Text("This permanently removes the loaded PDF and its annotations.") },
            confirmButton = {
                Button(onClick = {
                    confirmRemovePdf = false
                    pdfHidden = false
                    vm.setPdfPath(null)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePdf = false }) { Text("Cancel") }
            },
        )
    }

    if (patternEditorVisible) {
        KnitPatternEditor(
            initial = knitPattern,
            onDismiss = { patternEditorVisible = false },
            onSave = {
                vm.setKnitPattern(it)
                patternEditorVisible = false
            },
        )
    }
}

@Composable
private fun PaneDivider(
    heightDp: Int,
    onDragDelta: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    onDragDelta(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(4.dp)
                .width(48.dp)
                .background(Color.White.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun KnitPatternEditor(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Knit pattern") },
        text = {
            Column {
                Text(
                    "Type a sequence of K (knit) and P (purl). It repeats. Each character is one row. Leave empty to hide the indicator.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { input ->
                        draft = input.uppercase().filter { it == 'K' || it == 'P' }
                    },
                    placeholder = { Text("e.g. KKP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
