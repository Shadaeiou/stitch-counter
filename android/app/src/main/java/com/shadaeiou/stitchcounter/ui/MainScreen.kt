package com.shadaeiou.stitchcounter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.shadaeiou.stitchcounter.ui.pattern.PatternView
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
    counterView: Int,
    onCounterViewChange: (Int) -> Unit,
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
    val patternHtml by vm.patternHtml.collectAsStateWithLifecycle()
    val patternHighlightRange by vm.patternHighlightRange.collectAsStateWithLifecycle()
    val patternTool by vm.patternTool.collectAsStateWithLifecycle()
    val patternStrokes by vm.patternStrokes.collectAsStateWithLifecycle()
    val canPatternRedo by vm.canPatternRedo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var inverted by remember { mutableStateOf(false) }
    var historyVisible by remember { mutableStateOf(false) }
    var pdfHidden by remember { mutableStateOf(false) }
    var patternHidden by remember { mutableStateOf(true) }
    var confirmRemovePdf by remember { mutableStateOf(false) }
    var penPanelVisible by remember { mutableStateOf(false) }
    var patternPenPanelVisible by remember { mutableStateOf(false) }
    var counterSettingsVisible by remember { mutableStateOf(false) }
    // Pane split as a fraction of the splittable region (counter + bottom pane).
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
    val hasPattern = patternHtml.isNotBlank()
    // Showing PDF hides pattern and vice-versa (mutually exclusive bottom pane).
    val showPdf = hasPdf && !pdfHidden
    val showPattern = hasPattern && !patternHidden && !showPdf

    // Auto-show pattern pane when a pattern is first saved and PDF is not shown.
    LaunchedEffect(hasPattern) {
        if (hasPattern && !hasPdf) patternHidden = false
    }

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
                    val showBottomPane = showPdf || showPattern
                    val counterWeight = if (showBottomPane) counterFraction else 1f
                    Box(modifier = Modifier.weight(counterWeight).fillMaxWidth()) {
                        CounterArea(
                            count = project?.count ?: 0,
                            locked = locked,
                            interactionsEnabled = tool == Tool.None && patternTool == Tool.None,
                            backgroundArgb = counterBackgroundArgb,
                            knitPattern = knitPattern,
                            counterView = counterView,
                            pinnedNotes = pinnedNotes,
                            onIncrement = vm::increment,
                            onDecrement = vm::decrement,
                            onPullDown = { historyVisible = true },
                            onReset = ::resetWithUndo,
                            onEditPattern = { counterSettingsVisible = true },
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
                    if (showBottomPane) {
                        PaneDivider(
                            heightDp = DIVIDER_HEIGHT_DP,
                            onDragDelta = { dyPx ->
                                val deltaFraction = dyPx / splittableHeightPx
                                counterFraction = (counterFraction + deltaFraction)
                                    .coerceIn(MIN_PANE_FRACTION, 1f - MIN_PANE_FRACTION)
                            },
                        )
                        Box(modifier = Modifier.weight(1f - counterFraction).fillMaxWidth()) {
                            if (showPdf) {
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
                                    onTapToggleFullscreen = { /* fullscreen via Hide PDF in toolbar */ },
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
                            } else {
                                PatternView(
                                    patternHtml = patternHtml,
                                    highlightRange = patternHighlightRange,
                                    strokes = patternStrokes,
                                    patternTool = patternTool,
                                    penColorArgb = penColor,
                                    penWidthPx = penWidth,
                                    canRedo = canPatternRedo,
                                    onHighlightChange = vm::setPatternHighlightRange,
                                    onPinContent = vm::addPinnedNote,
                                    onAddStroke = { points ->
                                        patternPenPanelVisible = false
                                        vm.addPatternStroke(points, colorArgb = penColor, widthPx = penWidth)
                                    },
                                    onEraseAt = { x, y -> vm.erasePatternAt(x, y, toleranceNorm = 0.025f) },
                                    onUndoStroke = vm::undoLastPatternStroke,
                                    onRedoStroke = vm::redoLastPatternStroke,
                                    onSelectPen = {
                                        patternPenPanelVisible = false
                                        vm.selectPatternTool(Tool.Pen)
                                    },
                                    onLongPressPen = {
                                        if (patternTool != Tool.Pen) vm.selectPatternTool(Tool.Pen)
                                        patternPenPanelVisible = true
                                    },
                                    onSelectEraser = {
                                        patternPenPanelVisible = false
                                        vm.selectPatternTool(Tool.Eraser)
                                    },
                                    onEdit = onOpenPattern,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            BottomToolbar(
                locked = locked,
                hasPdf = hasPdf,
                pdfHidden = pdfHidden,
                hasPattern = hasPattern,
                patternHidden = patternHidden,
                onUploadPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onTogglePdfHidden = {
                    pdfHidden = !pdfHidden
                    // Show PDF → hide pattern; hide PDF → leave pattern as-is
                    if (!pdfHidden) patternHidden = true
                },
                onTogglePatternHidden = {
                    patternHidden = !patternHidden
                    // Show pattern → hide PDF
                    if (!patternHidden) pdfHidden = true
                },
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

        if (patternPenPanelVisible && patternTool == Tool.Pen) {
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

    if (counterSettingsVisible) {
        CounterSettingsDialog(
            currentView = counterView,
            currentPattern = knitPattern,
            onDismiss = { counterSettingsVisible = false },
            onSave = { view, pattern ->
                onCounterViewChange(view)
                vm.setKnitPattern(pattern)
                counterSettingsVisible = false
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

private const val STEP_SEP = "|||"

private fun decodeKnitPattern(raw: String): Pair<List<String>, Int> {
    if (raw.contains(STEP_SEP)) {
        val parts = raw.split(STEP_SEP)
        val steps = parts.take(4).toMutableList()
        while (steps.size < 4) steps.add("")
        val every = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(1, 999) ?: 1
        return Pair(steps, every)
    }
    return Pair(listOf(raw, "", "", ""), 1)
}

fun encodeKnitPattern(steps: List<String>, every: Int): String =
    steps.take(4).joinToString(STEP_SEP) + STEP_SEP + every.coerceIn(1, 999)

private val VIEW_LABELS = listOf(
    "View 1 — single counter with row label",
    "View 2 — rows completed + next row",
)

@Composable
private fun CounterSettingsDialog(
    currentView: Int,
    currentPattern: String,
    onDismiss: () -> Unit,
    onSave: (view: Int, pattern: String) -> Unit,
) {
    val (initSteps, initEvery) = remember(currentPattern) { decodeKnitPattern(currentPattern) }
    val steps = remember { mutableStateListOf(*initSteps.toTypedArray()) }
    var every by remember { mutableStateOf(initEvery.toString()) }
    var selectedView by remember { mutableStateOf(currentView) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Counter settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "View",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                VIEW_LABELS.forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = selectedView == i,
                            onClick = { selectedView = i },
                        )
                        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Knit pattern",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Each step is a row label (e.g. K20, P5, Row 1). Steps cycle in order. Leave unused steps empty.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                steps.forEachIndexed { i, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { steps[i] = it },
                        label = { Text("Step ${i + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (i < steps.lastIndex) Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Advance after",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = every,
                        onValueChange = { every = it.filter(Char::isDigit).take(3).trimStart('0').ifEmpty { "1" } },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "count(s)",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val everyInt = every.toIntOrNull()?.coerceIn(1, 999) ?: 1
                onSave(selectedView, encodeKnitPattern(steps.toList(), everyInt))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
