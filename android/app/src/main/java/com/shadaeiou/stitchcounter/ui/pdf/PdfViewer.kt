package com.shadaeiou.stitchcounter.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
    onPageChange: (Int) -> Unit,
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
    DisposableEffect(handle) {
        onDispose { handle?.close() }
    }

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
                    )
                    .pointerInput(safePage) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
            )
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
    }
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
