package com.shadaeiou.stitchcounter.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shadaeiou.stitchcounter.R
import com.shadaeiou.stitchcounter.ui.theme.PenColors
import com.shadaeiou.stitchcounter.viewmodel.Tool
import java.io.File
import kotlin.math.hypot

private class PdfHandle(file: File) : AutoCloseable {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer: PdfRenderer = PdfRenderer(pfd)
    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
fun PdfViewer(
    pdfPath: String?,
    page: Int,
    invertColors: Boolean,
    tool: Tool,
    strokes: List<Stroke>,
    penColorArgb: Long,
    penWidthPx: Float,
    onPageChange: (Int) -> Unit,
    onAddStroke: (List<StrokePoint>) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    onTapToggleFullscreen: () -> Unit,
    onRemovePdf: () -> Unit,
    canUndoStroke: Boolean,
    canRedoStroke: Boolean,
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onPenDrawStart: () -> Unit,
    onUploadPdf: () -> Unit,
    onSelectPen: () -> Unit,
    onLongPressPen: () -> Unit,
    onSelectEraser: () -> Unit,
    onToggleInvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pdfPath == null) return

    val handle = remember(pdfPath) {
        runCatching { PdfHandle(File(pdfPath)) }.getOrNull()
    }
    DisposableEffect(handle) { onDispose { handle?.close() } }

