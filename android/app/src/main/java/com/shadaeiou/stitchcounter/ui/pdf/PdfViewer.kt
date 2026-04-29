package com.shadaeiou.stitchcounter.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.shadaeiou.stitchcounter.viewmodel.Tool
import java.io.File

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
    onPageChange: (Int) -> Unit,
    onAddStroke: (List<StrokePoint>) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    onTapToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pdfPath == null) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center) {
            Text("No PDF loaded — tap Upload PDF in the toolbar",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }

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

    // Active stroke being drawn (in normalized 0..1 coords)
    var activeStroke by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (invertColors) Color.Black else Color.White),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY,
                    ),
            )
        }

        // Overlay layer for strokes + tool gestures.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool, safePage, pdfPath) {
                    when (tool) {
                        Tool.None -> {
                            detectTapGestures(onDoubleTap = { onTapToggleFullscreen() })
                        }
                        Tool.Pen -> {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val pts = mutableListOf<StrokePoint>()
                                pts += StrokePoint(down.position.x / w, down.position.y / h)
                                activeStroke = pts.toList()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val p = event.changes.firstOrNull { it.id == down.id } ?: break
                                    pts += StrokePoint(p.position.x / w, p.position.y / h)
                                    activeStroke = pts.toList()
                                    if (!p.pressed) break
                                }
                                if (pts.size >= 2) onAddStroke(pts.toList())
                                activeStroke = emptyList()
                            }
                        }
                        Tool.Eraser -> {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                onEraseAt(down.position.x / w, down.position.y / h)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val p = event.changes.firstOrNull { it.id == down.id } ?: break
                                    onEraseAt(p.position.x / w, p.position.y / h)
                                    if (!p.pressed) break
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
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasSize = Offset(size.width, size.height)
                val w = size.width
                val h = size.height
                strokes.forEach { stroke -> drawStroke(stroke, w, h) }
                if (activeStroke.size >= 2) {
                    val tmp = Stroke(points = activeStroke, colorArgb = penColorArgb(tool))
                    drawStroke(tmp, w, h)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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

        if (tool != Tool.None) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    when (tool) { Tool.Pen -> "Pen"; Tool.Eraser -> "Eraser"; else -> "" },
                    color = if (invertColors) Color.White else Color.Black,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun penColorArgb(tool: Tool): Long = 0xFFEF4444L  // red

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