    if (handle == null) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center) {
            Text("Could not open PDF", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val pageCount = handle.renderer.pageCount
    val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(safePage, pdfPath, invertColors) {
        scale = 1f; offsetX = 0f; offsetY = 0f
        bitmap = renderPage(handle, safePage, invertColors)
    }

    // Active stroke being drawn, in normalized 0..1 PDF coords (un-transformed).
    var activeStroke by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(if (invertColors) Color.Black else Color.White),
    ) {
        // Bitmap and strokes share the same graphicsLayer transform so strokes
        // remain anchored to the PDF page during zoom/pan. transformOrigin is
        // pinned to the top-left so screen<->page coordinate math is simple.
        val pageLayer = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY,
                transformOrigin = TransformOrigin(0f, 0f),
            )

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = pageLayer,
            )
        }

        Canvas(modifier = pageLayer) {
            val w = size.width
            val h = size.height
            strokes.forEach { stroke -> drawStroke(stroke, w, h) }
            if (activeStroke.size >= 2) {
                val tmp = Stroke(
                    points = activeStroke,
                    colorArgb = penColorArgb,
                    widthPx = penWidthPx,
                )
                drawStroke(tmp, w, h)
            }
        }

        // Gesture/input layer (un-transformed; pointer positions are in screen
        // coords, converted to PDF coords by reversing the transform).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool, safePage, pdfPath) {
                    when (tool) {
                        Tool.None -> {
                            detectTapGestures(onDoubleTap = { onTapToggleFullscreen() })
                        }
                        Tool.Pen -> {
                            // 1-finger: draw stroke. 2-finger: pinch-zoom + pan.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                onPenDrawStart()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val pts = mutableListOf<StrokePoint>()
                                pts += toPagePoint(down.position.x, down.position.y, w, h, scale, offsetX, offsetY)
                                activeStroke = pts.toList()
                                var isDrawing = true
                                var prevCentroid = down.position
                                var prevSpan = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    when {
                                        pressed.size >= 2 -> {
                                            if (isDrawing) {
                                                pts.clear()
                                                activeStroke = emptyList()
                                                isDrawing = false
                                            }
                                            val centroid = pressed
                                                .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                                pressed.size.toFloat()
                                            val span = hypot(
                                                pressed[0].position.x - pressed[1].position.x,
                                                pressed[0].position.y - pressed[1].position.y,
                                            )
                                            if (prevSpan > 0f && span > 0f) {
                                                scale = (scale * (span / prevSpan)).coerceIn(1f, 6f)
                                            }
                                            val delta = centroid - prevCentroid
                                            offsetX += delta.x
                                            offsetY += delta.y
                                            prevCentroid = centroid
                                            prevSpan = span
                                        }
                                        pressed.size == 1 && isDrawing -> {
                                            val p = pressed.first()
                                            pts += toPagePoint(p.position.x, p.position.y, w, h, scale, offsetX, offsetY)
                                            activeStroke = pts.toList()
                                            prevCentroid = p.position
                                        }
                                        pressed.isEmpty() -> break
                                        else -> {
                                            prevCentroid = pressed.first().position
                                            prevSpan = 0f
                                        }
                                    }
                                }
                                if (isDrawing && pts.size >= 2) onAddStroke(pts.toList())
                                activeStroke = emptyList()
                            }
                        }
                        Tool.Eraser -> {
                            // 1-finger: erase. 2-finger: pinch-zoom + pan.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val p0 = toPagePoint(down.position.x, down.position.y, w, h, scale, offsetX, offsetY)
                                onEraseAt(p0.x, p0.y)
                                var isErasing = true
                                var prevCentroid = down.position
                                var prevSpan = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    when {
                                        pressed.size >= 2 -> {
                                            isErasing = false
                                            val centroid = pressed
                                                .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                                pressed.size.toFloat()
                                            val span = hypot(
                                                pressed[0].position.x - pressed[1].position.x,
                                                pressed[0].position.y - pressed[1].position.y,
                                            )
                                            if (prevSpan > 0f && span > 0f) {
                                                scale = (scale * (span / prevSpan)).coerceIn(1f, 6f)
                                            }
                                            val delta = centroid - prevCentroid
                                            offsetX += delta.x
                                            offsetY += delta.y
                                            prevCentroid = centroid
                                            prevSpan = span
                                        }
                                        pressed.size == 1 && isErasing -> {
                                            val p = pressed.first()
                                            val pp = toPagePoint(p.position.x, p.position.y, w, h, scale, offsetX, offsetY)
                                            onEraseAt(pp.x, pp.y)
                                            prevCentroid = p.position
                                        }
                                        pressed.isEmpty() -> break
                                        else -> {
                                            prevCentroid = pressed.first().position
                                            prevSpan = 0f
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(tool, safePage, pdfPath) {
                    if (tool == Tool.None) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (safePage > 0) onPageChange(safePage - 1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page",
                    tint = if (invertColors) Color.White else Color.Black)
            }
            Text("${safePage + 1} / $pageCount",
                color = if (invertColors) Color.White else Color.Black)
            IconButton(onClick = { if (safePage < pageCount - 1) onPageChange(safePage + 1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next page",
                    tint = if (invertColors) Color.White else Color.Black)
            }
        }

        // Overlay toolbar in the upper-right corner of the PDF area.
        PdfToolbar(
            activeTool = tool,
            inverted = invertColors,
            onUploadPdf = onUploadPdf,
            onSelectPen = onSelectPen,
            onLongPressPen = onLongPressPen,
            onSelectEraser = onSelectEraser,
            onToggleInvert = onToggleInvert,
            onRemovePdf = onRemovePdf,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )

        if (tool == Tool.Pen) {
            UndoRedoBar(
                canUndo = canUndoStroke,
                canRedo = canRedoStroke,
                onUndo = onUndoStroke,
                onRedo = onRedoStroke,
                onDarkBackground = invertColors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 12.dp),
            )
        } else if (tool == Tool.Eraser) {
            Text(
                "Eraser",
                color = if (invertColors) Color.White else Color.Black,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfToolbar(
    activeTool: Tool,
    inverted: Boolean,
    onUploadPdf: () -> Unit,
    onSelectPen: () -> Unit,
    onLongPressPen: () -> Unit,
    onSelectEraser: () -> Unit,
    onToggleInvert: () -> Unit,
    onRemovePdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barBg =
        if (inverted) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.85f)
    val barBorder =
        if (inverted) Color.White.copy(alpha = 0.4f)
        else Color.Black.copy(alpha = 0.4f)
    val activeTint = MaterialTheme.colorScheme.primary
    val idleTint = if (inverted) Color.White else Color.Black

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(barBg)
            .border(BorderStroke(1.dp, barBorder), RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUploadPdf) {
            Icon(
                Icons.Default.UploadFile,
                contentDescription = "Upload PDF",
                tint = idleTint,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    onClick = onSelectPen,
                    onLongClick = onLongPressPen,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Pen (long-press for options)",
                tint = if (activeTool == Tool.Pen) activeTint else idleTint,
            )
        }
        IconButton(onClick = onSelectEraser) {
            Icon(
                painter = painterResource(R.drawable.ic_eraser),
                contentDescription = "Eraser",
                tint = if (activeTool == Tool.Eraser) activeTint else idleTint,
            )
        }
        IconButton(onClick = onToggleInvert) {
            Icon(
                Icons.Default.InvertColors,
                contentDescription = "Invert colors",
                tint = if (inverted) activeTint else idleTint,
            )
        }
        IconButton(onClick = onRemovePdf) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove PDF",
                tint = idleTint,
            )
        }
    }
}

@Composable
private fun UndoRedoBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val barBg =
        if (onDarkBackground) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.85f)
    val barBorder =
        if (onDarkBackground) Color.White.copy(alpha = 0.4f)
        else Color.Black.copy(alpha = 0.4f)
    val tint = if (onDarkBackground) Color.White else Color.Black

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(barBg)
            .border(BorderStroke(1.dp, barBorder), RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo last stroke",
                tint = if (canUndo) tint else tint.copy(alpha = 0.3f),
            )
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo last undone stroke",
                tint = if (canRedo) tint else tint.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
fun PenSettingsPanel(
    selectedColorArgb: Long,
    widthPx: Float,
    onColorChange: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val panelBg =
        if (onDarkBackground) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.85f)
    val borderColor =
        if (onDarkBackground) Color.White.copy(alpha = 0.4f)
        else Color.Black.copy(alpha = 0.4f)
    val onPanel = if (onDarkBackground) Color.White else Color.Black

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(panelBg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (choice in PenColors) {
            val isSelected = choice.argb == selectedColorArgb
            val swatchBorder = if (isSelected) onPanel else onPanel.copy(alpha = 0.3f)
            val swatchBorderWidth = if (isSelected) 3.dp else 1.dp
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(choice.argb.toInt()))
                    .border(BorderStroke(swatchBorderWidth, swatchBorder), CircleShape)
                    .selectable(
                        selected = isSelected,
                        onClick = { onColorChange(choice.argb) },
                    ),
            )
        }
        Slider(
            value = widthPx,
            onValueChange = onWidthChange,
            valueRange = 1f..36f,
            modifier = Modifier.width(140.dp),
        )
        Text(
            "${widthPx.toInt()}px",
            color = onPanel,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun toPagePoint(
    x: Float, y: Float, w: Float, h: Float,
    scale: Float, offX: Float, offY: Float,
): StrokePoint {
    val s = scale.coerceAtLeast(0.0001f)
    val px = (x - offX) / s
    val py = (y - offY) / s
    return StrokePoint(px / w, py / h)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: Stroke, w: Float, h: Float,
) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * w, first.y * h)
        for (i in 1 until stroke.points.size) {
            val p = stroke.points[i]
            lineTo(p.x * w, p.y * h)
        }
    }
    drawPath(
        path = path,
        color = Color(stroke.colorArgb.toInt()),
        style = DrawStroke(
            width = stroke.widthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun renderPage(handle: PdfHandle, index: Int, invert: Boolean): Bitmap? {
    return runCatching {
        handle.renderer.openPage(index).use { page ->
            val width = 1600
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (invert) invertBitmap(bmp) else bmp
        }
    }.getOrNull()
}

private fun invertBitmap(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val matrix = ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ))
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
    canvas.drawBitmap(src, 0f, 0f, paint)
    src.recycle()
    return out
}

@Suppress("UNUSED_PARAMETER")
fun copyPdfToInternal(context: Context, uri: android.net.Uri): String? {
    return runCatching {
        val dest = File(context.filesDir, "pdfs/${System.currentTimeMillis()}.pdf").apply {
            parentFile?.mkdirs()
        }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }.getOrNull()
}
